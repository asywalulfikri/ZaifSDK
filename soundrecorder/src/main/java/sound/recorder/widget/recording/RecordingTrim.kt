package sound.recorder.widget.recording

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sound.recorder.widget.R
import sound.recorder.widget.recording.database.AppDatabase
import sound.recorder.widget.recording.database.RecordedMusicSegment
import sound.recorder.widget.recording.database.RecordedTap
import sound.recorder.widget.recording.database.RecordingEntity
import java.io.File
import java.nio.ByteBuffer

/**
 * Memotong rekaman menjadi rekaman BARU (rekaman asli tidak diubah).
 * Audio mic dipotong dengan remux (tanpa encode ulang) → cepat, ringan, kualitas tetap.
 */
internal object RecordingTrim {

    private const val TAG = "RecordingTrim"
    const val MIN_LENGTH_MS = 1_000L

    private val gson = Gson()
    private val tapsType = object : TypeToken<List<RecordedTap>>() {}.type

    /**
     * Ketukan di dalam [startMs, endMs], digeser sehingga [shiftMs] menjadi 0.
     * Instrumen bernada panjang (punya OFF): nada yang ditahan melewati titik awal dibunyikan ulang di awal,
     * dan nada yang masih ditahan di titik akhir diberi OFF — agar tidak ada nada hilang / menggantung.
     */
    fun trimTaps(taps: List<RecordedTap>, startMs: Long, endMs: Long, shiftMs: Long): List<RecordedTap> {
        fun shifted(ms: Long) = (ms - shiftMs).coerceAtLeast(0L)
        val sorted = taps.sortedBy { it.timestamp }
        val inRange = sorted.filter { it.timestamp in startMs..endMs }.map { it.copy(timestamp = shifted(it.timestamp)) }
        if (!SustainNotes.usesOff(sorted)) return inRange

        val reStart = SustainNotes.heldBefore(sorted, startMs).map { it.copy(timestamp = shifted(startMs)) }
        val release = SustainNotes.heldPadsAt(sorted, endMs).map { SustainNotes.offFor(it, shifted(endMs)) }
        // sortedBy stabil: ON ulang tetap sebelum OFF di waktu yang sama
        return (reStart + inRange + release).sortedBy { it.timestamp }
    }

    /** Potongan lagu dipangkas ke [startMs, endMs]; posisi lagu ikut maju untuk bagian yang terpotong di awal. */
    fun trimMusic(music: List<RecordedMusicSegment>, startMs: Long, endMs: Long, shiftMs: Long): List<RecordedMusicSegment> =
        music.mapNotNull { s ->
            val at = maxOf(s.atMs, startMs)
            val end = minOf(s.endMs, endMs)
            if (end <= at) return@mapNotNull null
            s.copy(
                atMs = (at - shiftMs).coerceAtLeast(0L),
                endMs = end - shiftMs,
                posMs = s.posMs + (at - s.atMs)
            )
        }

    fun parseTaps(json: String): List<RecordedTap> = try {
        gson.fromJson<List<RecordedTap>>(json, tapsType).orEmpty()
    } catch (e: Exception) {
        emptyList()
    }

    /**
     * Buat & simpan rekaman hasil potong. Jalankan di coroutine (kerja berat di IO).
     * @return rekaman baru, atau null jika gagal (rekaman asli tetap utuh).
     */
    suspend fun saveTrimmedCopy(context: Context, rec: RecordingEntity, startMs: Long, endMs: Long): RecordingEntity? =
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            var newAudio: File? = null
            try {
                // Audio mic: potong dulu, waktu mulai sebenarnya (frame AAC terdekat) dipakai untuk menggeser ketukan
                var shiftMs = startMs
                val src = rec.audioPath?.let(::File)?.takeIf { it.exists() && it.length() > 0 }
                if (src != null) {
                    val out = File(app.filesDir, "REC_${System.currentTimeMillis()}.m4a")
                    val firstUs = remux(src.absolutePath, out, startMs * 1000, endMs * 1000)
                        ?: run { out.delete(); return@withContext null }
                    newAudio = out
                    shiftMs = firstUs / 1000
                }
                val taps = trimTaps(parseTaps(rec.eventsJson), startMs, endMs, shiftMs)
                val music = trimMusic(RecordedMusicJson.parse(rec.musicJson), startMs, endMs, shiftMs)
                // Bagian yang dipilih kosong (tanpa ketukan, audio, maupun lagu) → tidak ada yang bisa disimpan
                if (taps.isEmpty() && newAudio == null && music.isEmpty()) return@withContext null
                val trimmed = rec.copy(
                    id = 0,
                    name = app.getString(R.string.record_trim_copy_name, rec.name),
                    eventsJson = gson.toJson(taps),
                    audioPath = newAudio?.absolutePath,
                    createdAt = System.currentTimeMillis(),
                    durationMs = endMs - shiftMs,
                    musicJson = if (music.isEmpty()) null else gson.toJson(music)
                )
                val id = AppDatabase.getInstance(app).recordingDao().insert(trimmed)
                trimmed.copy(id = id.toInt())
            } catch (e: Exception) {
                Log.e(TAG, "trim gagal: ${e.message}")
                newAudio?.delete()
                null
            }
        }

    /**
     * Salin sampel audio [startUs, endUs] ke [out] (MP4/AAC) tanpa decode.
     * @return waktu (µs) sampel pertama yang disalin di file asli, atau null jika gagal / tidak ada sampel.
     */
    private fun remux(srcPath: String, out: File, startUs: Long, endUs: Long): Long? {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var started = false
        try {
            extractor.setDataSource(srcPath)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val outTrack = muxer.addTrack(format)
            muxer.start()
            started = true

            val size = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) else 0
            val buffer = ByteBuffer.allocate(maxOf(size, 64 * 1024))
            val info = MediaCodec.BufferInfo()
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            var firstUs = -1L
            while (true) {
                val read = extractor.readSampleData(buffer, 0)
                if (read < 0) break
                val pts = extractor.sampleTime
                if (pts > endUs) break
                if (pts >= startUs - 30_000) { // toleransi 1 frame AAC sebelum titik potong
                    if (firstUs < 0) firstUs = pts
                    info.set(0, read, pts - firstUs, MediaCodec.BUFFER_FLAG_KEY_FRAME)
                    muxer.writeSampleData(outTrack, buffer, info)
                }
                extractor.advance()
            }
            if (firstUs < 0) return null
            // stop() di sini (bukan di finally) agar file yang gagal ditutup dianggap gagal, bukan rekaman rusak
            started = false
            muxer.stop()
            return firstUs
        } catch (e: Exception) {
            Log.e(TAG, "remux gagal: ${e.message}")
            return null
        } finally {
            try { if (started) muxer?.stop() } catch (_: Exception) { }
            try { muxer?.release() } catch (_: Exception) { }
            extractor.release()
        }
    }
}

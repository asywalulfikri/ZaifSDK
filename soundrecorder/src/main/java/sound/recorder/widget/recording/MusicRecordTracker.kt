package sound.recorder.widget.recording

import android.content.Context
import android.os.SystemClock
import android.util.Log
import sound.recorder.widget.music.MusicPlayerManager
import sound.recorder.widget.recording.database.RecordedMusicSegment

/**
 * Mencatat lagu yang diputar [MusicPlayerManager] selama rekaman (lagu apa, mulai/jeda kapan, posisi berapa).
 * Hanya mencatat waktu — tidak ada decode/IO, jadi tidak menambah beban saat merekam.
 * Dipakai di main thread.
 */
internal class MusicRecordTracker(context: Context) {

    private val resources = context.applicationContext.resources
    private val segments = ArrayList<RecordedMusicSegment>()
    /** Potongan yang sedang berbunyi; endMs belum diketahui. */
    private var open: RecordedMusicSegment? = null
    private var startElapsed = 0L
    private var active = false
    private val observer: () -> Unit = { onMusicChanged() }

    /** @param startElapsed jam yang sama dengan ketukan (SystemClock.elapsedRealtime saat rekam mulai). */
    fun start(startElapsed: Long) {
        cancel()
        this.startElapsed = startElapsed
        active = true
        MusicPlayerManager.addTimelineObserver(observer)
        onMusicChanged() // lagu yang sudah berbunyi sebelum rekam dimulai
    }

    /** Potongan sejauh ini (untuk draft autosave). */
    fun snapshot(): List<RecordedMusicSegment> {
        if (!active) return emptyList()
        val list = ArrayList(segments)
        open?.let { o -> now().let { t -> if (t > o.atMs) list.add(o.copy(endMs = t)) } }
        return list
    }

    fun stop(): List<RecordedMusicSegment> {
        if (!active) return emptyList()
        closeOpen(now())
        val result = ArrayList(segments)
        cancel()
        return result
    }

    fun cancel() {
        active = false
        MusicPlayerManager.removeTimelineObserver(observer)
        segments.clear()
        open = null
    }

    private fun now() = SystemClock.elapsedRealtime() - startElapsed

    private fun onMusicChanged() {
        if (!active) return
        val t = now()
        closeOpen(t)
        if (!MusicPlayerManager.isPlaying) return
        val track = MusicPlayerManager.getCurrentTrack() ?: return
        val rawName = if (track.isRaw) rawNameOf(track.rawResId) ?: return else null
        val uri = if (track.isRaw) null else track.deviceUri?.toString() ?: return
        open = RecordedMusicSegment(
            atMs = t,
            endMs = t,
            posMs = MusicPlayerManager.getCurrentPosition().toLong(),
            rawName = rawName,
            uri = uri,
            title = track.title,
            volume = MusicPlayerManager.getVolume()
        )
    }

    private fun closeOpen(t: Long) {
        open?.let { if (t > it.atMs) segments.add(it.copy(endMs = t)) }
        open = null
    }

    private fun rawNameOf(resId: Int): String? = try {
        resources.getResourceEntryName(resId)
    } catch (e: Exception) {
        Log.w("MusicRecordTracker", "raw track tanpa nama resource: $resId")
        null
    }

    companion object {
        /** Geser potongan agar 0 ms = awal audio mic (sama seperti [InstrumentRecorderManager.alignToMicStart]). */
        fun align(segments: List<RecordedMusicSegment>, offsetMs: Long): List<RecordedMusicSegment> {
            if (offsetMs == 0L) return segments
            return segments.mapNotNull { s ->
                val end = s.endMs - offsetMs
                if (end <= 0L) return@mapNotNull null
                val at = s.atMs - offsetMs
                // Bagian lagu sebelum mic mulai dibuang; posisi lagu maju sebesar itu
                if (at >= 0L) s.copy(atMs = at, endMs = end) else s.copy(atMs = 0L, endMs = end, posMs = s.posMs - at)
            }
        }
    }
}

/** JSON kolom `musicJson`. Data rusak → dianggap tanpa lagu (rekaman tetap bisa diputar). */
internal object RecordedMusicJson {
    private val gson = com.google.gson.Gson()
    private val type = object : com.google.gson.reflect.TypeToken<List<RecordedMusicSegment>>() {}.type

    fun parse(json: String?): List<RecordedMusicSegment> {
        if (json.isNullOrEmpty()) return emptyList()
        return try {
            gson.fromJson<List<RecordedMusicSegment>>(json, type).orEmpty().filter { it.endMs > it.atMs && it.sourceKey != null }
        } catch (e: Exception) {
            Log.e("RecordedMusicJson", "musicJson tidak valid: ${e.message}")
            emptyList()
        }
    }
}

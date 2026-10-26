package sound.recorder.widget.recording

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import android.widget.Toast
import androidx.annotation.Keep
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import sound.recorder.widget.R
import sound.recorder.widget.recording.database.AppDatabase
import sound.recorder.widget.recording.database.RecordedMusicSegment
import sound.recorder.widget.recording.database.RecordedTap
import sound.recorder.widget.recording.database.RecordingEntity
import java.io.File
import java.text.DateFormat
import java.util.Date

/**
 * Supaya rekaman user tidak hilang:
 * - selama merekam, ketukan ditulis berkala ke file draft → dipulihkan kalau proses app mati mendadak
 * - rekaman yang belum sempat disimpan (panel dilepas / dialog simpan ditutup tanpa pilihan) disimpan otomatis
 *
 * Scope sendiri (bukan panelScope) agar penyimpanan tetap selesai walau View/Fragment sudah dihancurkan.
 * Hanya memegang applicationContext → tidak menahan Activity/View.
 */
internal object RecordingAutosave {

    private const val TAG = "RecordingAutosave"
    private const val DRAFT_FILE = "recording_draft.json"

    // Satu antrean berurutan (FIFO): draft yang ditulis sebelum save tidak bisa "menyusul" setelah draft dihapus
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val mutex = Mutex()
    private val gson = Gson()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Keep
    private data class Draft(
        val pid: Int,
        val startedAtWall: Long,
        val setName: String,
        val audioPath: String?,
        val isEarphoneRecording: Boolean,
        val events: List<RecordedTap>,
        val music: List<RecordedMusicSegment>? = null, // null pada draft versi lama
        /** true = audioPath milik rekaman lain (rekam lapis) → disalin saat disimpan, tidak dipakai bersama. */
        val audioShared: Boolean? = null,
        val name: String? = null
    )

    private fun draftFile(context: Context) = File(context.applicationContext.filesDir, DRAFT_FILE)

    /** Tulis/ganti draft (atomic: tulis ke .tmp lalu rename). `events` harus salinan. */
    fun writeDraft(
        context: Context,
        startedAtWall: Long,
        setName: String,
        audioPath: String?,
        isEarphoneRecording: Boolean,
        events: List<RecordedTap>,
        music: List<RecordedMusicSegment> = emptyList(),
        audioShared: Boolean = false,
        name: String? = null
    ) {
        val app = context.applicationContext
        scope.launch {
            mutex.withLock {
                try {
                    val json = gson.toJson(
                        Draft(Process.myPid(), startedAtWall, setName, audioPath, isEarphoneRecording, events, music, audioShared, name)
                    )
                    val file = draftFile(app)
                    val tmp = File(file.parentFile, "$DRAFT_FILE.tmp")
                    tmp.writeText(json)
                    if (!tmp.renameTo(file)) {
                        file.delete()
                        tmp.renameTo(file)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "writeDraft failed: ${e.message}")
                }
            }
        }
    }

    fun clearDraft(context: Context) {
        val app = context.applicationContext
        scope.launch { mutex.withLock { draftFile(app).delete() } }
    }

    /**
     * Simpan rekaman ke database, lalu hapus draft. Tetap jalan walau panel sudah dilepas.
     * @param name null → nama otomatis berdasarkan [startedAtWall].
     * @param onDone dipanggil di main thread: true = tersimpan.
     */
    fun save(
        context: Context,
        name: String?,
        startedAtWall: Long,
        setName: String,
        events: List<RecordedTap>,
        audioPath: String?,
        isEarphoneRecording: Boolean,
        music: List<RecordedMusicSegment> = emptyList(),
        audioShared: Boolean = false,
        onDone: ((saved: Boolean, error: String?) -> Unit)? = null
    ) {
        val app = context.applicationContext
        scope.launch {
            val result = mutex.withLock {
                try {
                    // Format sama dengan InstrumentRecorderManager.getEventsAsString (Gson default)
                    val eventsJson = gson.toJson(events)
                    // Mode mic: durasi = panjang audio asli (lebih panjang dari ketukan terakhir), minimal ketukan terakhir
                    val lastTap = events.maxOfOrNull { it.timestamp } ?: 0L
                    // Audio rekaman lain → salinan sendiri, agar menghapus salah satu tidak merusak yang lain
                    val ownAudio = if (audioShared && audioPath != null) copyAudio(app, audioPath) else audioPath
                    val durationMs = maxOf(
                        lastTap,
                        ownAudio?.let { audioDurationMs(it, waitForFinalize = true) } ?: 0L,
                        music.maxOfOrNull { it.endMs } ?: 0L
                    )
                    AppDatabase.getInstance(app).recordingDao().insert(
                        RecordingEntity(
                            name = name ?: autoName(app, startedAtWall),
                            setName = setName,
                            eventsJson = eventsJson,
                            durationMs = durationMs,
                            audioPath = ownAudio,
                            isEarphoneRecording = isEarphoneRecording,
                            musicJson = musicJsonOf(music)
                        )
                    )
                    draftFile(app).delete()
                    null
                } catch (e: Exception) {
                    // Draft sengaja tidak dihapus → masih bisa dipulihkan nanti
                    Log.e(TAG, "save failed: ${e.message}")
                    e.message ?: "error"
                }
            }
            onDone?.let { cb -> mainHandler.post { cb(result == null, result) } }
        }
    }

    /**
     * Pulihkan draft dari proses sebelumnya yang mati saat merekam. Draft milik proses ini
     * (rekaman/penyimpanan yang sedang berjalan) dilewati agar tidak dobel.
     */
    fun recoverIfAny(context: Context) {
        val app = context.applicationContext
        scope.launch {
            val recovered = mutex.withLock {
                val file = draftFile(app)
                if (!file.exists()) return@withLock false
                val draft = try {
                    gson.fromJson(file.readText(), Draft::class.java)
                } catch (e: Exception) {
                    null
                }
                if (draft == null) {
                    file.delete()
                    return@withLock false
                }
                if (draft.pid == Process.myPid()) return@withLock false
                val events = draft.events.orEmpty()
                if (events.isEmpty()) {
                    file.delete()
                    return@withLock false
                }
                try {
                    // Audio mic dari proses yang mati biasanya tidak bisa dibaca (file belum ditutup) → pakai ketukan saja
                    val audioDuration = draft.audioPath?.let { audioDurationMs(it, waitForFinalize = false) } ?: 0L
                    val audio = draft.audioPath?.takeIf { audioDuration > 0L }
                        ?.let { if (draft.audioShared == true) copyAudio(app, it) else it }
                    val music = draft.music.orEmpty()
                    AppDatabase.getInstance(app).recordingDao().insert(
                        RecordingEntity(
                            name = draft.name ?: autoName(app, draft.startedAtWall),
                            setName = draft.setName,
                            eventsJson = gson.toJson(events),
                            durationMs = maxOf(events.maxOf { it.timestamp }, audioDuration, music.maxOfOrNull { it.endMs } ?: 0L),
                            audioPath = audio,
                            isEarphoneRecording = draft.isEarphoneRecording,
                            musicJson = musicJsonOf(music)
                        )
                    )
                    file.delete()
                    Log.d(TAG, "recovered draft: ${events.size} events, audio=${audio != null}")
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "recover failed: ${e.message}")
                    false
                }
            }
            if (recovered) mainHandler.post {
                Toast.makeText(app, app.getString(R.string.record_recovered), Toast.LENGTH_LONG).show()
            }
        }
    }

    /** Salin file audio ke REC_* baru di filesDir. Gagal → exception (rekaman tidak disimpan setengah jadi). */
    private fun copyAudio(app: Context, path: String): String {
        val src = File(path)
        val dst = File(app.filesDir, "REC_${System.currentTimeMillis()}.${src.extension.ifEmpty { "m4a" }}")
        try {
            src.copyTo(dst, overwrite = false)
        } catch (e: Exception) {
            dst.delete()
            throw e
        }
        return dst.absolutePath
    }

    private fun musicJsonOf(music: List<RecordedMusicSegment>): String? =
        if (music.isEmpty()) null else gson.toJson(music)

    private fun autoName(context: Context, startedAtWall: Long): String {
        val time = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(startedAtWall))
        return context.getString(R.string.record_autosave_name, time)
    }

    /**
     * Durasi audio (ms), 0 jika tidak ada / tidak bisa dibaca.
     * waitForFinalize: MediaRecorder.stop() jalan asinkron, file mungkin belum ditutup saat autosave langsung
     * setelah stop → coba ulang sebentar (maks ±1,5 dtk, di thread IO).
     */
    private suspend fun audioDurationMs(path: String, waitForFinalize: Boolean): Long {
        val attempts = if (waitForFinalize) 6 else 1
        repeat(attempts) { i ->
            readDurationMs(path).takeIf { it > 0L }?.let { return it }
            if (i < attempts - 1) delay(250L)
        }
        return 0L
    }

    private fun readDurationMs(path: String): Long {
        val file = File(path)
        if (!file.exists() || file.length() == 0L) return 0L
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(path)
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            try { mmr.release() } catch (_: Exception) { }
        }
    }

    /**
     * Hapus file audio mic rekaman yang dibuang (Batal / tanpa ketukan) agar tidak menumpuk.
     * Pengaman: hanya file REC_* langsung di filesDir app — tidak pernah menyentuh file lain.
     */
    fun discardAudio(context: Context, path: String?) {
        if (path.isNullOrEmpty()) return
        val app = context.applicationContext
        scope.launch {
            mutex.withLock {
                try {
                    val file = File(path)
                    val inFilesDir = file.parentFile?.canonicalPath == app.filesDir.canonicalPath
                    if (inFilesDir && file.name.startsWith("REC_") && file.delete()) {
                        Log.d(TAG, "discarded unused mic file ${file.name}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "discardAudio failed: ${e.message}")
                }
            }
        }
    }
}

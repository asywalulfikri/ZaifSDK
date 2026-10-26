package sound.recorder.widget.recording

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sound.recorder.widget.recording.database.RecordedTap

class InstrumentRecorderManager(
    private val onTriggerNote: (event: RecordedTap) -> Unit
) {
    private var isRecording = false
    private var startTime = 0L
    private val recordedEvents = mutableListOf<RecordedTap>()
    private val playbackHandler = Handler(Looper.getMainLooper())
    private var playbackToken = 0L
    private var playbackEvents: List<RecordedTap> = emptyList()
    private var playbackIndex = 0
    private var playbackStartedAt = 0L
    private var playbackComplete: (() -> Unit)? = null
    /** Nada bernada panjang yang sedang berbunyi saat putar ulang (pad yang sudah ON tapi belum OFF). */
    private val playbackHeld = LinkedHashSet<Int>()

    // Inisialisasi Gson untuk konversi JSON
    private val gson = Gson()

    fun isRecording() = isRecording

    /** SystemClock.elapsedRealtime saat rekaman dimulai (jam yang dipakai timestamp ketukan). */
    val recordingStartElapsed: Long get() = startTime

    fun startRecording() {
        isRecording = true
        recordedEvents.clear()
        // Jam monotonic: tidak lompat walau jam HP disinkron ulang saat merekam
        startTime = SystemClock.elapsedRealtime()
    }

    fun stopRecording(): List<RecordedTap> {
        isRecording = false
        // Instrumen bernada panjang: nada yang masih ditahan saat stop diberi OFF agar tidak menggantung
        return SustainNotes.closeHeld(ArrayList(recordedEvents), SystemClock.elapsedRealtime() - startTime)
    }

    /** Salinan ketukan yang sudah terekam sejauh ini (untuk draft autosave). */
    fun snapshotEvents(): List<RecordedTap> = ArrayList(recordedEvents)

    fun onNoteEvent(padIndex: Int, metadata: String? = null) {
        if (isRecording) {
            val ts = SystemClock.elapsedRealtime() - startTime
            recordedEvents.add(RecordedTap(padIndex, ts, metadata))
        }
    }

    /**
     * Geser ketukan supaya 0 ms = awal audio mic (mic baru mulai setelah prepare/start di background).
     * Ketukan sebelum mic mulai tidak punya audio → ditaruh di 0. Format data tidak berubah.
     * @param micStartedAtElapsed [AudioEngine.micStartedAtElapsed]; 0 = mic tidak jalan → tidak digeser.
     */
    fun alignToMicStart(events: List<RecordedTap>, micStartedAtElapsed: Long): List<RecordedTap> {
        if (micStartedAtElapsed <= 0L) return events
        val offset = micStartedAtElapsed - startTime
        Log.d("RecordSync", "mic offset=${offset}ms events=${events.size}")
        if (offset == 0L) return events
        return events.map { it.copy(timestamp = (it.timestamp - offset).coerceAtLeast(0L)) }
    }

    fun play(events: List<RecordedTap>, onComplete: () -> Unit = {}) {
        stopPlayback(releaseHeld = true)
        if (events.isEmpty()) {
            onComplete()
            return
        }

        playbackEvents = events.sortedBy { it.timestamp }
        playbackUsesOff = SustainNotes.usesOff(playbackEvents)
        playbackIndex = 0
        playbackStartedAt = System.currentTimeMillis()
        playbackComplete = onComplete
        val token = playbackToken
        scheduleNextEvent(token)
    }

    fun stopPlayback() = stopPlayback(releaseHeld = false)

    /**
     * @param releaseHeld true = kirim OFF ke host untuk nada yang masih berbunyi (Stop oleh user).
     *        false saat layar ditutup: view host mungkin sudah dihancurkan, jangan panggil callback host.
     */
    fun stopPlayback(releaseHeld: Boolean) {
        playbackToken++
        playbackHandler.removeCallbacksAndMessages(null)
        playbackEvents = emptyList()
        playbackIndex = 0
        playbackComplete = null
        if (releaseHeld) releaseHeldNotes() else playbackHeld.clear()
    }

    private fun releaseHeldNotes() {
        if (playbackHeld.isEmpty()) return
        val pads = playbackHeld.toList()
        playbackHeld.clear()
        pads.forEach { onTriggerNote(SustainNotes.offFor(it, 0L)) }
    }

    private fun trigger(event: RecordedTap) {
        if (SustainNotes.isOff(event)) playbackHeld.remove(event.padIndex)
        else if (playbackUsesOff) playbackHeld.add(event.padIndex)
        onTriggerNote(event)
    }

    private var playbackUsesOff = false

    private fun scheduleNextEvent(token: Long) {
        if (token != playbackToken || playbackIndex >= playbackEvents.size) {
            if (token == playbackToken) {
                val callback = playbackComplete
                playbackComplete = null
                playbackEvents = emptyList()
                callback?.invoke()
            }
            return
        }

        val event = playbackEvents[playbackIndex]
        val elapsed = System.currentTimeMillis() - playbackStartedAt
        val delay = (event.timestamp - elapsed).coerceAtLeast(0L)
        playbackHandler.postDelayed({
            if (token != playbackToken) return@postDelayed
            trigger(event)
            playbackIndex++
            if (playbackIndex >= playbackEvents.size) {
                playbackHandler.postDelayed({
                    if (token == playbackToken) {
                        // Rekaman lama tanpa OFF di akhir → lepas nada yang masih berbunyi
                        releaseHeldNotes()
                        val callback = playbackComplete
                        playbackComplete = null
                        playbackEvents = emptyList()
                        callback?.invoke()
                    }
                }, 200L)
            } else {
                scheduleNextEvent(token)
            }
        }, delay)
    }

    // --- PENYESUAIAN PENTING: MENGGUNAKAN GSON ---

    /**
     * Mengubah List Events menjadi String JSON untuk disimpan ke database
     */
    suspend fun getEventsAsString(events: List<RecordedTap>): String = withContext(Dispatchers.Default) {
        try {
            gson.toJson(events)
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Mengubah String JSON dari database kembali menjadi List<RecordedTap>
     */
    suspend fun parseJson(json: String): List<RecordedTap> = withContext(Dispatchers.Default) {
        if (json.isEmpty()) {
            emptyList()
        } else {
            try {
                val type = object : TypeToken<List<RecordedTap>>() {}.type
                gson.fromJson(json, type)
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}

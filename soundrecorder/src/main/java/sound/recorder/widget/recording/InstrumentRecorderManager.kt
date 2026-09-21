package sound.recorder.widget.recording

import android.os.Handler
import android.os.Looper
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

    // Inisialisasi Gson untuk konversi JSON
    private val gson = Gson()

    fun isRecording() = isRecording

    fun startRecording() {
        isRecording = true
        recordedEvents.clear()
        startTime = System.currentTimeMillis()
    }

    fun stopRecording(): List<RecordedTap> {
        isRecording = false
        return ArrayList(recordedEvents) // Mengembalikan salinan list
    }

    fun onNoteEvent(padIndex: Int, metadata: String? = null) {
        if (isRecording) {
            val ts = System.currentTimeMillis() - startTime
            recordedEvents.add(RecordedTap(padIndex, ts, metadata))
        }
    }

    fun play(events: List<RecordedTap>, onComplete: () -> Unit = {}) {
        stopPlayback()
        if (events.isEmpty()) {
            onComplete()
            return
        }

        playbackEvents = events.sortedBy { it.timestamp }
        playbackIndex = 0
        playbackStartedAt = System.currentTimeMillis()
        playbackComplete = onComplete
        val token = playbackToken
        scheduleNextEvent(token)
    }

    fun stopPlayback() {
        playbackToken++
        playbackHandler.removeCallbacksAndMessages(null)
        playbackEvents = emptyList()
        playbackIndex = 0
        playbackComplete = null
    }

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
            onTriggerNote(event)
            playbackIndex++
            if (playbackIndex >= playbackEvents.size) {
                playbackHandler.postDelayed({
                    if (token == playbackToken) {
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

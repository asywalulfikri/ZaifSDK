package sound.recorder.widget.recording

import android.os.Handler
import android.os.Looper
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import sound.recorder.widget.recording.database.RecordedTap

class InstrumentRecorderManager(
    private val onTriggerNote: (event: RecordedTap) -> Unit
) {
    private var isRecording = false
    private var startTime = 0L
    private val recordedEvents = mutableListOf<RecordedTap>()
    private val playbackHandler = Handler(Looper.getMainLooper())

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

        events.forEach { event ->
            playbackHandler.postDelayed({
                onTriggerNote(event)
            }, event.timestamp)
        }

        // Tambahkan delay sedikit setelah not terakhir selesai agar tidak terputus kasar
        val totalDuration = (events.lastOrNull()?.timestamp ?: 0L) + 200L
        playbackHandler.postDelayed({ onComplete() }, totalDuration)
    }

    fun stopPlayback() {
        playbackHandler.removeCallbacksAndMessages(null)
    }

    // --- PENYESUAIAN PENTING: MENGGUNAKAN GSON ---

    /**
     * Mengubah List Events menjadi String JSON untuk disimpan ke database
     */
    fun getEventsAsString(events: List<RecordedTap>): String {
        return try {
            gson.toJson(events)
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Mengubah String JSON dari database kembali menjadi List<RecordedTap>
     */
    fun parseJson(json: String): List<RecordedTap> {
        return if (json.isEmpty()) {
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
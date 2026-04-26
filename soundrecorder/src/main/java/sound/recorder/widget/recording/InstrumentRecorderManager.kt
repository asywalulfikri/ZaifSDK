package sound.recorder.widget.recording

import android.os.Handler
import android.os.Looper
import android.util.Log
import sound.recorder.widget.recording.database.RecordedTap

class InstrumentRecorderManager(
    private val onTriggerNote: (event: RecordedTap) -> Unit
) {
    private var isRecording = false
    private var startTime = 0L
    private val recordedEvents = mutableListOf<RecordedTap>()
    private val playbackHandler = Handler(Looper.getMainLooper())

    fun isRecording() = isRecording

    fun startRecording() {
        isRecording = true
        recordedEvents.clear()
        startTime = System.currentTimeMillis()
    }

    fun stopRecording(): List<RecordedTap> {
        isRecording = false
        return recordedEvents.toList()
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

        // Memberikan buffer 200ms setelah note terakhir sebelum memicu onComplete
        val totalDuration = (events.lastOrNull()?.timestamp ?: 0L) + 200L
        playbackHandler.postDelayed({ onComplete() }, totalDuration)
    }

    fun stopPlayback() {
        playbackHandler.removeCallbacksAndMessages(null)
    }

    // ─── UTILITY UNTUK DATABASE ───

    /**
     * Mengubah List Event menjadi String untuk disimpan di kolom eventsJson (Database)
     */
    fun getEventsAsString(events: List<RecordedTap>): String {
        return events.joinToString("|") {
            "padIndex=${it.padIndex},timestamp=${it.timestamp},meta=${it.metadata ?: ""}"
        }
    }

    /**
     * Fungsi Tambahan: Mengubah String dari Database kembali menjadi List objek.
     * Sangat berguna saat Playback agar tidak perlu menulis Regex berulang kali di Fragment.
     */
    fun parseJson(json: String): List<RecordedTap> {
        if (json.isEmpty()) return emptyList()
        val result = mutableListOf<RecordedTap>()
        val pattern = Regex("""padIndex=(\d+),timestamp=(\d+),meta=([^|]*)""")
        try {
            pattern.findAll(json).forEach { matchResult ->
                val padIndex = matchResult.groupValues[1].toInt()
                val timestamp = matchResult.groupValues[2].toLong()
                val metadata = matchResult.groupValues[3].trim().takeIf { it.isNotEmpty() && it != "null" }
                result.add(RecordedTap(padIndex, timestamp, metadata))
            }
        } catch (e: Exception) {
            Log.e("RecorderManager", "Error parsing events: ${e.message}")
        }
        return result
    }
}
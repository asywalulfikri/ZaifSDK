package sound.recorder.widget.recording

import android.os.Handler
import android.os.Looper
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

    // Fungsi record sekarang menerima metadata (misal: nama suara)
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

        val totalDuration = (events.lastOrNull()?.timestamp ?: 0L) + 200L
        playbackHandler.postDelayed({ onComplete() }, totalDuration)
    }

    fun stopPlayback() {
        playbackHandler.removeCallbacksAndMessages(null)
    }

    // Untuk simpan ke Database (Parsing metadata juga)
    fun getEventsAsString(events: List<RecordedTap>): String {
        return events.joinToString("|") {
            "padIndex=${it.padIndex},timestamp=${it.timestamp},meta=${it.metadata ?: ""}"
        }
    }

}
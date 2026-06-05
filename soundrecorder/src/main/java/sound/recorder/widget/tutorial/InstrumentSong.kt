package sound.recorder.widget.tutorial

import androidx.annotation.Keep

data class InstrumentSong(
    val name: String,
    val notes: List<InstrumentNote>
)

data class InstrumentNote(
    val padIndex:Int,
    val timeMs: Long,
    val durationMs: Long = 400L
)

@Keep
data class SongJson(
    val name: String,
    val events: List<EventJson>
)

@Keep
data class EventJson(
    val metadata: String,
    val padIndex: Int,
    val timestamp: Long
)

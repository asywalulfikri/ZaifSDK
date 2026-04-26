package sound.recorder.widget.recording.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val setName: String,
    val eventsJson: String,   // JSON dari List<RecordedTap>
    val createdAt: Long = System.currentTimeMillis(),
    val durationMs: Long = 0,
    val musicPath: String?,
    val musicOffset: Int // SIMPAN DISINI (misal 30000)
)
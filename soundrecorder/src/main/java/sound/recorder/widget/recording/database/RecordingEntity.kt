package sound.recorder.widget.recording.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val setName: String,
    val eventsJson: String,   // JSON dari List<RecordedTap>
    val audioPath: String? = null, // KOLOM BARU: Alamat file audio mp4/wav
    val createdAt: Long = System.currentTimeMillis(),
    val durationMs: Long = 0,
    val isEarphoneRecording: Boolean = false
)
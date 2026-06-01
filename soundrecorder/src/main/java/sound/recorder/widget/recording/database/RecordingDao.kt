package sound.recorder.widget.recording.database

// RecordingDao.kt

import androidx.room.*

@Dao
interface RecordingDao {
    @Insert
    suspend fun insert(recording: RecordingEntity): Long

    @Query("SELECT * FROM recordings ORDER BY createdAt DESC")
    suspend fun getAll(): List<RecordingEntity>

    @Delete
    suspend fun delete(recording: RecordingEntity)

    @Query("UPDATE recordings SET name = :newName WHERE id = :id")
    suspend fun updateName(id: Int, newName: String)
}
package sound.recorder.widget.db

import androidx.room.*

@Dao
internal interface AudioRecordDAO {
    @Query("SELECT * FROM audioRecords")
    fun getAll(): List<AudioRecord>

    @Query("SELECT * FROM audioRecords ORDER BY date DESC")
    fun getAllByDateDESC(): List<AudioRecord>

    @Query("SELECT * FROM audioRecords WHERE filename LIKE :searchQuery")
    fun searchDatabase(searchQuery: String): List<AudioRecord>

    @Query("DELETE FROM audioRecords")
    fun deleteAll()

    @Insert
    fun insert(vararg audioRecord: AudioRecord)

    @Delete
    fun delete(audioRecord: AudioRecord)

    @Delete
    fun delete(audioRecords: List<AudioRecord>)

    @Update
    fun update(audioRecord: AudioRecord)
}
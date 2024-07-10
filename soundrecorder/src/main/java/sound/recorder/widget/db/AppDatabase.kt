package sound.recorder.widget.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [AudioRecord::class], version = 1)
internal abstract class AppDatabase : RoomDatabase(){
    abstract fun audioRecordDAO(): AudioRecordDAO
}
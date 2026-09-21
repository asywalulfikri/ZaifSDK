package sound.recorder.widget.db

import androidx.room.Database
import android.content.Context
import androidx.room.RoomDatabase
import androidx.room.Room

@Database(entities = [AudioRecord::class], version = 1)
internal abstract class AppDatabase : RoomDatabase(){
    abstract fun audioRecordDAO(): AudioRecordDAO

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "audioRecords"
                ).build().also { INSTANCE = it }
            }
        }
    }
}

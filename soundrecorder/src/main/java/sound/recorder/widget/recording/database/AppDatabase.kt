package sound.recorder.widget.recording.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import sound.recorder.widget.recording.database.RecordingDao
import sound.recorder.widget.recording.database.RecordingEntity

@Database(entities = [RecordingEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Migrasi dari Versi 1 ke 3
        private val MIGRATION_1_3 = object : Migration(1, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE recordings ADD COLUMN musicPath TEXT DEFAULT NULL")
                database.execSQL("ALTER TABLE recordings ADD COLUMN musicOffset INTEGER NOT NULL DEFAULT 0")
            }
        }

        // Migrasi dari Versi 2 ke 3
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE recordings ADD COLUMN musicPath TEXT DEFAULT NULL")
                database.execSQL("ALTER TABLE recordings ADD COLUMN musicOffset INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "drum_db"
                )
                    // Masukkan semua kemungkinan migrasi
                    .addMigrations(MIGRATION_1_3, MIGRATION_2_3)
                    // Jika terjadi error migrasi yang tidak terduga, hapus & buat baru daripada crash
                    .fallbackToDestructiveMigration()
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}
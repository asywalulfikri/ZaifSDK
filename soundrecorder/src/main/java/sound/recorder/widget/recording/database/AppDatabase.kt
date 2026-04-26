package sound.recorder.widget.recording.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// 1. Pastikan versi sekarang adalah 4
@Database(entities = [RecordingEntity::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Migrasi dari Versi 1 ke 4
        private val MIGRATION_1_4 = object : Migration(1, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE recordings ADD COLUMN musicPath TEXT DEFAULT NULL")
                database.execSQL("ALTER TABLE recordings ADD COLUMN musicOffset INTEGER NOT NULL DEFAULT 0")
            }
        }

        // Migrasi dari Versi 2 ke 4
        private val MIGRATION_2_4 = object : Migration(2, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE recordings ADD COLUMN musicPath TEXT DEFAULT NULL")
                database.execSQL("ALTER TABLE recordings ADD COLUMN musicOffset INTEGER NOT NULL DEFAULT 0")
            }
        }

        // Migrasi dari Versi 3 ke 4 (PENTING: Ini yang menangani error Identity Hash tadi)
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Tambahkan kolom jika belum ada di versi 3
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
                    // Daftarkan semua jalur migrasi yang mungkin dilalui user
                    .addMigrations(MIGRATION_1_4, MIGRATION_2_4, MIGRATION_3_4)
                    // Pengaman terakhir: Jika versi naik tapi migrasi gagal, reset DB agar tidak crash
                    .fallbackToDestructiveMigration()
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}
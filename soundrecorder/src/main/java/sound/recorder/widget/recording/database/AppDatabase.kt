package sound.recorder.widget.recording.database

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Database rekaman user (dipakai semua app alat musik yang memakai SDK ini).
 *
 * PENTING saat mengubah [RecordingEntity]:
 * 1. Naikkan `version`.
 * 2. Tambahkan Migration(versiLama, versiBaru) ke [MIGRATIONS] — JANGAN pakai fallbackToDestructiveMigration,
 *    itu menghapus semua rekaman user di semua app.
 * 3. Commit file skema baru di `soundrecorder/schemas/` (dibuat otomatis saat build).
 * Tanpa migration, app akan crash saat membuka database setelah update — jadi selalu uji update dari versi lama.
 */
@Database(entities = [RecordingEntity::class], version = 6, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** Skema v5 persis seperti hasil Room (schemas/…/5.json). */
        private const val CREATE_V5 =
            "CREATE TABLE IF NOT EXISTS `recordings` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `setName` TEXT NOT NULL, `eventsJson` TEXT NOT NULL, `audioPath` TEXT, " +
                "`createdAt` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, `isEarphoneRecording` INTEGER NOT NULL)"

        /** Kolom v5 → nilai pengganti jika kolom tidak ada / NULL di tabel lama. */
        private val V5_COLUMNS = linkedMapOf(
            "id" to null,
            "name" to "''",
            "setName" to "''",
            "eventsJson" to "''",
            "audioPath" to "NULL",
            "createdAt" to "CAST(strftime('%s','now') AS INTEGER) * 1000",
            "durationMs" to "0",
            "isEarphoneRecording" to "0"
        )

        /**
         * Versi 1–4 dulu dihapus otomatis (destructive). Skemanya tidak tercatat dan versi 3 sempat punya
         * dua bentuk berbeda (dengan/tanpa musicPath, musicOffset), jadi tidak bisa pakai ALTER TABLE biasa.
         * Tabel dibangun ulang ke skema v5 dan hanya kolom yang memang ada yang disalin — rekaman lama tetap ada.
         */
        private class LegacyToV5(from: Int) : Migration(from, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    copyToV5(db)
                } catch (e: Exception) {
                    // Jaring pengaman: jangan crash & jangan hapus data. Tabel lama disimpan sebagai backup
                    // (bisa dipulihkan di versi berikutnya), app jalan dengan tabel v5 kosong.
                    Log.e("AppDatabase", "Legacy migration $startVersion→5 failed, keeping backup: ${e.message}")
                    keepBackupAndCreateV5(db, startVersion)
                }
            }

            private fun tableExists(db: SupportSQLiteDatabase, name: String): Boolean =
                db.query("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", arrayOf(name))
                    .use { it.moveToFirst() }

            private fun keepBackupAndCreateV5(db: SupportSQLiteDatabase, from: Int) {
                val backup = "recordings_backup_v${from}_${System.currentTimeMillis()}"
                // Tidak ada DROP: semua tabel yang mungkin berisi data hanya di-rename jadi backup
                if (tableExists(db, "recordings_old")) {
                    db.execSQL("ALTER TABLE `recordings_old` RENAME TO `$backup`")
                }
                if (tableExists(db, "recordings")) {
                    db.execSQL("ALTER TABLE `recordings` RENAME TO `${backup}_b`")
                }
                db.execSQL(CREATE_V5)
            }

            private fun copyToV5(db: SupportSQLiteDatabase) {
                val oldColumns = mutableSetOf<String>()
                db.query("PRAGMA table_info(`recordings`)").use { c ->
                    val nameIdx = c.getColumnIndex("name")
                    while (c.moveToNext()) oldColumns.add(c.getString(nameIdx))
                }
                if (oldColumns.isEmpty()) {
                    db.execSQL(CREATE_V5)
                    return
                }

                db.execSQL("ALTER TABLE `recordings` RENAME TO `recordings_old`")
                db.execSQL(CREATE_V5)
                if ("id" in oldColumns) {
                    val targets = V5_COLUMNS.keys.joinToString(", ") { "`$it`" }
                    val values = V5_COLUMNS.entries.joinToString(", ") { (col, fallback) ->
                        when {
                            fallback == null -> "`$col`"
                            col in oldColumns -> "COALESCE(`$col`, $fallback)"
                            else -> fallback
                        }
                    }
                    db.execSQL("INSERT INTO `recordings` ($targets) SELECT $values FROM `recordings_old`")
                }
                db.execSQL("DROP TABLE `recordings_old`")
            }
        }

        /** v6: lagu yang ikut direkam. Hanya menambah kolom nullable — data lama tidak disentuh. */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `recordings` ADD COLUMN `musicJson` TEXT")
            }
        }

        // Versi 1–4 → 5 (bangun ulang), lalu 5 → 6 dijalankan berurutan oleh Room
        private val MIGRATIONS: Array<Migration> = arrayOf(
            LegacyToV5(1), LegacyToV5(2), LegacyToV5(3), LegacyToV5(4), MIGRATION_5_6
        )

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "drum_db" // Nama database kamu
                )
                    .addMigrations(*MIGRATIONS)
                    // Downgrade (user pasang APK versi lama) tetap reset seperti sebelumnya, daripada crash
                    .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

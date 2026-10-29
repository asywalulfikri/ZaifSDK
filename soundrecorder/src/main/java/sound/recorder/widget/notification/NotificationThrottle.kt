package sound.recorder.widget.notification

import android.content.Context

/**
 * Batas bersama untuk notifikasi lokal SDK (pengingat buka aplikasi & klaim koin) agar tidak mengganggu:
 * - paling banyak satu notifikasi per [MIN_GAP_MS], apa pun jenisnya
 * - tidak dikirim bila aplikasi baru dibuka dalam [RECENT_OPEN_MS] (user tidak perlu diingatkan)
 */
internal object NotificationThrottle {
    private const val PREFS = "zaif_notification_throttle"
    private const val KEY_LAST_SHOWN = "last_shown_at"
    private const val KEY_LAST_OPEN = "last_open_at"

    private const val MIN_GAP_MS = 48 * 60 * 60 * 1000L
    private const val RECENT_OPEN_MS = 20 * 60 * 60 * 1000L

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Dipanggil saat aplikasi tampil ke user. */
    fun markAppOpened(context: Context) {
        prefs(context).edit().putLong(KEY_LAST_OPEN, System.currentTimeMillis()).apply()
    }

    fun canShow(context: Context): Boolean {
        val p = prefs(context)
        val now = System.currentTimeMillis()
        // Jam HP dimundurkan → nilai tersimpan ada di masa depan; anggap lama sudah lewat
        val sinceShown = now - p.getLong(KEY_LAST_SHOWN, 0L)
        val sinceOpen = now - p.getLong(KEY_LAST_OPEN, 0L)
        val shownRecently = sinceShown in 0 until MIN_GAP_MS
        val openedRecently = sinceOpen in 0 until RECENT_OPEN_MS
        return !shownRecently && !openedRecently
    }

    fun markShown(context: Context) {
        prefs(context).edit().putLong(KEY_LAST_SHOWN, System.currentTimeMillis()).apply()
    }
}

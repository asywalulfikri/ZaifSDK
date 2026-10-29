package sound.recorder.widget.rewards

import android.content.Context
import sound.recorder.widget.util.CoinManager

/**
 * Akses tanpa iklan sementara yang ditukar dengan koin [CoinManager].
 * Penukaran saat masih aktif menambah durasi, bukan mengganti.
 *
 * Host app menggabungkannya ke status premium, misal:
 * `isPremium = hasPurchasedPremium || PremiumPass.isActive(context)`
 */
object PremiumPass {
    private const val PREFS            = "zaif_premium_pass"
    private const val KEY_EXPIRES_AT   = "expires_at"
    private const val KEY_REDEEMED_AT  = "redeemed_at"
    private const val KEY_REDEEM_COUNT = "redeem_count"

    /** Harga penukaran pertama (lebih murah agar pengguna baru mencicipi), lalu [costCoins]. */
    @JvmStatic
    var firstCostCoins = 50

    @JvmStatic
    var costCoins = 100

    @JvmStatic
    var durationDays = 2

    private val lock = Any()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Harga penukaran berikutnya. */
    @JvmStatic
    fun cost(context: Context): Int =
        if (prefs(context).getInt(KEY_REDEEM_COUNT, 0) == 0) firstCostCoins else costCoins

    @JvmStatic
    fun isFirstRedeem(context: Context): Boolean = prefs(context).getInt(KEY_REDEEM_COUNT, 0) == 0

    @JvmStatic
    fun isActive(context: Context): Boolean = remainingMs(context) > 0

    @JvmStatic
    fun remainingMs(context: Context): Long {
        val p = prefs(context)
        val now = System.currentTimeMillis()
        // Jam HP dimundurkan ke sebelum penukaran → anggap tidak sah.
        if (now < p.getLong(KEY_REDEEMED_AT, 0L)) return 0L
        return (p.getLong(KEY_EXPIRES_AT, 0L) - now).coerceAtLeast(0L)
    }

    /** Tukar [cost] koin dengan [durationDays] hari tanpa iklan. false bila koin kurang. */
    @JvmStatic
    fun redeem(context: Context): Boolean {
        synchronized(lock) {
            if (!CoinManager.spendCoins(context, cost(context))) return false
            val now = System.currentTimeMillis()
            val base = now + remainingMs(context)
            val p = prefs(context)
            p.edit()
                .putLong(KEY_REDEEMED_AT, now)
                .putLong(KEY_EXPIRES_AT, base + durationDays * 24 * 60 * 60 * 1000L)
                .putInt(KEY_REDEEM_COUNT, p.getInt(KEY_REDEEM_COUNT, 0) + 1)
                .apply()
            return true
        }
    }
}

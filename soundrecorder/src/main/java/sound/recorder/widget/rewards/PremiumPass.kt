package sound.recorder.widget.rewards

import android.content.Context
import sound.recorder.widget.util.CoinManager
import sound.recorder.widget.util.ZaifAnalytics

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

    /**
     * false (bawaan) = penukaran koin dimatikan: kartu Premium Pass tidak tampil di dialog Misi dan
     * [redeem] selalu gagal. Pass yang SUDAH ditukar tetap berlaku sampai habis ([isActive]), supaya
     * koin user yang terlanjur dipakai tidak hilang.
     *
     * **Alasan:** 2 hari tanpa iklan terlalu murah dibanding pendapatan iklan yang hilang (keputusan
     * pemilik app, 2026-11-04). Host app boleh menyalakan lagi dengan `PremiumPass.isEnabled = true`.
     */
    @JvmStatic
    var isEnabled = false

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

    /**
     * Pindahkan data pass dari penyimpanan lama milik app (mis. Belira: `"premium_pass"`, kunci
     * `expires_at`, `redeemed_at`, `redeem_count`) ke SDK, supaya pass yang masih berjalan tidak hilang
     * saat app beralih ke [PremiumPass] SDK. Hanya jalan sekali: dilewati bila SDK sudah punya data,
     * dan data lama dihapus setelah disalin. Panggil di thread background sebelum UI membaca pass.
     */
    @JvmStatic
    fun importFrom(context: Context, legacyPrefsName: String) {
        if (legacyPrefsName == PREFS) return
        synchronized(lock) {
            val old = context.applicationContext.getSharedPreferences(legacyPrefsName, Context.MODE_PRIVATE)
            if (!old.contains(KEY_REDEEM_COUNT) && !old.contains(KEY_EXPIRES_AT)) return
            val p = prefs(context)
            if (!p.contains(KEY_REDEEM_COUNT) && !p.contains(KEY_EXPIRES_AT)) {
                p.edit()
                    .putLong(KEY_EXPIRES_AT, old.getLong(KEY_EXPIRES_AT, 0L))
                    .putLong(KEY_REDEEMED_AT, old.getLong(KEY_REDEEMED_AT, 0L))
                    .putInt(KEY_REDEEM_COUNT, old.getInt(KEY_REDEEM_COUNT, 0))
                    .commit()
            }
            old.edit().clear().apply()
        }
    }

    /** Tukar [cost] koin dengan [durationDays] hari tanpa iklan. false bila koin kurang. */
    @JvmStatic
    fun redeem(context: Context): Boolean {
        if (!isEnabled) return false
        synchronized(lock) {
            val cost = cost(context)
            if (!CoinManager.spendCoins(context, cost, ZaifAnalytics.PURPOSE_PREMIUM_PASS)) return false
            val now = System.currentTimeMillis()
            val base = now + remainingMs(context)
            val p = prefs(context)
            p.edit()
                .putLong(KEY_REDEEMED_AT, now)
                .putLong(KEY_EXPIRES_AT, base + durationDays * 24 * 60 * 60 * 1000L)
                .putInt(KEY_REDEEM_COUNT, p.getInt(KEY_REDEEM_COUNT, 0) + 1)
                .apply()
            ZaifAnalytics.premiumPassRedeemed(context, cost)
            return true
        }
    }
}

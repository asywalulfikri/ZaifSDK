package sound.recorder.widget.util

import android.content.Context
import android.graphics.Paint
import android.os.Build
import sound.recorder.widget.notification.CoinNotificationWorker

object CoinManager {

    private const val COIN_EMOJI = "🪙"
    private const val COIN_EMOJI_FALLBACK = "💰"

    /**
     * Emoji koin yang aman ditampilkan di HP ini. 🪙 baru ada di font Android 11+; di HP lama
     * tampil sebagai kotak, jadi diganti 💰 (ada sejak Android 4.4). Dicek sekali lalu disimpan.
     */
    @JvmStatic
    val icon: String by lazy {
        val supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && runCatching { Paint().hasGlyph(COIN_EMOJI) }.getOrDefault(false))
        if (supported) COIN_EMOJI else COIN_EMOJI_FALLBACK
    }

    /** Ganti 🪙 di teks (mis. dari strings.xml) dengan [icon]. */
    @JvmStatic
    fun withIcon(text: String): String =
        if (icon == COIN_EMOJI) text else text.replace(COIN_EMOJI, icon)

    private const val PREFS_NAME        = "zaif_coins"
    private const val KEY_BALANCE       = "balance"
    private const val KEY_LAST_DAILY    = "last_daily"
    const val DAILY_COINS               = 2
    const val UNLOCK_COST               = 1
    private const val DAILY_INTERVAL_MS = 5 * 60 * 60 * 1000L

    fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getBalance(context: Context): Int =
        prefs(context).getInt(KEY_BALANCE, 0)

    fun addCoins(context: Context, amount: Int) {
        prefs(context).edit()
            .putInt(KEY_BALANCE, getBalance(context) + amount)
            .apply()
    }

    fun spendCoin(context: Context): Boolean {
        val current = getBalance(context)
        if (current < UNLOCK_COST) return false
        prefs(context).edit().putInt(KEY_BALANCE, current - UNLOCK_COST).apply()
        return true
    }

    /** Kurangi saldo sebanyak [amount]; false bila koin tidak cukup. */
    @Synchronized
    fun spendCoins(context: Context, amount: Int): Boolean {
        if (amount <= 0) return false
        val current = getBalance(context)
        if (current < amount) return false
        prefs(context).edit().putInt(KEY_BALANCE, current - amount).apply()
        return true
    }

    fun canClaimDaily(context: Context): Boolean {
        val last = prefs(context).getLong(KEY_LAST_DAILY, 0L)
        return System.currentTimeMillis() - last >= DAILY_INTERVAL_MS
    }

    fun nextClaimInMs(context: Context): Long {
        val last = prefs(context).getLong(KEY_LAST_DAILY, 0L)
        return maxOf(0L, last + DAILY_INTERVAL_MS - System.currentTimeMillis())
    }

    fun claimDailyBonus(context: Context): Boolean {
        if (!canClaimDaily(context)) return false
        prefs(context).edit().putLong(KEY_LAST_DAILY, System.currentTimeMillis()).apply()
        addCoins(context, DAILY_COINS)
        // Jadwalkan pengingat untuk claim berikutnya (siapa pun pemanggil claim-nya)
        CoinNotificationWorker.schedule(context)
        return true
    }

}

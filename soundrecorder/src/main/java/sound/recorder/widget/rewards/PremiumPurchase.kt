package sound.recorder.widget.rewards

import android.content.Context
import sound.recorder.widget.notification.CoinNotificationWorker

/**
 * Status pembelian premium permanen (Remove Ads) yang perlu diketahui SDK.
 * Status pembelian disimpan oleh host app, jadi tanpa ini SDK tidak tahu user sudah membeli.
 *
 * Host app memanggil [setPurchased] setiap kali pembelian terbaca (dari prefs saat app dibuka,
 * dari callback BillingManager, atau dari checkHistory).
 *
 * Saat ini dipakai untuk mematikan notif klaim koin ([CoinNotificationWorker]) bagi pembeli.
 * Notif pengingat buka app ([sound.recorder.widget.notification.AppReminderWorker]) tetap jalan.
 * Premium Pass (tukar koin, sementara) tidak dihitung di sini.
 */
object PremiumPurchase {
    private const val PREFS = "zaif_premium_purchase"
    private const val KEY_PURCHASED = "purchased"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @JvmStatic
    fun isPurchased(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PURCHASED, false)

    /** Simpan status beli. Kalau true, jadwal notif klaim koin langsung dibatalkan. */
    @JvmStatic
    fun setPurchased(context: Context, purchased: Boolean) {
        if (isPurchased(context) != purchased) {
            prefs(context).edit().putBoolean(KEY_PURCHASED, purchased).apply()
        }
        if (purchased) CoinNotificationWorker.cancel(context)
    }
}

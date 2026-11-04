package sound.recorder.widget.rewards

import android.content.Context
import sound.recorder.widget.R
import sound.recorder.widget.builder.ZaifSDKBuilder
import sound.recorder.widget.recording.export.RecordingExporter
import sound.recorder.widget.tutorial.TutorialVideoQuota
import sound.recorder.widget.util.CoinManager

/** Satu baris "Koin untuk apa?": [price] sudah berupa teks, mis. "10" atau "30–50". */
data class CoinUse(val icon: String, val label: String, val price: String)

/**
 * Daftar kegunaan koin untuk panel Misi, sama untuk semua app: dialog Misi SDK ([RewardsDialog]) dan
 * panel Misi buatan host (mis. Belira, Compose) memakai daftar ini supaya isi dan harganya seragam.
 * Harga dibaca langsung dari pengaturan SDK, jadi ikut berubah bila host mengubahnya.
 */
object CoinUses {

    @JvmStatic
    fun list(context: Context): List<CoinUse> {
        val config = runCatching { ZaifSDKBuilder.load(context) }.getOrNull()
        val items = mutableListOf<CoinUse>()
        items += CoinUse("🎵", context.getString(R.string.coin_use_unlock_song), CoinManager.UNLOCK_COST.toString())
        if (RecordingExporter.exportCoinCost > 0) {
            items += CoinUse("🎧", context.getString(R.string.coin_use_export), RecordingExporter.exportCoinCost.toString())
        }
        // Video tanpa watermark dengan koin hanya ada di app tanpa pembelian yang video-nya aktif.
        if (config?.isVideo == true && config.noPremiumPurchase) {
            val costs = listOf(TutorialVideoQuota.removeWatermarkCoinCost, TutorialVideoQuota.noWatermarkCoinCost)
                .filter { it > 0 }.distinct().sorted()
            if (costs.isNotEmpty()) {
                items += CoinUse("🎬", context.getString(R.string.coin_use_video), costs.joinToString("–"))
            }
        }
        return items
    }
}

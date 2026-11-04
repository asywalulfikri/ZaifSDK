package sound.recorder.widget.tutorial

import sound.recorder.widget.util.ZaifAnalytics
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import sound.recorder.widget.R
import sound.recorder.widget.builder.ZaifSDKBuilder
import sound.recorder.widget.payment.BillingManager
import sound.recorder.widget.recording.export.RecordingExporter
import sound.recorder.widget.util.CoinManager
import sound.recorder.widget.recording.RecordingDialogPalette
import sound.recorder.widget.recording.RecordingDialogStyle
import sound.recorder.widget.recording.RecordingDialogViews.applyWindow
import sound.recorder.widget.recording.RecordingDialogViews.boxButton
import sound.recorder.widget.recording.RecordingDialogViews.coinButton
import sound.recorder.widget.recording.RecordingDialogViews.rounded
import sound.recorder.widget.recording.RecordingDialogViews.sdp
import sound.recorder.widget.recording.RecordingDialogViews.textButton
import sound.recorder.widget.recording.RecordingDialogViews.vSpace
import com.intuit.sdp.R as SdpR

/**
 * Tampil saat jatah video gratis hari ini habis.
 *
 * Tombol beli hanya muncul bila host mengisi [onPremiumPurchased], karena host yang menyimpan
 * status premium dan mematikan iklan. Pembelian memakai produk remove ads yang sama.
 *
 * App yang menyatakan tidak punya pembelian (`ZaifSDKBuilder.hasPremiumPurchase(false)`) mendapat
 * pilihan tonton iklan ([onRequestReward]) dan bayar koin (bila isCoin). Keduanya membuka satu video
 * versi gratis (dengan watermark) lewat [onUnlocked]. Bayar [TutorialVideoQuota.noWatermarkCoinCost]
 * koin (default 50) membuka satu video TANPA watermark lewat [onUnlockedNoWatermark]. App lain tanpa tombol beli hanya melihat
 * "coba lagi besok", supaya lupa mengisi sesuatu tidak membuka iklan/koin tanpa sengaja.
 */
internal object TutorialVideoPremiumDialog {

    fun show(
        context: Context,
        onPremiumPurchased: (() -> Unit)?,
        onRequestReward: ((onRewarded: () -> Unit) -> Unit)? = null,
        /** Dibuka dengan iklan (0) atau koin; parameter = koin yang dibayar, untuk refund bila batal. */
        onUnlocked: (paidCoins: Int) -> Unit = {},
        /** Dibayar [TutorialVideoQuota.noWatermarkCoinCost] koin: satu video tanpa watermark. */
        onUnlockedNoWatermark: (paidCoins: Int) -> Unit = onUnlocked,
        onSuccess: () -> Unit
    ) {
        val c = RecordingDialogPalette(context, RecordingDialogStyle.default)
        val dialog = AlertDialog.Builder(context).create()
        val activity = findActivity(context)
        var finished = false

        // Dibuat saat dialog dibuka supaya koneksi Play Store siap sebelum tombol ditekan.
        // Pembelian lama yang ditemukan di sini ikut memulihkan status premium.
        // App tanpa pembelian tidak membuka koneksi Play Billing sama sekali.
        val noPurchaseApp = ZaifSDKBuilder.load(context)?.noPremiumPurchase == true
        var billing: BillingManager? = null
        if (activity != null && onPremiumPurchased != null && !noPurchaseApp) {
            billing = BillingManager(activity) {
                activity.runOnUiThread {
                    if (finished) return@runOnUiThread
                    finished = true
                    onPremiumPurchased()
                    dialog.dismiss()
                    Toast.makeText(context, R.string.tutorial_video_premium_success, Toast.LENGTH_LONG).show()
                    onSuccess()
                }
            }
        }
        dialog.setOnDismissListener { billing?.destroy() }

        val pad = context.sdp(SdpR.dimen._16sdp)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(context, c.surface, SdpR.dimen._12sdp)
            setPadding(pad, pad, pad, context.sdp(SdpR.dimen._8sdp))
        }
        // Iklan / koin HANYA untuk app yang menyatakan tidak punya pembelian:
        // ZaifSDKBuilder.hasPremiumPurchase(false). App lain tetap dialog Premium.
        if (noPurchaseApp || onPremiumPurchased == null) {
            showUnlockContent(context, c, dialog, root, onRequestReward, onUnlocked, onUnlockedNoWatermark, allowUnlock = noPurchaseApp)
            return
        }

        root.addView(TextView(context).apply {
            text = "👑 " + context.getString(R.string.tutorial_video_premium_title)
            setTextColor(c.text)
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
        })
        root.addView(vSpace(context, SdpR.dimen._6sdp))
        root.addView(TextView(context).apply {
            text = context.getString(R.string.tutorial_video_premium_message)
            setTextColor(c.textSecondary)
            textSize = 13f
            setLineSpacing(0f, 1.2f)
        })
        root.addView(vSpace(context, SdpR.dimen._8sdp))
        listOf(
            R.string.tutorial_video_premium_benefit_unlimited,
            R.string.tutorial_video_premium_benefit_watermark,
            R.string.tutorial_video_premium_benefit_ads
        ).forEach { benefit ->
            root.addView(TextView(context).apply {
                text = "✓  " + context.getString(benefit)
                setTextColor(c.text)
                textSize = 13f
                setPadding(0, context.sdp(SdpR.dimen._2sdp), 0, context.sdp(SdpR.dimen._2sdp))
            })
        }
        root.addView(vSpace(context, SdpR.dimen._10sdp))

        root.addView(LinearLayout(context).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            addView(textButton(context, c, context.getString(R.string.tutorial_video_premium_later)).apply {
                setTextColor(c.textSecondary)
                setOnClickListener { dialog.dismiss() }
            })
            billing?.let { manager ->
                addView(View(context), LinearLayout.LayoutParams(context.sdp(SdpR.dimen._8sdp), 1))
                addView(boxButton(context, c, context.getString(R.string.tutorial_video_premium_buy), primary = true) {
                    ZaifAnalytics.premiumOfferClicked(context, ZaifAnalytics.PLACE_VIDEO)
                    manager.makePurchase {
                        activity?.runOnUiThread {
                            Toast.makeText(context, R.string.tutorial_video_premium_error, Toast.LENGTH_LONG).show()
                        }
                    }
                }, LinearLayout.LayoutParams(context.sdp(SdpR.dimen._120sdp), context.sdp(SdpR.dimen._34sdp)))
            }
        }, LinearLayout.LayoutParams(-1, -2))

        dialog.setCanceledOnTouchOutside(false)
        dialog.setView(root)
        dialog.show()
        applyWindow(dialog, context, 0.88, 0.50)
        ZaifAnalytics.premiumOfferShown(context, ZaifAnalytics.PLACE_VIDEO)
    }

    /** Isi dialog untuk app tanpa pembelian: tonton iklan / bayar koin, atau coba lagi besok. */
    private fun showUnlockContent(
        context: Context,
        c: RecordingDialogPalette,
        dialog: AlertDialog,
        root: LinearLayout,
        onRequestReward: ((onRewarded: () -> Unit) -> Unit)?,
        onUnlocked: (paidCoins: Int) -> Unit,
        onUnlockedNoWatermark: (paidCoins: Int) -> Unit,
        /** False: app punya pembelian tapi tidak memasang tombol beli → hanya "coba lagi besok". */
        allowUnlock: Boolean
    ) {
        val coinEnabled = allowUnlock && ZaifSDKBuilder.load(context)?.isCoin == true
        val adRequest = onRequestReward.takeIf { allowUnlock }
        val canUnlock = adRequest != null || coinEnabled

        root.addView(TextView(context).apply {
            text = "🎬 " + context.getString(R.string.tutorial_video_limit_title)
            setTextColor(c.text)
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
        })
        root.addView(vSpace(context, SdpR.dimen._6sdp))
        root.addView(TextView(context).apply {
            text = context.getString(
                if (canUnlock) R.string.tutorial_video_limit_message else R.string.tutorial_video_limit_tomorrow
            )
            setTextColor(c.textSecondary)
            textSize = 13f
            setLineSpacing(0f, 1.2f)
        })
        root.addView(vSpace(context, SdpR.dimen._10sdp))

        if (coinEnabled) {
            val balance = CoinManager.getBalance(context)
            // Bayar koin: video biasa (watermark), atau lebih mahal tanpa watermark
            coinRow(context, c, dialog, root, balance,
                label = "${CoinManager.icon} " + context.getString(R.string.your_coin) + ": $balance",
                button = context.getString(R.string.export_pay_coins, RecordingExporter.exportCoinCost),
                cost = RecordingExporter.exportCoinCost, onPaid = onUnlocked)
            val noWmCost = TutorialVideoQuota.noWatermarkCoinCost
            if (noWmCost > 0) {
                coinRow(context, c, dialog, root, balance,
                    label = context.getString(R.string.tutorial_video_no_watermark),
                    button = context.getString(R.string.export_pay_coins, noWmCost),
                    cost = noWmCost, onPaid = onUnlockedNoWatermark)
            }
        }

        root.addView(LinearLayout(context).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            addView(textButton(context, c, context.getString(R.string.tutorial_video_premium_later)).apply {
                setTextColor(c.textSecondary)
                setOnClickListener { dialog.dismiss() }
            })
            adRequest?.let { request ->
                addView(View(context), LinearLayout.LayoutParams(context.sdp(SdpR.dimen._8sdp), 1))
                addView(boxButton(context, c, "▶  " + context.getString(R.string.export_reward_watch), primary = true) {
                    dialog.dismiss()
                    request { onUnlocked(0) }
                }, LinearLayout.LayoutParams(context.sdp(SdpR.dimen._120sdp), context.sdp(SdpR.dimen._34sdp)))
            }
        }, LinearLayout.LayoutParams(-1, -2))

        dialog.setCanceledOnTouchOutside(false)
        dialog.setView(root)
        dialog.show()
        applyWindow(dialog, context, 0.88, 0.50)
    }

    private fun coinRow(
        context: Context,
        c: RecordingDialogPalette,
        dialog: AlertDialog,
        root: LinearLayout,
        balance: Int,
        label: String,
        button: String,
        cost: Int,
        onPaid: (paidCoins: Int) -> Unit
    ) {
        root.addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(context).apply {
                text = label
                setTextColor(c.textSecondary)
                textSize = 12f
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(coinButton(context, c, button, balance >= cost) {
                if (CoinManager.spendCoins(context, cost, ZaifAnalytics.PURPOSE_EXPORT)) {
                    dialog.dismiss()
                    onPaid(cost)
                } else {
                    Toast.makeText(context, context.getString(R.string.coin_not_enough), Toast.LENGTH_SHORT).show()
                }
            })
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = context.sdp(SdpR.dimen._6sdp) })
    }

    private tailrec fun findActivity(context: Context?): Activity? = when (context) {
        is Activity -> context
        is ContextWrapper -> findActivity(context.baseContext)
        else -> null
    }
}

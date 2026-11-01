package sound.recorder.widget.tutorial

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
import sound.recorder.widget.payment.BillingManager
import sound.recorder.widget.recording.RecordingDialogPalette
import sound.recorder.widget.recording.RecordingDialogStyle
import sound.recorder.widget.recording.RecordingDialogViews.applyWindow
import sound.recorder.widget.recording.RecordingDialogViews.boxButton
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
 */
internal object TutorialVideoPremiumDialog {

    fun show(context: Context, onPremiumPurchased: (() -> Unit)?, onSuccess: () -> Unit) {
        val c = RecordingDialogPalette(context, RecordingDialogStyle.default)
        val dialog = AlertDialog.Builder(context).create()
        val activity = findActivity(context)
        var finished = false

        // Dibuat saat dialog dibuka supaya koneksi Play Store siap sebelum tombol ditekan.
        // Pembelian lama yang ditemukan di sini ikut memulihkan status premium.
        var billing: BillingManager? = null
        if (activity != null && onPremiumPurchased != null) {
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
    }

    private tailrec fun findActivity(context: Context?): Activity? = when (context) {
        is Activity -> context
        is ContextWrapper -> findActivity(context.baseContext)
        else -> null
    }
}

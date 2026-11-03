package sound.recorder.widget.tutorial

import android.animation.ObjectAnimator
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import sound.recorder.widget.R
import sound.recorder.widget.recording.RecordingDialogViews.circle
import sound.recorder.widget.recording.RecordingDialogViews.sdp
import java.util.Locale
import com.intuit.sdp.R as SdpR

/**
 * Tanda "REC 00:12 / 01:30" selama video direkam.
 *
 * Dipasang di root layar, di luar view yang direkam, jadi tidak ikut masuk video. Posisinya
 * mengikuti [anchor] agar tidak menutupi banner iklan, dan tidak menerima sentuhan.
 */
internal class VideoRecordingIndicator(
    private val anchor: View,
    private val durationMs: Long,
    /** App ditinggalkan saat merekam: gambar berhenti, jadi video harus dibatalkan. */
    private val onInterrupted: () -> Unit
) {

    /** Video seluruh layar: indikator tidak boleh ikut terekam. Diisi sebelum [show]. */
    var hiddenWhileRecording: Boolean = false

    private val handler = Handler(Looper.getMainLooper())
    private var pill: LinearLayout? = null
    private var dot: View? = null
    private var label: TextView? = null
    private var blink: ObjectAnimator? = null
    private var startedAt = 0L
    private var lifecycleOwner: LifecycleOwner? = null
    private var keptScreenOn = false

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) = onInterrupted()
    }

    private fun stopWatching() {
        lifecycleOwner?.lifecycle?.removeObserver(lifecycleObserver)
        lifecycleOwner = null
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!anchor.isAttachedToWindow) {
                dismiss()
                return
            }
            val elapsed = (SystemClock.elapsedRealtime() - startedAt).coerceAtMost(durationMs)
            label?.text = anchor.context.getString(R.string.tutorial_video_rec_timer, format(elapsed), format(durationMs))
            handler.postDelayed(this, TICK_MS)
        }
    }

    fun show() {
        val context = anchor.context
        val root = anchor.rootView.findViewById<ViewGroup>(android.R.id.content) ?: return
        val padH = context.sdp(SdpR.dimen._10sdp)
        val padV = context.sdp(SdpR.dimen._4sdp)
        val dotView = View(context).apply { background = circle(Color.parseColor("#FF3B30")) }
        val text = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(padH, padV, padH, padV)
            background = GradientDrawable().apply {
                setColor(Color.argb(200, 0, 0, 0))
                cornerRadius = context.resources.getDimension(SdpR.dimen._14sdp)
            }
            elevation = context.resources.getDimension(SdpR.dimen._6sdp)
            isClickable = false
            if (hiddenWhileRecording) visibility = View.INVISIBLE
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            val dotSize = context.sdp(SdpR.dimen._8sdp)
            addView(dotView, LinearLayout.LayoutParams(dotSize, dotSize).apply {
                marginEnd = context.sdp(SdpR.dimen._6sdp)
            })
            addView(text)
        }

        val rootPos = IntArray(2).also { root.getLocationInWindow(it) }
        val anchorPos = IntArray(2).also { anchor.getLocationInWindow(it) }
        root.addView(container, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = anchorPos[1] - rootPos[1] + context.sdp(SdpR.dimen._6sdp)
        })

        pill = container
        dot = dotView
        label = text
        blink = ObjectAnimator.ofFloat(dotView, View.ALPHA, 1f, 0.15f).apply {
            duration = BLINK_MS
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            start()
        }
        startedAt = SystemClock.elapsedRealtime()
        // Lagu bisa lebih lama dari batas layar mati, dan user tidak menyentuh layar selama merekam.
        keptScreenOn = anchor.keepScreenOn
        anchor.keepScreenOn = true
        lifecycleOwner = anchor.findViewTreeLifecycleOwner()?.also { it.lifecycle.addObserver(lifecycleObserver) }
        tick.run()
    }

    /** Rekaman selesai, file sedang diproses. */
    fun showSaving() {
        stopWatching()
        handler.removeCallbacks(tick)
        blink?.cancel()
        dot?.visibility = View.GONE
        pill?.visibility = View.VISIBLE
        label?.text = anchor.context.getString(R.string.tutorial_video_saving)
    }

    fun dismiss() {
        stopWatching()
        if (pill != null) anchor.keepScreenOn = keptScreenOn
        handler.removeCallbacks(tick)
        blink?.cancel()
        blink = null
        pill?.let { (it.parent as? ViewGroup)?.removeView(it) }
        pill = null
        dot = null
        label = null
    }

    private fun format(ms: Long): String {
        val seconds = ms / 1000
        return String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60)
    }

    private companion object {
        const val TICK_MS = 500L
        const val BLINK_MS = 500L
    }
}

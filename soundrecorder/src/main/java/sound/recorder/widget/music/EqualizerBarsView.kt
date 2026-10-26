package sound.recorder.widget.music

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.os.SystemClock
import android.view.View
import kotlin.math.sin

/**
 * Bar equalizer kecil untuk dialog volume — murni dekorasi.
 * Tinggi bar mengikuti [level] (0..1). Bar hanya bergoyang sebentar setelah [kick] dipanggil
 * (mis. saat slider digeser), lalu mereda dan diam — atau terus bergoyang selama [continuous] true
 * (mis. lagu sedang diputar). Saat bisu, bar rata dan memakai [mutedColor].
 */
internal class EqualizerBarsView(context: Context) : View(context) {

    var barColor: Int = Color.BLACK
    var mutedColor: Int = Color.GRAY

    var level: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val phases = floatArrayOf(0f, 1.7f, 3.1f, 0.9f, 2.4f)
    private val speeds = floatArrayOf(5.2f, 7.1f, 6.3f, 8.4f, 5.8f)
    private var activeUntil = 0L

    var continuous: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            // Saat berhenti, biarkan mereda halus seperti setelah kick
            if (!value) activeUntil = SystemClock.uptimeMillis() + FADE_MS.toLong()
            invalidate()
        }

    /** Mulai / perpanjang goyangan; mereda sendiri setelah [LINGER_MS] tanpa kick baru. */
    fun kick() {
        activeUntil = SystemClock.uptimeMillis() + LINGER_MS
        invalidate()
    }

    // Hormati setelan "hapus animasi" di Developer options / aksesibilitas
    private fun animationsEnabled() =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val count = phases.size
        val gap = w / (count * 2.6f)
        val barW = (w - gap * (count - 1)) / count
        val minH = barW
        val muted = level <= 0f
        val now = SystemClock.uptimeMillis()
        val animate = !muted && animationsEnabled() && (continuous || now < activeUntil)
        // Amplitudo mengecil di akhir supaya berhenti halus, bukan loncat ke posisi diam
        val blend = if (continuous) 1f else ((activeUntil - now).toFloat() / FADE_MS).coerceIn(0f, 1f)
        val t = now / 1000f

        paint.color = if (muted) mutedColor else barColor
        for (i in 0 until count) {
            val wave = 0.5f + 0.5f * sin(t * speeds[i] + phases[i])
            val wobble = if (animate) IDLE + (wave - IDLE) * blend else IDLE
            val barH = if (muted) minH else minH + (h - minH) * level * (0.4f + 0.6f * wobble)
            val left = i * (barW + gap)
            rect.set(left, h - barH, left + barW, h)
            canvas.drawRoundRect(rect, barW / 2f, barW / 2f, paint)
        }

        // Loop berhenti sendiri setelah reda, saat bisu, atau saat view tidak lagi digambar
        if (animate) postInvalidateOnAnimation()
    }

    private companion object {
        const val LINGER_MS = 700L
        const val FADE_MS = 300f
        const val IDLE = 0.75f
    }
}

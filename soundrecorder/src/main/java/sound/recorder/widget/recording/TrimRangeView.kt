package sound.recorder.widget.recording

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.ColorUtils
import kotlin.math.abs

/**
 * Penggeser rentang dua pegangan untuk memotong rekaman.
 * Menampilkan posisi ketukan (garis kecil) dan bagian lagu (pita) agar mudah memilih bagian yang disimpan.
 * Tanpa Material Slider: aman di tema app host apa pun.
 */
@SuppressLint("ViewConstructor")
internal class TrimRangeView(
    context: Context,
    private val durationMs: Long,
    private val tapTimes: List<Long>,
    private val musicRanges: List<LongRange>,
    private val minLengthMs: Long,
    private val accent: Int,
    private val trackColor: Int,
    private val markerColor: Int
) : View(context) {

    var startMs = 0L
        private set
    var endMs = durationMs
        private set
    var onRangeChanged: ((startMs: Long, endMs: Long) -> Unit)? = null

    /** Posisi pratinjau yang sedang diputar (garis putih). -1 = tidak ditampilkan. */
    var playheadMs = -1L
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val thumbRadius = 11f * density
    private val trackHeight = 28f * density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var dragging = 0 // 0 = tidak, 1 = awal, 2 = akhir

    init {
        minimumHeight = (trackHeight + thumbRadius * 2 + 8 * density).toInt()
    }

    private val left get() = paddingLeft + thumbRadius
    private val right get() = width - paddingRight - thumbRadius
    private fun xOf(ms: Long) = left + (right - left) * (ms.toFloat() / durationMs.coerceAtLeast(1))
    private fun msOf(x: Float) = (((x - left) / (right - left)).coerceIn(0f, 1f) * durationMs).toLong()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            getDefaultSize(suggestedMinimumWidth, widthMeasureSpec),
            resolveSize(minimumHeight, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        val cy = height / 2f
        val top = cy - trackHeight / 2
        val bottom = cy + trackHeight / 2
        val r = 6f * density

        // Lintasan penuh
        paint.color = trackColor
        rect.set(left, top, right, bottom)
        canvas.drawRoundRect(rect, r, r, paint)

        // Bagian lagu (pita di bawah)
        paint.color = ColorUtils.setAlphaComponent(accent, 0x55)
        musicRanges.forEach { m ->
            rect.set(xOf(m.first), bottom - 4 * density, xOf(m.last), bottom)
            canvas.drawRect(rect, paint)
        }

        // Ketukan
        paint.color = markerColor
        paint.strokeWidth = 1.5f * density
        tapTimes.forEach { t ->
            val x = xOf(t)
            canvas.drawLine(x, top + 6 * density, x, bottom - 6 * density, paint)
        }

        // Bagian yang disimpan
        val sx = xOf(startMs)
        val ex = xOf(endMs)
        paint.color = ColorUtils.setAlphaComponent(accent, 0x40)
        rect.set(sx, top, ex, bottom)
        canvas.drawRect(rect, paint)
        paint.color = accent
        paint.strokeWidth = 2f * density
        canvas.drawLine(sx, top, ex, top, paint)
        canvas.drawLine(sx, bottom, ex, bottom, paint)

        // Posisi pratinjau
        if (playheadMs in 0..durationMs) {
            val px = xOf(playheadMs)
            paint.color = 0xFFFFFFFF.toInt()
            paint.strokeWidth = 2.5f * density
            canvas.drawLine(px, top - 4 * density, px, bottom + 4 * density, paint)
        }

        // Pegangan
        listOf(sx, ex).forEach { x ->
            paint.color = accent
            canvas.drawRect(x - 1.5f * density, top, x + 1.5f * density, bottom, paint)
            canvas.drawCircle(x, cy, thumbRadius, paint)
            paint.color = 0xFFFFFFFF.toInt()
            canvas.drawCircle(x, cy, thumbRadius * 0.4f, paint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Pegangan terdekat yang digeser
                dragging = if (abs(e.x - xOf(startMs)) <= abs(e.x - xOf(endMs))) 1 else 2
                parent?.requestDisallowInterceptTouchEvent(true)
                moveTo(e.x)
            }
            MotionEvent.ACTION_MOVE -> if (dragging != 0) moveTo(e.x)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = 0
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    private fun moveTo(x: Float) {
        val ms = msOf(x)
        if (dragging == 1) startMs = ms.coerceIn(0L, (endMs - minLengthMs).coerceAtLeast(0L))
        else endMs = ms.coerceIn((startMs + minLengthMs).coerceAtMost(durationMs), durationMs)
        invalidate()
        onRangeChanged?.invoke(startMs, endMs)
    }
}

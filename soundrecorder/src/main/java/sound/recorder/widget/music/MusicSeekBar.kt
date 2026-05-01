package sound.recorder.widget.music


import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.sin

class MusicSeekBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    interface OnProgressChangeListener {
        fun onProgressChanged(progress: Int, fromUser: Boolean)
        fun onStartTrackingTouch()
        fun onStopTrackingTouch()
    }

    var listener: OnProgressChangeListener? = null

    var max: Int = 100
        set(value) { field = value; invalidate() }

    var progress: Int = 0
        set(value) { field = value.coerceIn(0, max); invalidate() }

    // ─── STYLE ───
    var trackColor: Int         = Color.parseColor("#1E2340")
    var progressStartColor: Int = Color.parseColor("#00C9FF")
    var progressEndColor: Int   = Color.parseColor("#92FE9D")
    var glowColor: Int          = Color.parseColor("#00C9FF")
    var thumbColor: Int         = Color.WHITE
    var thumbBorderColor: Int   = Color.parseColor("#00C9FF")
    var showGlow: Boolean       = true
    var showThumb: Boolean      = true

    private var isDragging = false
    private val paint      = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint  = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        val thumbR     = if (showThumb) h * 0.38f else 0f
        val trackLeft  = thumbR + 4f
        val trackRight = w - thumbR - 4f
        val trackW     = trackRight - trackLeft

        val progressFraction = if (max > 0) progress.toFloat() / max else 0f
        val thumbX = trackLeft + trackW * progressFraction

        // ─── 1. Track background ───
        val trackH   = h * 0.18f
        val trackTop = h / 2f - trackH / 2f
        val trackBot = h / 2f + trackH / 2f

        paint.apply {
            shader = null
            color  = trackColor
            alpha  = 255
            style  = Paint.Style.FILL
            clearShadowLayer()
        }
        canvas.drawRoundRect(trackLeft, trackTop, trackRight, trackBot, trackH / 2f, trackH / 2f, paint)

        // ─── 2. Progress fill ───
        if (thumbX > trackLeft) {
            paint.shader = LinearGradient(
                trackLeft, 0f, thumbX, 0f,
                intArrayOf(progressStartColor, progressEndColor),
                null,
                Shader.TileMode.CLAMP
            )
            paint.alpha = 255
            if (showGlow) paint.setShadowLayer(trackH * 2f, 0f, 0f, glowColor)
            canvas.drawRoundRect(trackLeft, trackTop, thumbX, trackBot, trackH / 2f, trackH / 2f, paint)
            paint.clearShadowLayer()
        }

        // ─── 3. Time markers (tick marks) ───
        val tickCount = 10
        for (i in 1 until tickCount) {
            val tickX     = trackLeft + trackW * (i.toFloat() / tickCount)
            val isActive  = tickX <= thumbX
            val tickH     = if (i % 5 == 0) h * 0.25f else h * 0.15f
            val tickTop   = h / 2f - tickH / 2f
            val tickBot   = h / 2f + tickH / 2f

            paint.apply {
                shader = null
                color  = if (isActive) progressEndColor else Color.WHITE
                alpha  = if (isActive) 180 else 40
                style  = Paint.Style.FILL
                clearShadowLayer()
            }
            canvas.drawRoundRect(tickX - 1.5f, tickTop, tickX + 1.5f, tickBot, 1.5f, 1.5f, paint)
        }

        // ─── 4. Elapsed / remaining time label ───
        val elapsedMs   = (progress.toLong())
        val remainingMs = (max - progress).toLong()
        val elapsedStr  = formatMs(elapsedMs)
        val remainStr   = "-${formatMs(remainingMs)}"

        textPaint.apply {
            color    = Color.WHITE
            alpha    = 160
            textSize = h * 0.22f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        canvas.drawText(elapsedStr, trackLeft, h - 2f, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(remainStr, trackRight, h - 2f, textPaint)
        textPaint.textAlign = Paint.Align.LEFT

        // ─── 5. Thumb ───
        if (showThumb) {
            // Glow layers
            if (showGlow) {
                for (layer in 3 downTo 1) {
                    thumbPaint.apply {
                        color      = glowColor
                        alpha      = 20 * layer
                        style      = Paint.Style.FILL
                        maskFilter = BlurMaskFilter(thumbR * layer * 0.7f, BlurMaskFilter.Blur.NORMAL)
                    }
                    canvas.drawCircle(thumbX, h / 2f, thumbR, thumbPaint)
                }
            }

            // Shadow
            thumbPaint.apply {
                color      = Color.BLACK
                alpha      = 80
                style      = Paint.Style.FILL
                maskFilter = BlurMaskFilter(thumbR * 0.5f, BlurMaskFilter.Blur.NORMAL)
            }
            canvas.drawCircle(thumbX + 1.5f, h / 2f + 2f, thumbR, thumbPaint)

            // Main fill
            thumbPaint.apply {
                color      = thumbColor
                alpha      = 255
                maskFilter = null
                style      = Paint.Style.FILL
            }
            canvas.drawCircle(thumbX, h / 2f, thumbR, thumbPaint)

            // Gradient overlay
            thumbPaint.shader = RadialGradient(
                thumbX - thumbR * 0.3f,
                h / 2f - thumbR * 0.3f,
                thumbR * 1.2f,
                intArrayOf(Color.WHITE, Color.parseColor("#D0F0FF")),
                null,
                Shader.TileMode.CLAMP
            )
            thumbPaint.alpha = 180
            canvas.drawCircle(thumbX, h / 2f, thumbR, thumbPaint)
            thumbPaint.shader = null

            // Outer ring
            thumbPaint.apply {
                color       = thumbBorderColor
                alpha       = 255
                style       = Paint.Style.STROKE
                strokeWidth = thumbR * 0.18f
            }
            canvas.drawCircle(thumbX, h / 2f, thumbR, thumbPaint)

            // Inner ring
            thumbPaint.apply {
                color       = progressEndColor
                alpha       = 150
                style       = Paint.Style.STROKE
                strokeWidth = thumbR * 0.1f
            }
            canvas.drawCircle(thumbX, h / 2f, thumbR * 0.58f, thumbPaint)

            // Center dot
            thumbPaint.apply {
                color  = thumbBorderColor
                alpha  = 255
                style  = Paint.Style.FILL
            }
            canvas.drawCircle(thumbX, h / 2f, thumbR * 0.2f, thumbPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = true
                parent?.requestDisallowInterceptTouchEvent(true)
                updateProgress(event.x)
                listener?.onStartTrackingTouch()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) updateProgress(event.x)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
                listener?.onStopTrackingTouch()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateProgress(touchX: Float) {
        val thumbR     = if (showThumb) height * 0.38f else 0f
        val trackLeft  = thumbR + 4f
        val trackRight = width - thumbR - 4f
        val clamped    = touchX.coerceIn(trackLeft, trackRight)
        val fraction   = (clamped - trackLeft) / (trackRight - trackLeft)
        val newProgress = (fraction * max).toInt().coerceIn(0, max)
        if (newProgress != progress) {
            progress = newProgress
            listener?.onProgressChanged(newProgress, true)
        }
    }

    private fun formatMs(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return "%d:%02d".format(min, sec)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredH = (resources.displayMetrics.density * 52).toInt()
        val h = resolveSize(desiredH, heightMeasureSpec)
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
    }
}
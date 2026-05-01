package sound.recorder.widget.music

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.sin

class DJSeekBar @JvmOverloads constructor(
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
    var trackColor: Int          = Color.parseColor("#1E2340")
    var progressStartColor: Int  = Color.parseColor("#6C63FF")
    var progressEndColor: Int    = Color.parseColor("#A78BFA")
    var glowColor: Int           = Color.parseColor("#6C63FF")
    var thumbColor: Int          = Color.WHITE
    var thumbBorderColor: Int    = Color.parseColor("#6C63FF")
    var showGlow: Boolean        = true
    var showThumb: Boolean       = true
    var barCount: Int            = 32

    private var isDragging = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // ─── Generate random-ish waveform heights ───
    private val waveHeights: FloatArray by lazy {
        FloatArray(barCount) { i ->
            val base = sin(i * 0.8).toFloat()
            val mid  = sin(i * 0.3 + 1.2).toFloat()
            val fine = sin(i * 1.7 + 0.5).toFloat()
            val raw  = (base * 0.5f + mid * 0.3f + fine * 0.2f)
            0.25f + abs(raw) * 0.75f
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        val thumbR      = if (showThumb) h * 0.42f else 0f
        val trackLeft   = thumbR
        val trackRight  = w - thumbR
        val trackW      = trackRight - trackLeft

        val gap      = trackW * 0.012f
        val barWidth = (trackW - gap * (barCount - 1)) / barCount

        val progressFraction = if (max > 0) progress.toFloat() / max else 0f
        val thumbX = trackLeft + trackW * progressFraction

        // ─── 1. Track background pill ───
        paint.apply {
            shader = null
            color  = trackColor
            alpha  = 255
            clearShadowLayer()
            style  = Paint.Style.FILL
        }
        val pillH      = h * 0.22f
        val pillTop    = h / 2f - pillH / 2f
        val pillBottom = h / 2f + pillH / 2f
        canvas.drawRoundRect(
            trackLeft, pillTop, trackRight, pillBottom,
            pillH / 2f, pillH / 2f, paint
        )

        // ─── 2. Waveform bars ───
        for (i in 0 until barCount) {
            val left   = trackLeft + i * (barWidth + gap)
            val right  = left + barWidth
            val centerX = (left + right) / 2f

            val barH   = h * 0.3f + h * 0.6f * waveHeights[i]
            val top    = h / 2f - barH / 2f
            val bottom = h / 2f + barH / 2f

            val isActive = centerX <= thumbX

            if (isActive) {
                // Gradient aktif
                paint.shader = LinearGradient(
                    left, bottom, left, top,
                    intArrayOf(
                        progressStartColor,
                        progressEndColor,
                        Color.WHITE
                    ),
                    floatArrayOf(0f, 0.7f, 1f),
                    Shader.TileMode.CLAMP
                )
                paint.alpha = 255

                if (showGlow) {
                    paint.setShadowLayer(barWidth * 1.5f, 0f, 0f, glowColor)
                }
            } else {
                paint.shader = null
                paint.clearShadowLayer()

                // Bar inactive: sedikit lebih terang di tengah
                val distFromThumb = (centerX - thumbX) / trackW
                val alphaVal = (40 + 40 * (1f - distFromThumb.coerceIn(0f, 1f))).toInt()
                paint.color = trackColor
                paint.alpha = alphaVal
            }

            canvas.drawRoundRect(
                left, top, right, bottom,
                barWidth / 2f, barWidth / 2f,
                paint
            )
        }

        // ─── 3. Progress line (tipis, di bawah bar) ───
        paint.apply {
            shader = LinearGradient(
                trackLeft, 0f, thumbX, 0f,
                intArrayOf(progressStartColor, progressEndColor),
                null,
                Shader.TileMode.CLAMP
            )
            alpha = 180
            clearShadowLayer()
        }
        canvas.drawRoundRect(
            trackLeft, pillTop, thumbX, pillBottom,
            pillH / 2f, pillH / 2f, paint
        )

        // ─── 4. Thumb ───
        if (showThumb) {
            // Outer glow
            if (showGlow) {
                for (layer in 3 downTo 1) {
                    thumbPaint.apply {
                        color    = glowColor
                        alpha    = 25 * layer
                        style    = Paint.Style.FILL
                        maskFilter = BlurMaskFilter(thumbR * layer * 0.6f, BlurMaskFilter.Blur.NORMAL)
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

            // Main circle
            thumbPaint.apply {
                color      = thumbColor
                alpha      = 255
                maskFilter = null
                style      = Paint.Style.FILL
            }
            canvas.drawCircle(thumbX, h / 2f, thumbR, thumbPaint)

            // Gradient overlay di thumb
            thumbPaint.shader = RadialGradient(
                thumbX - thumbR * 0.3f, h / 2f - thumbR * 0.3f,
                thumbR * 1.2f,
                intArrayOf(Color.WHITE, Color.parseColor("#E0E0FF")),
                null,
                Shader.TileMode.CLAMP
            )
            thumbPaint.alpha = 200
            canvas.drawCircle(thumbX, h / 2f, thumbR, thumbPaint)
            thumbPaint.shader = null

            // Border ring
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
                alpha       = 120
                style       = Paint.Style.STROKE
                strokeWidth = thumbR * 0.1f
            }
            canvas.drawCircle(thumbX, h / 2f, thumbR * 0.6f, thumbPaint)

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
        val thumbR   = if (showThumb) height * 0.42f else 0f
        val trackLeft  = thumbR
        val trackRight = width - thumbR
        val clamped    = touchX.coerceIn(trackLeft, trackRight)
        val fraction   = (clamped - trackLeft) / (trackRight - trackLeft)
        val newProgress = (fraction * max).toInt().coerceIn(0, max)
        if (newProgress != progress) {
            progress = newProgress
            listener?.onProgressChanged(newProgress, true)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredH = (resources.displayMetrics.density * 44).toInt()
        val h = resolveSize(desiredH, heightMeasureSpec)
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
    }
}
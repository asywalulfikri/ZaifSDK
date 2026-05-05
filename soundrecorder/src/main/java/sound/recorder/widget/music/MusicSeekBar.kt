package sound.recorder.widget.music

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

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

    // ─── PROPERTIES ───

    var max: Int = 100
        set(value) {
            val newMax = maxOf(1, value)
            if (field == newMax) return
            field = newMax
            // FIX #6: Clamp ulang progress ketika max berubah
            _progress = _progress.coerceIn(0, field)
            invalidateCachedShaders()
            invalidate()
        }

    // Backing field untuk progress agar setter bisa dipanggil internal
    private var _progress: Int = 0

    var progress: Int
        get() = _progress
        set(value) {
            val clamped = value.coerceIn(0, max)
            // FIX #5: Early return jika value tidak berubah
            if (_progress == clamped) return
            _progress = clamped
            invalidateCachedShaders()
            invalidate()
        }

    // ─── STYLE ───
    var trackColor: Int         = Color.parseColor("#1E2340")
    var progressStartColor: Int = Color.parseColor("#00C9FF")
    var progressEndColor: Int   = Color.parseColor("#92FE9D")
    var glowColor: Int          = Color.parseColor("#00C9FF")
    var thumbColor: Int         = Color.WHITE
    var thumbBorderColor: Int   = Color.parseColor("#00C9FF")
    var showGlow: Boolean       = true
    var showThumb: Boolean      = true

    // ─── PAINT OBJECTS ───
    private val paint      = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint  = Paint(Paint.ANTI_ALIAS_FLAG)

    // ─── FIX #3: Cache untuk gradient agar tidak di-allocate tiap frame ───
    private var cachedProgressShader: LinearGradient? = null
    private var cachedThumbShader: RadialGradient? = null
    private var lastThumbX: Float = -1f
    private var lastThumbCx: Float = -1f
    private var lastThumbR: Float = -1f

    private var isDragging = false

    init {
        // FIX #1: Wajib set LAYER_TYPE_SOFTWARE agar shadow & BlurMaskFilter bekerja
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    /**
     * Invalidate semua cached shader sehingga akan dibuat ulang di frame berikutnya.
     * Dipanggil ketika progress atau max berubah.
     */
    private fun invalidateCachedShaders() {
        lastThumbX = -1f
        lastThumbCx = -1f
        lastThumbR = -1f
        cachedProgressShader = null
        cachedThumbShader = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val thumbR     = if (showThumb) h * 0.38f else 0f
        val trackLeft  = thumbR + 4f
        val trackRight = maxOf(trackLeft + 1f, w - thumbR - 4f)
        val trackW     = trackRight - trackLeft

        val progressFraction = _progress.toFloat() / max.toFloat()
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
            // FIX #2: Pastikan shadow selalu di-clear sebelum track background
            clearShadowLayer()
        }
        canvas.drawRoundRect(trackLeft, trackTop, trackRight, trackBot, trackH / 2f, trackH / 2f, paint)

        // ─── 2. Progress fill ───
        if (thumbX > trackLeft) {
            // FIX #3: Hanya buat LinearGradient baru jika thumbX berubah
            if (thumbX != lastThumbX) {
                cachedProgressShader = LinearGradient(
                    trackLeft, 0f, thumbX, 0f,
                    intArrayOf(progressStartColor, progressEndColor),
                    null,
                    Shader.TileMode.CLAMP
                )
                lastThumbX = thumbX
            }
            paint.shader = cachedProgressShader
            if (showGlow) paint.setShadowLayer(trackH * 2f, 0f, 0f, glowColor)
            canvas.drawRoundRect(trackLeft, trackTop, thumbX, trackBot, trackH / 2f, trackH / 2f, paint)
        }

        // FIX #2: Selalu clear shadow layer setelah progress fill,
        // termasuk saat progress = 0 (blok di atas dilewati)
        paint.clearShadowLayer()
        paint.shader = null

        // ─── 3. Time markers (tick marks) ───
        for (i in 1 until 10) {
            val tickX    = trackLeft + trackW * (i.toFloat() / 10)
            val isActive = tickX <= thumbX
            val tickH    = if (i % 5 == 0) h * 0.25f else h * 0.15f
            val tickTop  = h / 2f - tickH / 2f
            val tickBot  = h / 2f + tickH / 2f

            paint.apply {
                shader = null
                color  = if (isActive) progressEndColor else Color.WHITE
                alpha  = if (isActive) 180 else 40
                style  = Paint.Style.FILL
            }
            canvas.drawRoundRect(tickX - 1.5f, tickTop, tickX + 1.5f, tickBot, 1.5f, 1.5f, paint)
        }

        // ─── 4. Elapsed / remaining time label ───
        val elapsedStr   = formatMs(_progress.toLong())
        val remainingStr = "-${formatMs((max - _progress).toLong())}"

        // FIX #4: Reset textAlign ke LEFT di awal agar state selalu bersih
        textPaint.apply {
            color     = Color.WHITE
            alpha     = 160
            textSize  = h * 0.22f
            typeface  = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            textAlign = Paint.Align.LEFT
        }
        canvas.drawText(elapsedStr, trackLeft, h - 2f, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(remainingStr, trackRight, h - 2f, textPaint)
        // FIX #4: Selalu kembalikan ke LEFT setelah selesai
        textPaint.textAlign = Paint.Align.LEFT

        // ─── 5. Thumb ───
        if (showThumb) {
            drawThumb(canvas, thumbX, h / 2f, thumbR)
        }
    }

    private fun drawThumb(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        // Glow halo
        if (showGlow) {
            thumbPaint.apply {
                maskFilter  = BlurMaskFilter(r * 0.7f, BlurMaskFilter.Blur.NORMAL)
                color       = glowColor
                alpha       = 40
                shader      = null
                style       = Paint.Style.FILL
            }
            canvas.drawCircle(cx, cy, r * 1.2f, thumbPaint)
            thumbPaint.maskFilter = null
        }

        // FIX #3: Hanya buat RadialGradient baru jika cx, cy, atau r berubah
        if (cx != lastThumbCx || r != lastThumbR) {
            cachedThumbShader = RadialGradient(
                cx - r * 0.3f, cy - r * 0.3f, r * 1.2f,
                intArrayOf(Color.WHITE, Color.parseColor("#D0F0FF")),
                null,
                Shader.TileMode.CLAMP
            )
            lastThumbCx = cx
            lastThumbR  = r
        }

        // Main fill
        thumbPaint.apply {
            color  = thumbColor
            alpha  = 255
            style  = Paint.Style.FILL
            shader = cachedThumbShader
        }
        canvas.drawCircle(cx, cy, r, thumbPaint)
        thumbPaint.shader = null

        // Outer ring
        thumbPaint.apply {
            color       = thumbBorderColor
            style       = Paint.Style.STROKE
            strokeWidth = r * 0.18f
        }
        canvas.drawCircle(cx, cy, r, thumbPaint)

        // Center dot
        thumbPaint.apply {
            style = Paint.Style.FILL
            color = thumbBorderColor
        }
        canvas.drawCircle(cx, cy, r * 0.2f, thumbPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
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
        if (width <= 0 || height <= 0 || max <= 0) return

        val thumbR     = if (showThumb) height * 0.38f else 0f
        val trackLeft  = thumbR + 4f
        val trackRight = width - thumbR - 4f

        if (trackRight <= trackLeft) return

        val clamped     = touchX.coerceIn(trackLeft, trackRight)
        val fraction    = (clamped - trackLeft) / (trackRight - trackLeft)
        val newProgress = (fraction * max).toInt().coerceIn(0, max)

        // Gunakan backing field agar tidak trigger setter 2x
        if (newProgress != _progress) {
            _progress = newProgress
            invalidateCachedShaders()
            invalidate()
            listener?.onProgressChanged(newProgress, true)
        }
    }

    private fun formatMs(ms: Long): String {
        val totalSec = maxOf(0, ms / 1000)
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
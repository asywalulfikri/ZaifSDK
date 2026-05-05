package sound.recorder.widget.music


import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * SmoothSeekBar
 *
 * Custom SeekBar tanpa XML, mirip screenshot:
 * - Track tipis warna abu muda (background)
 * - Progress fill warna ungu solid
 * - Thumb putih bulat dengan shadow halus
 * - Tidak ada glow, tidak ada tick mark — clean & minimal
 *
 * Cara pakai:
 *   val seekBar = SmoothSeekBar(context)
 *   seekBar.max      = 100
 *   seekBar.progress = 60
 *   seekBar.listener = object : SmoothSeekBar.OnProgressChangeListener {
 *       override fun onProgressChanged(progress: Int, fromUser: Boolean) { }
 *       override fun onStartTrackingTouch() { }
 *       override fun onStopTrackingTouch()  { }
 *   }
 */
class SmoothSeekBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    interface OnProgressChangeListener {
        fun onProgressChanged(progress: Int, fromUser: Boolean)
        fun onStartTrackingTouch()
        fun onStopTrackingTouch()
    }

    var listener: OnProgressChangeListener? = null

    // ─── PROPERTIES ───────────────────────────────────────────────────────────

    var max: Int = 100
        set(value) {
            val newMax = maxOf(1, value)
            if (field == newMax) return
            field    = newMax
            _progress = _progress.coerceIn(0, field)
            invalidate()
        }

    private var _progress = 0
    var progress: Int
        get() = _progress
        set(value) {
            val clamped = value.coerceIn(0, max)
            if (_progress == clamped) return
            _progress = clamped
            invalidate()
        }

    // ─── STYLE ────────────────────────────────────────────────────────────────
    var trackColor: Int     = Color.parseColor("#E0DDD8")  // abu krem (background track)
    var progressColor: Int  = Color.parseColor("#6C4FD4")  // ungu solid
    var thumbColor: Int     = Color.WHITE
    var thumbShadowColor: Int = Color.parseColor("#33000000")

    // ─── PAINTS ───────────────────────────────────────────────────────────────
    private val paintTrack    = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintProgress = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintThumb    = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintShadow   = Paint(Paint.ANTI_ALIAS_FLAG)

    // ─── STATE ────────────────────────────────────────────────────────────────
    private var isDragging = false

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    // ─── DRAW ─────────────────────────────────────────────────────────────────
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val thumbRadius = h * 0.42f
        val trackLeft   = thumbRadius
        val trackRight  = w - thumbRadius
        val trackWidth  = trackRight - trackLeft
        if (trackWidth <= 0f) return

        val trackH      = h * 0.22f   // tinggi track — tipis seperti screenshot
        val trackTop    = h / 2f - trackH / 2f
        val trackBot    = h / 2f + trackH / 2f
        val trackRadius = trackH / 2f

        val fraction    = _progress.toFloat() / max.toFloat()
        val thumbX      = trackLeft + trackWidth * fraction

        // ── 1. Track background ──────────────────────────────────────────────
        paintTrack.color = trackColor
        paintTrack.style = Paint.Style.FILL
        canvas.drawRoundRect(trackLeft, trackTop, trackRight, trackBot, trackRadius, trackRadius, paintTrack)

        // ── 2. Progress fill (ungu, dari kiri sampai thumbX) ─────────────────
        if (thumbX > trackLeft) {
            paintProgress.color = progressColor
            paintProgress.style = Paint.Style.FILL
            canvas.drawRoundRect(trackLeft, trackTop, thumbX, trackBot, trackRadius, trackRadius, paintProgress)
        }

        // ── 3. Thumb shadow (lingkaran abu transparan di belakang thumb) ──────
        paintShadow.color    = thumbShadowColor
        paintShadow.maskFilter = BlurMaskFilter(thumbRadius * 0.5f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawCircle(thumbX, h / 2f + thumbRadius * 0.15f, thumbRadius * 0.9f, paintShadow)
        paintShadow.maskFilter = null

        // ── 4. Thumb utama (putih bersih) ─────────────────────────────────────
        paintThumb.color = thumbColor
        paintThumb.style = Paint.Style.FILL
        canvas.drawCircle(thumbX, h / 2f, thumbRadius, paintThumb)

        // ── 5. Spekuler kecil di thumb (kesan 3D subtle) ──────────────────────
        val specPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#30FFFFFF")
        }
        canvas.drawCircle(
            thumbX - thumbRadius * 0.22f,
            h / 2f - thumbRadius * 0.22f,
            thumbRadius * 0.35f,
            specPaint
        )
    }

    // ─── TOUCH ────────────────────────────────────────────────────────────────
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = true
                parent?.requestDisallowInterceptTouchEvent(true)
                updateFromTouch(event.x)
                listener?.onStartTrackingTouch()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) updateFromTouch(event.x)
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

    private fun updateFromTouch(touchX: Float) {
        if (width <= 0 || height <= 0 || max <= 0) return

        val thumbRadius = height * 0.42f
        val trackLeft   = thumbRadius
        val trackRight  = width - thumbRadius
        if (trackRight <= trackLeft) return

        val clamped     = touchX.coerceIn(trackLeft, trackRight)
        val fraction    = (clamped - trackLeft) / (trackRight - trackLeft)
        val newProgress = (fraction * max).toInt().coerceIn(0, max)

        if (newProgress != _progress) {
            _progress = newProgress
            invalidate()
            listener?.onProgressChanged(newProgress, true)
        }
    }

    // ─── MEASURE ──────────────────────────────────────────────────────────────
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredH = (resources.displayMetrics.density * 40).toInt()
        val h = resolveSize(desiredH, heightMeasureSpec)
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
    }
}
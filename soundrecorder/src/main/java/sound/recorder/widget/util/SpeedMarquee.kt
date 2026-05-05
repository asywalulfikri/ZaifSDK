package sound.recorder.widget.util

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.Log
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.view.animation.LinearInterpolator
import android.widget.Scroller
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import sound.recorder.widget.R
import sound.recorder.widget.listener.MyCompleteMarqueeListener

@SuppressLint("AppCompatCustomView")
class SpeedMarquee(context: Context?, attrs: AttributeSet?, defStyle: Int) :
    TextView(context, attrs, defStyle) {

    var textScroller: Scroller? = null
    private var mXPaused = 0
    var isPaused = true
        private set
    private var mScrollSpeed = 222f
    private var onGlobalLayoutListener: OnGlobalLayoutListener? = null

    interface OnScrollCompleteListener {
        fun onScrollComplete()
    }

    private var onScrollCompleteListener: OnScrollCompleteListener? = null

    fun setOnScrollCompleteListener(listener: OnScrollCompleteListener?) {
        onScrollCompleteListener = listener
    }

    constructor(context: Context) : this(context, null) {
        initialize()
    }

    @SuppressLint("CustomViewStyleable")
    constructor(context: Context, attrs: AttributeSet?) : this(
        context,
        attrs,
        android.R.attr.textViewStyle
    ) {
        context.obtainStyledAttributes(attrs, R.styleable.SpeedMarquee).apply {
            mScrollSpeed = getFloat(R.styleable.SpeedMarquee_marquee_speed, 222f)
            recycle()
        }
        initialize()
    }

    private fun initialize() {
        setSingleLine()
        ellipsize = null
        visibility = VISIBLE
        attachGlobalListener()
    }

    // ─── CUSTOMIZATION HANDLERS ───

    /** Mengganti warna teks (Color Int) */
    fun setTextColorCustom(@ColorInt color: Int) {
        setTextColor(color)
        invalidate()
    }

    /** Mengganti warna teks dari Resource ID */
    fun setTextColorRes(resId: Int) {
        setTextColor(ContextCompat.getColor(context, resId))
    }

    /** Mengganti background warna (Color Int) */
    fun setBackgroundColorCustom(@ColorInt color: Int) {
        setBackgroundColor(color)
    }

    /** Mengganti background dari Resource ID (Drawable/Shape) */
    fun setBackgroundResourceCustom(resId: Int) {
        setBackgroundResource(resId)
    }

    /** Mengganti Font via Typeface object */
    fun setTypefaceCustom(tf: Typeface?) {
        typeface = tf
        requestLayout() // Hitung ulang dimensi karena font berubah
        invalidate()
    }

    /** Mengganti Font dari Folder res/font */
    fun setFontRes(resId: Int) {
        try {
            val tf = ResourcesCompat.getFont(context, resId)
            setTypefaceCustom(tf)
        } catch (e: Exception) {
            Log.e("SpeedMarquee", "Font res not found: ${e.message}")
        }
    }

    /** Mengganti Font dari Folder assets */
    fun setFontAssets(path: String) {
        try {
            val tf = Typeface.createFromAsset(context.assets, path)
            setTypefaceCustom(tf)
        } catch (e: Exception) {
            Log.e("SpeedMarquee", "Font asset not found: ${e.message}")
        }
    }

    // ─── SCROLL LOGIC ───

    private fun attachGlobalListener() {
        if (onGlobalLayoutListener == null) {
            onGlobalLayoutListener = OnGlobalLayoutListener {
                if (isPaused) startScroll()
            }
            viewTreeObserver.addOnGlobalLayoutListener(onGlobalLayoutListener)
        }
    }

    override fun onDetachedFromWindow() {
        removeGlobalListener()
        super.onDetachedFromWindow()
    }

    fun startScroll() {
        attachGlobalListener()
        if (!checkIfNeedsScrolling()) {
            stopScroll()
            return
        }
        if (!isPaused && textScroller != null && !textScroller!!.isFinished) return

        mXPaused = -1 * (width / 2)
        isPaused = true
        resumeScroll()
    }

    fun clear() {
        try {
            stopScroll()
            removeGlobalListener()
            text = context.getString(R.string.text_choose_not)
            visibility = VISIBLE
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopScroll() {
        textScroller?.abortAnimation()
        textScroller = null
        isPaused = true
        mXPaused = 0
        scrollTo(0, 0)
    }

    fun startScrollAfterUpdate(currX: Int) {
        try {
            val needsScrolling = checkIfNeedsScrolling()
            mXPaused = currX
            isPaused = true
            if (needsScrolling) resumeScroll() else pauseScroll()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Synchronized
    private fun removeGlobalListener() {
        onGlobalLayoutListener?.let {
            viewTreeObserver.removeOnGlobalLayoutListener(it)
            onGlobalLayoutListener = null
        }
    }

    private fun checkIfNeedsScrolling(): Boolean {
        if (width <= 0) return false
        val textWidth = paint.measureText(text.toString())
        return textWidth > width
    }

    fun resumeScroll() {
        if (!isPaused) return

        setHorizontallyScrolling(true)
        textScroller = Scroller(this.context, LinearInterpolator())
        setScroller(textScroller)

        val scrollingLen = calculateScrollingLen()
        val distance = scrollingLen - (width + mXPaused)
        if (distance <= 0) return

        val duration = (1000f * distance / mScrollSpeed).toInt()

        visibility = VISIBLE
        isPaused = false
        textScroller!!.startScroll(mXPaused, 0, distance, 0, duration)
        invalidate()
    }

    private fun calculateScrollingLen(): Int = textLength + width

    private val textLength: Int
        get() {
            val rect = Rect()
            val strTxt = text.toString()
            paint.getTextBounds(strTxt, 0, strTxt.length, rect)
            return rect.width()
        }

    fun pauseScroll() {
        if (textScroller != null && !isPaused) {
            isPaused = true
            mXPaused = textScroller!!.currX
            textScroller!!.abortAnimation()
        }
    }

    override fun computeScroll() {
        super.computeScroll()
        val scroller = textScroller ?: return

        if (scroller.isFinished && !isPaused) {
            val isAtEnd = scroller.currX >= calculateScrollingLen() - width
            if (isAtEnd) {
                pauseScroll()
                onScrollCompleteListener?.onScrollComplete()
                MyCompleteMarqueeListener.postOnCompleteMarquee()
            } else {
                isPaused = true
                resumeScroll()
            }
        }
    }

    fun setSpeed(value: Float) {
        mScrollSpeed = value
        val currentX = textScroller?.currX ?: 0
        stopScroll()
        startScrollAfterUpdate(currentX)
    }

    fun getSpeed(): Float = mScrollSpeed
}
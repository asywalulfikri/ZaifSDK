package sound.recorder.widget.util

import sound.recorder.widget.R
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.view.animation.LinearInterpolator
import android.widget.Scroller
import android.widget.TextView
import sound.recorder.widget.listener.MyCompleteMarqueeListener
import kotlin.apply
import kotlin.let

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

    private fun attachGlobalListener() {
        if (onGlobalLayoutListener == null) {
            onGlobalLayoutListener = OnGlobalLayoutListener {
                if (isPaused) {
                    startScroll()
                }
            }
            viewTreeObserver.addOnGlobalLayoutListener(onGlobalLayoutListener)
        }
    }

    override fun onDetachedFromWindow() {
        removeGlobalListener()
        super.onDetachedFromWindow()
    }

    fun startScroll() {
        // Jika listener pernah dihapus (oleh clear), pasang lagi
        attachGlobalListener()

        val needsScrolling = checkIfNeedsScrolling()
        if (!needsScrolling) {
            stopScroll()
            return
        }

        // Jangan restart jika sudah berjalan
        if (!isPaused && textScroller != null && !textScroller!!.isFinished) return

        mXPaused = -1 * (width / 2)
        isPaused = true // Set true agar resumeScroll mau jalan
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
            if (needsScrolling) {
                resumeScroll()
            } else {
                pauseScroll()
            }
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

    private fun calculateScrollingLen(): Int {
        return textLength + width
    }

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
                // Looping jika belum benar-benar sampai ujung (jarang terjadi dengan LinearInterpolator)
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
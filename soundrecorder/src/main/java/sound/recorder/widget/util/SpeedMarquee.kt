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
import kotlin.takeIf

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
            mScrollSpeed = getFloat(sound.recorder.widget.R.styleable.SpeedMarquee_marquee_speed, 222f)
            recycle()
        }
        initialize()
    }

    private fun initialize() {
        setSingleLine()
        ellipsize = null
        visibility = VISIBLE
        onGlobalLayoutListener = OnGlobalLayoutListener {
            if (isPaused) startScroll() // Hanya panggil jika marquee sedang pause
        }
        viewTreeObserver.addOnGlobalLayoutListener(onGlobalLayoutListener)
    }

    override fun onDetachedFromWindow() {
        removeGlobalListener()
        super.onDetachedFromWindow()
    }


    fun startScroll() {
        val needsScrolling = checkIfNeedsScrolling()
        if (!needsScrolling || !isPaused) return // Jangan restart jika sudah berjalan
        mXPaused = -1 * (width / 2)
        isPaused = true
        resumeScroll()
    }

    fun startScrollAfterUpdate(currX: Int) {
        val needsScrolling = checkIfNeedsScrolling()
        mXPaused = currX
        isPaused = true
        if (needsScrolling) {
            resumeScroll()
        } else {
            pauseScroll()
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
        measure(0, 0)
        val textViewWidth = width
        if (textViewWidth == 0) return false
        val textWidth = textLength.toFloat()
        return textWidth > textViewWidth
    }

    fun resumeScroll() {
        if (!isPaused) return
        setHorizontallyScrolling(true)
        textScroller = Scroller(this.context, LinearInterpolator())
        setScroller(textScroller)

        val scrollingLen = calculateScrollingLen()
        val distance = scrollingLen - (width + mXPaused)

        // Hitung durasi berdasarkan jarak dan kecepatan
        val duration = (1000f * distance / mScrollSpeed).toInt()

        visibility = VISIBLE

        // Mulai scroll
        textScroller!!.startScroll(mXPaused, 0, distance, 0, duration)
        invalidate()
        isPaused = false
    }

    private fun calculateScrollingLen(): Int {
        val length = textLength
        return length + width
    }

    private val textLength: Int
        get() {
            val tp = paint
            val rect = Rect()
            val strTxt = text.toString()
            tp.getTextBounds(strTxt, 0, strTxt.length, rect)
            return rect.width()
        }

    fun pauseScroll() {
        textScroller?.takeIf { !isPaused }?.apply {
            isPaused = true
            mXPaused = currX
            abortAnimation()
        }
    }

    override fun computeScroll() {
        super.computeScroll()

        // Cek apakah scroller sudah selesai dan posisi akhir telah tercapai
        textScroller?.takeIf { it.isFinished && !isPaused }?.let {
            val isAtEnd = textScroller!!.currX >= calculateScrollingLen() - width
            if (isAtEnd) {
                // Hentikan scrolling karena sudah mencapai akhir
                pauseScroll()
                onScrollCompleteListener?.onScrollComplete()
                MyCompleteMarqueeListener.postOnCompleteMarquee()
            } else {
                // Lanjutkan scrolling jika belum selesai
                startScroll()
            }
        }
    }

    fun setSpeed(value: Float) {
        mScrollSpeed = value
        startScrollAfterUpdate(textScroller?.currX ?: 0)
    }

    fun getSpeed(): Float = mScrollSpeed
}

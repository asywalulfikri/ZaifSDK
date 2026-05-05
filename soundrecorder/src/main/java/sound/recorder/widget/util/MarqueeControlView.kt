package sound.recorder.widget.util

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import sound.recorder.widget.R

class MarqueeControlView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val marquee: SpeedMarquee
    private val btnMinus: ImageView
    private val btnPlus: ImageView
    private val btnClear: ImageView

    private val speedStep    = 20f
    private val speedMin     = 20f
    private val speedDefault = 45f

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL

        val density    = resources.displayMetrics.density
        val dp4        = (4  * density).toInt()
        val dp8        = (8  * density).toInt()
        val iconSize   = (28 * density).toInt()
        val iconPad    = (6  * density).toInt()
        val iconMargin = (4  * density).toInt()

        setPadding(dp8, 0, dp8, 0)

        btnMinus = ImageView(context).apply {
            layoutParams = LayoutParams(iconSize, iconSize)
            setBackgroundResource(R.drawable.minus)
            setPadding(iconPad, iconPad, iconPad, iconPad)
            setColorFilter(Color.WHITE)
            visibility = View.GONE
        }
        addView(btnMinus)

        marquee = SpeedMarquee(context, null, android.R.attr.textViewStyle).apply {
            layoutParams = LayoutParams(0, (30 * density).toInt(), 1f).apply {
                setMargins(dp4, 0, dp4, 0)
            }
            setBackgroundResource(R.drawable.bg_marquee_curved)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                (16 * density).toInt(), 0,
                (16 * density).toInt(), 0
            )
            isSingleLine = true
            setTextColor(Color.WHITE)
            textSize = 12f

            try {
                typeface = ResourcesCompat.getFont(context, R.font.campton_thin)
            } catch (e: Exception) {
                Log.e("MarqueeControl", "Font load error: ${e.message}")
            }

            setSpeed(speedDefault)
            text = context.getString(R.string.text_choose_not)
        }
        addView(marquee)

        btnClear = ImageView(context).apply {
            layoutParams = LayoutParams(iconSize, iconSize).apply {
                setMargins(0, 0, iconMargin, 0)
            }
            setBackgroundResource(R.drawable.circle_red_champ )
            setImageResource(R.drawable.ic_delete_white)
            setColorFilter(Color.WHITE)
            setPadding(iconPad, iconPad, iconPad, iconPad)
            visibility = View.GONE
        }
        addView(btnClear)

        btnPlus = ImageView(context).apply {
            layoutParams = LayoutParams(iconSize, iconSize)
            setBackgroundResource(R.drawable.plus)
            setPadding(iconPad, iconPad, iconPad, iconPad)
            setColorFilter(Color.WHITE)
            visibility = View.GONE
        }
        addView(btnPlus)

        setupClickListeners()
    }

    private fun setupClickListeners() {
        btnPlus.setOnClickListener {
            marquee.setSpeed(marquee.getSpeed() + speedStep)
            btnMinus.visibility = View.VISIBLE
        }

        btnMinus.setOnClickListener {
            val newSpeed = (marquee.getSpeed() - speedStep).coerceAtLeast(speedMin)
            marquee.setSpeed(newSpeed)
            if (newSpeed <= speedMin) btnMinus.visibility = View.GONE
        }

        btnClear.setOnClickListener {
            clear()
        }
    }

    // ─── CUSTOMIZATION API ───

    fun setMarqueeTextColor(@ColorInt color: Int) {
        marquee.setTextColor(color)
    }

    fun setMarqueeTextColorRes(resId: Int) {
        marquee.setTextColor(ContextCompat.getColor(context, resId))
    }

    fun setMarqueeBackgroundRes(resId: Int) {
        marquee.setBackgroundResource(resId)
    }

    fun setMarqueeFontRes(resId: Int) {
        try {
            marquee.typeface = ResourcesCompat.getFont(context, resId)
            marquee.invalidate()
        } catch (e: Exception) {
            Log.e("MarqueeControl", "Font change error: ${e.message}")
        }
    }

    fun setButtonsColor(@ColorInt color: Int) {
        btnPlus.setColorFilter(color)
        btnMinus.setColorFilter(color)
        btnClear.setColorFilter(color)
    }

    // ─── FLOW API ───

    fun setText(text: String) {
        marquee.text = text
        marquee.setSpeed(speedDefault)
        marquee.startScroll()
        // Tampilkan tombol kontrol ketika teks di-set
        showControls(true)
    }

    fun clear() {
        marquee.clear()
        showControls(false)
    }

    fun pauseScroll() { marquee.pauseScroll() }
    fun startScroll() { marquee.startScroll() }

    fun startScrollAfterUpdate(currX: Int) {
        marquee.startScrollAfterUpdate(currX)
    }

    fun getCurrentScrollX(): Int = marquee.textScroller?.currX ?: 0

    fun setOnScrollCompleteListener(listener: SpeedMarquee.OnScrollCompleteListener?) {
        marquee.setOnScrollCompleteListener(listener)
    }

    fun showControls(show: Boolean) {
        val v = if (show) View.VISIBLE else View.GONE
        btnPlus.visibility  = v
        btnClear.visibility = v
        btnMinus.visibility = if (show && marquee.getSpeed() > speedMin) View.VISIBLE else View.GONE
    }

    override fun onDetachedFromWindow() {
        marquee.setOnScrollCompleteListener(null)
        marquee.stopScroll()
        super.onDetachedFromWindow()
    }
}
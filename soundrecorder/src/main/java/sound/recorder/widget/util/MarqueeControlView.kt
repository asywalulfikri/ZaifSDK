package sound.recorder.widget.util

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
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

    // ─── PENGATURAN KECEPATAN ───
    // speedStep: seberapa besar perubahan saat tombol +/- diklik
    // ─── PENGATURAN KECEPATAN IDEAL ───
    private val speedStep = 20f     // Lonjakan kecepatan saat klik +/-
    private val speedMin = 20f      // Batas paling lambat (biar gak siput banget)
    private val speedDefault = 45f  // KECEPATAN AWAL (Pas, tidak pelan & tidak ngebut)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL

        val dp4 = (4 * resources.displayMetrics.density).toInt()
        val dp8 = (8 * resources.displayMetrics.density).toInt()
        setPadding(dp8, 0, dp8, 0)

        val iconSize = (28 * resources.displayMetrics.density).toInt()
        val iconPad = (6 * resources.displayMetrics.density).toInt()
        val iconMargin = (4 * resources.displayMetrics.density).toInt()

        btnMinus = ImageView(context).apply {
            layoutParams = LayoutParams(iconSize, iconSize)
            setBackgroundResource(R.drawable.minus)
            setPadding(iconPad, iconPad, iconPad, iconPad)
            setColorFilter(android.graphics.Color.WHITE)
            visibility = View.GONE
        }
        addView(btnMinus)

        marquee = SpeedMarquee(context, null, android.R.attr.textViewStyle).apply {
            layoutParams = LayoutParams(0, (30 * resources.displayMetrics.density).toInt(), 1f).apply {
                setMargins(dp4, 0, dp4, 0)
            }
            setBackgroundResource(R.drawable.bg_marquee_curved)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                (10 * resources.displayMetrics.density).toInt(), 0,
                (10 * resources.displayMetrics.density).toInt(), 0
            )
            isSingleLine = true
            setTextColor(android.graphics.Color.WHITE)
            textSize = 12f

            // Menggunakan font kustom
            try {
                typeface = ResourcesCompat.getFont(context, R.font.campton_thin)
            } catch (e: Exception) {
                android.util.Log.e("MarqueeControl", "Font error: ${e.message}")
            }

            // SET KECEPATAN AWAL DI SINI
            setSpeed(speedDefault)

            text = context.getString(R.string.text_choose_not)
        }
        addView(marquee)

        btnClear = ImageView(context).apply {
            layoutParams = LayoutParams(iconSize, iconSize).apply {
                setMargins(0, 0, iconMargin, 0)
            }
            setBackgroundResource(R.drawable.button_black)
            setImageResource(R.drawable.ic_delete_white)
            setColorFilter(android.graphics.Color.WHITE)
            setPadding(iconPad, iconPad, iconPad, iconPad)
            visibility = View.GONE
        }
        addView(btnClear)

        btnPlus = ImageView(context).apply {
            layoutParams = LayoutParams(iconSize, iconSize)
            setBackgroundResource(R.drawable.plus)
            setPadding(iconPad, iconPad, iconPad, iconPad)
            setColorFilter(android.graphics.Color.WHITE)
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

    // ─── PUBLIC API ───

    fun setText(text: String) {
        marquee.text = text
        // Reset ke kecepatan default setiap kali ganti teks agar tidak terbawa speed kencang sebelumnya
        marquee.setSpeed(speedDefault)
        marquee.startScroll()
        showControls(true)
    }

    fun clear() {
        marquee.clear()
        showControls(false)
    }

    fun pauseScroll() { marquee.pauseScroll() }
    fun startScroll() { marquee.startScroll() }
    fun startScrollAfterUpdate(currX: Int) { marquee.startScrollAfterUpdate(currX) }
    fun getCurrentScrollX(): Int { return marquee.textScroller?.currX ?: 0 }
    fun setOnScrollCompleteListener(listener: SpeedMarquee.OnScrollCompleteListener?) {
        marquee.setOnScrollCompleteListener(listener)
    }

    private fun showControls(show: Boolean) {
        val v = if (show) View.VISIBLE else View.GONE
        btnPlus.visibility = v
        btnClear.visibility = v
        // Minus muncul hanya jika speed di atas minimum
        btnMinus.visibility = if (show && marquee.getSpeed() > speedMin) View.VISIBLE else View.GONE
    }

    override fun onDetachedFromWindow() {
        marquee.setOnScrollCompleteListener(null)
        marquee.stopScroll()
        btnPlus.setOnClickListener(null)
        btnMinus.setOnClickListener(null)
        btnClear.setOnClickListener(null)
        super.onDetachedFromWindow()
    }
}
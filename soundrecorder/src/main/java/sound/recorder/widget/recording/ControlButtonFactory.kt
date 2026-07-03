package sound.recorder.widget.recording


import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.widget.Button
import com.intuit.sdp.R as SdpR

class ControlButtonFactory(
    private val context: Context,
    private val config: ControlConfig,
    private val typeface: Typeface?
) {

    private fun sdp(id: Int)  = context.resources.getDimensionPixelSize(id)
    private fun sdpF(id: Int) = context.resources.getDimension(id)

    fun createBtn(label: String, isRed: Boolean = false): Button = Button(context).apply {
        text = label
        setTextColor(if (isRed) Color.WHITE else config.textColor)
        textSize = config.fontSize
        this.typeface = this@ControlButtonFactory.typeface ?: Typeface.DEFAULT_BOLD
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), createBg(pressed = true, isRed = isRed))
            addState(intArrayOf(), createBg(pressed = false, isRed = isRed))
        }
    }

    fun createBtnWithIcon(label: String, iconRes: Int, isRed: Boolean = false): Button = createBtn(label, isRed).apply {
        val iconSize = sdp(SdpR.dimen._16sdp)
        val drawable = androidx.core.content.res.ResourcesCompat.getDrawable(context.resources, iconRes, null)
        drawable?.let {
            it.setBounds(0, 0, iconSize, iconSize)
            setCompoundDrawables(null, it, null, null)
        }
        compoundDrawablePadding = sdp(SdpR.dimen._1sdp)
        textSize = 7f 
        gravity = Gravity.CENTER
        setPadding(0, sdp(SdpR.dimen._3sdp), 0, sdp(SdpR.dimen._3sdp))
    }

    @SuppressLint("UseKtx")
    fun createBg(pressed: Boolean, isRed: Boolean = false): GradientDrawable = GradientDrawable().apply {
        cornerRadius = sdpF(config.cornerRadiusDimenRes)
        val stroke = sdp(SdpR.dimen._1sdp)
        when {
            isRed -> {
                colors = if (pressed)
                    intArrayOf(Color.parseColor("#FF4444"), Color.parseColor("#AA0000"))
                else
                    intArrayOf(Color.parseColor("#FF3333"), Color.parseColor("#CC0000"))
                orientation = GradientDrawable.Orientation.TOP_BOTTOM
                setStroke(stroke, Color.parseColor("#FF6666"))
            }
            pressed && config.btnPressedGradientColors != null -> {
                colors      = config.btnPressedGradientColors
                orientation = config.btnGradientOrientation
                setStroke(stroke, config.strokeColor)
            }
            !pressed && config.btnGradientColors != null -> {
                colors      = config.btnGradientColors
                orientation = config.btnGradientOrientation
                setStroke(stroke, config.strokeColor)
            }
            else -> {
                setColor(if (pressed) config.btnPressedColor else config.btnColor)
                setStroke(stroke, config.strokeColor)
            }
        }
    }

    @SuppressLint("UseKtx")
    fun createBgRec(isOn: Boolean): GradientDrawable = GradientDrawable().apply {
        cornerRadius = sdpF(config.cornerRadiusDimenRes)
        setStroke(
            sdp(SdpR.dimen._1sdp),
            if (isOn) Color.parseColor("#FF9999") else config.strokeColor
        )
        colors = if (isOn)
            intArrayOf(Color.parseColor("#FF3333"), Color.parseColor("#CC0000"))
        else
            config.btnGradientColors ?: intArrayOf(config.btnColor, config.btnColor)
        orientation = config.btnGradientOrientation
    }

    @SuppressLint("UseKtx")
    fun createBgStopOn(): GradientDrawable = GradientDrawable().apply {
        colors = intArrayOf(Color.parseColor("#FF4444"), Color.parseColor("#CC0000"))
        orientation = GradientDrawable.Orientation.TOP_BOTTOM
        cornerRadius = sdpF(config.cornerRadiusDimenRes)
        setStroke(sdp(SdpR.dimen._1sdp), Color.parseColor("#FF9999"))
    }

    // ─── GAME STYLE (REFERENCE IMAGE) ───

    @SuppressLint("UseKtx")
    fun createGameStyleBg(baseColorHex: String, isPressed: Boolean = false): android.graphics.drawable.Drawable {
        val color = Color.parseColor("#$baseColorHex")
        
        val shadow = GradientDrawable().apply {
            val hsv = FloatArray(3)
            Color.colorToHSV(color, hsv)
            hsv[2] *= 0.7f 
            setColor(Color.HSVToColor(hsv))
            cornerRadius = sdpF(SdpR.dimen._10sdp)
        }

        val main = GradientDrawable().apply {
            setColor(if (isPressed) Color.argb(200, Color.red(color), Color.green(color), Color.blue(color)) else color)
            cornerRadius = sdpF(SdpR.dimen._10sdp)
        }

        val shine = GradientDrawable().apply {
            setColors(intArrayOf(Color.parseColor("#60FFFFFF"), Color.TRANSPARENT))
            orientation = GradientDrawable.Orientation.TOP_BOTTOM
            cornerRadius = sdpF(SdpR.dimen._10sdp)
        }

        return LayerDrawable(arrayOf(shadow, main, shine)).apply {
            val offsetShadow = sdp(SdpR.dimen._4sdp)
            setLayerInset(0, 0, offsetShadow, 0, 0)
            setLayerInset(1, 0, 0, 0, offsetShadow)
            setLayerInset(2, sdp(SdpR.dimen._4sdp), sdp(SdpR.dimen._2sdp), sdp(SdpR.dimen._4sdp), offsetShadow + sdp(SdpR.dimen._15sdp))
        }
    }

    fun createGameBtn(label: String, colorHex: String, iconRes: Int): Button = Button(context).apply {
        text = label
        setTextColor(Color.WHITE)
        textSize = 9f
        typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        setAllCaps(true)
        
        val iconSize = sdp(SdpR.dimen._18sdp)
        val drawable = androidx.core.content.res.ResourcesCompat.getDrawable(context.resources, iconRes, null)
        drawable?.let {
            it.setBounds(0, 0, iconSize, iconSize)
            it.setTint(Color.WHITE)
            setCompoundDrawables(null, null, it, null)
        }
        compoundDrawablePadding = sdp(SdpR.dimen._2sdp)
        
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), createGameStyleBg(colorHex, true))
            addState(intArrayOf(), createGameStyleBg(colorHex, false))
        }
        
        gravity = Gravity.CENTER
        setPadding(sdp(SdpR.dimen._8sdp), 0, sdp(SdpR.dimen._8sdp), sdp(SdpR.dimen._4sdp))
    }

    @SuppressLint("UseKtx")
    fun createGameStyleBgRec(isOn: Boolean): android.graphics.drawable.Drawable {
        return createGameStyleBg(if (isOn) "F44336" else "8BC34A")
    }

    @SuppressLint("UseKtx")
    fun createGameStyleBgStopOn(): android.graphics.drawable.Drawable {
        return createGameStyleBg("F44336")
    }
}

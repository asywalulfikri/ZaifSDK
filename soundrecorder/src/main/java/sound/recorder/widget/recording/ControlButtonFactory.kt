package sound.recorder.widget.recording


import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
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
}
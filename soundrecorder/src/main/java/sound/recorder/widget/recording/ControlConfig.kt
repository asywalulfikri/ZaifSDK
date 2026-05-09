package sound.recorder.widget.recording


import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import com.intuit.sdp.R as SdpR

@SuppressLint("UseKtx")
data class ControlConfig(
    val textColor: Int = Color.parseColor("#F5D76E"),
    val bgColor: Int = Color.parseColor("#1F1612"),
    val btnColor: Int = Color.parseColor("#3D2510"),
    val btnPressedColor: Int = Color.parseColor("#4A2E1C"),
    val strokeColor: Int = Color.parseColor("#9B6A14"),
    val cornerRadiusDimenRes: Int = SdpR.dimen._6sdp,
    val fontSize: Float = 8f,
    val fontResId: Int? = null,
    val btnGradientColors: IntArray? = intArrayOf(
        Color.parseColor("#5A3A1A"),
        Color.parseColor("#2A1508")
    ),
    val btnPressedGradientColors: IntArray? = intArrayOf(
        Color.parseColor("#7A5030"),
        Color.parseColor("#3D2510")
    ),
    val btnGradientOrientation: GradientDrawable.Orientation = GradientDrawable.Orientation.TOP_BOTTOM,
    val btnWidthDimenRes: Int? = null,
    val btnHeightDimenRes: Int? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ControlConfig
        if (textColor != other.textColor) return false
        if (bgColor != other.bgColor) return false
        if (btnColor != other.btnColor) return false
        if (btnPressedColor != other.btnPressedColor) return false
        if (strokeColor != other.strokeColor) return false
        if (cornerRadiusDimenRes != other.cornerRadiusDimenRes) return false
        if (fontSize != other.fontSize) return false
        if (fontResId != other.fontResId) return false
        if (!btnGradientColors.contentEquals(other.btnGradientColors)) return false
        if (!btnPressedGradientColors.contentEquals(other.btnPressedGradientColors)) return false
        if (btnGradientOrientation != other.btnGradientOrientation) return false
        return true
    }

    override fun hashCode(): Int {
        var result = textColor
        result = 31 * result + bgColor
        result = 31 * result + btnColor
        result = 31 * result + btnPressedColor
        result = 31 * result + strokeColor
        result = 31 * result + cornerRadiusDimenRes
        result = 31 * result + fontSize.hashCode()
        result = 31 * result + (fontResId ?: 0)
        result = 31 * result + (btnGradientColors?.contentHashCode() ?: 0)
        result = 31 * result + (btnPressedGradientColors?.contentHashCode() ?: 0)
        result = 31 * result + btnGradientOrientation.hashCode()
        return result
    }
}
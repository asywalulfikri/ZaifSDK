package sound.recorder.widget.model

/**
 * Created by Susheel Kumar Karam
 * Website - SusheelKaram.com
 */
data class Recording(
    val id: Long,
    val name: String,
    val duration: Int,
    val dateAdded: Int,
    val size: Int
);
package sound.recorder.widget.builder

data class ZaifSDKConfig(
    val appName: String,
    val versionCode: Int,
    val versionName: String,
    val applicationId: String,
    val developerName: String,
    val showNote: Boolean,
    val showChangeColor: Boolean,
    val backgroundWidgetColor: String,
    val showListSong: Boolean,
    val showVolume: Boolean,
    val showTooltip: Boolean
)

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
    val showTooltip: Boolean,
    val fcmKey: String,
    val isPromotNot: Boolean = false,
    val isPromotNote: Boolean = false,
    val isDownload: Boolean = false,
    val isLockRec: Boolean = false,
    val isLockMusic: Boolean = false,
    val isCoin: Boolean = false,
    val isFilterTutorial : Boolean = false,
    val isVideo : Boolean = false,
    /** Lihat [ZaifSDKBuilder.hasPremiumPurchase]. False = app punya pembelian (default aman). */
    val noPremiumPurchase: Boolean = false,
) {
    val hasPremiumPurchase: Boolean get() = !noPremiumPurchase
}

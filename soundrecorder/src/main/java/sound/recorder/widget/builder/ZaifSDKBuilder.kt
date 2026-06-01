package sound.recorder.widget.builder

import android.content.Context

class ZaifSDKBuilder private constructor(
    private val context: Context
) {
    var appName: String = ""
    var versionCode: Int = 1
    var versionName: String = "1.0"
    var applicationId: String = ""
    var developerName: String = ""
    var showNote = false
    var showChangeColor = false
    var backgroundWidgetColor = "#FFFFFF"
    var showListSong = true
    var showVolume = true
    var showTooltip = false
    var fcmKey = ""
    var isPromotNot = false

    fun setAppName(value: String) = apply { appName = value }
    fun setVersionCode(value: Int) = apply { versionCode = value }
    fun setVersionName(value: String) = apply { versionName = value }
    fun setApplicationId(value: String) = apply { applicationId = value }
    fun setDeveloperName(value: String) = apply { developerName = value }
    fun showNote(value: Boolean) = apply { showNote = value }
    fun showChangeColor(value: Boolean) = apply { showChangeColor = value }
    fun setBackgroundWidgetColor(value: String) = apply { backgroundWidgetColor = value }
    fun showListSong(value: Boolean) = apply { showListSong = value }
    fun showVolume(value: Boolean) = apply { showVolume = value }
    fun showTooltip(value: Boolean) = apply { showTooltip = value }
    fun setFcmKey(value: String) = apply { fcmKey = value }
    fun isPromotNot(value: Boolean) = apply { isPromotNot = value }

    fun build(): ZaifSDKConfig {
        val config = ZaifSDKConfig(
            appName = appName,
            versionCode = versionCode,
            versionName = versionName,
            applicationId = applicationId,
            developerName = developerName,
            showNote = showNote,
            showChangeColor = showChangeColor,
            backgroundWidgetColor = backgroundWidgetColor,
            showListSong = showListSong,
            showVolume = showVolume,
            showTooltip = showTooltip,
            fcmKey = fcmKey,
            isPromotNot = isPromotNot
        )
        instance = config // ✅ langsung simpan di memory
        ZaifSDKStorage.save(context, config)
        return config
    }

    companion object {

        @Volatile
        private var instance: ZaifSDKConfig? = null

        fun builder(context: Context) = ZaifSDKBuilder(context.applicationContext)

        fun load(context: Context): ZaifSDKConfig? {
            return instance ?: ZaifSDKStorage.load(context)?.also { instance = it }
        }
    }
}
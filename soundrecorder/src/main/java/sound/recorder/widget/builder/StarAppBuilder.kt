package sound.recorder.widget.builder

import android.content.Context
import android.content.SharedPreferences
import sound.recorder.widget.util.Constant

class StarAppBuilder private constructor(
    val applicationId : String?,
    val showBanner : Boolean = false,
    val showInterstitial : Boolean = false,
    val enable : Boolean) {

    // Builder class to construct MyObject
    class Builder(private val context: Context) {
        private var applicationId: String? = null
        private var showBanner : Boolean = false
        private var showInterstitial : Boolean = false
        private var enable : Boolean = false

        fun setApplicationId(applicationId: String?): Builder {
            this.applicationId = applicationId
            return this
        }

        fun showBanner(showBanner: Boolean): Builder {
            this.showBanner = showBanner
            return this
        }

        fun showInterstitial(showInterstitial: Boolean): Builder {
            this.showInterstitial= showInterstitial
            return this
        }
        fun setEnable(enable: Boolean): Builder {
            this.enable = enable
            return this
        }

        // Build function to create an instance of MyObject
        fun build(): StarAppBuilder {
            val myObject = StarAppBuilder(applicationId,showBanner,showInterstitial,enable)

            saveToSharedPreferences(myObject)

            return myObject
        }

        private fun saveToSharedPreferences(starAppBuilder: StarAppBuilder) {
            val sharedPreferences: SharedPreferences = context.getSharedPreferences(
                Constant.KeyShared.shareKey,
                Context.MODE_PRIVATE
            )
            val editor = sharedPreferences.edit()

            editor.putString(Constant.KeyShared.starAppId, starAppBuilder.applicationId)
            editor.putBoolean(Constant.KeyShared.starAppShowBanner,starAppBuilder.showBanner)
            editor.putBoolean(Constant.KeyShared.starAppShowInterstitial,starAppBuilder.showInterstitial)
            editor.putBoolean(Constant.KeyShared.starAppEnable,starAppBuilder.enable)

            editor.apply()
        }
    }

    companion object {
        // Function to get a Builder instance
        fun builder(context: Context): Builder {
            return Builder(context)
        }
    }
}

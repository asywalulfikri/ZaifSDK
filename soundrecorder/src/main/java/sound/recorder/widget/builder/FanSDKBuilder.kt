package sound.recorder.widget.builder

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import sound.recorder.widget.util.Constant

class FanSDKBuilder private constructor(
    val applicationId : String? = null,
    val bannerId: String? = null,
    val bannerHomeId : String? = null,
    val interstitialId: String? = null,
    val enable : Boolean
) {

    // Builder class
    class Builder(private val context: Context) {
        private var applicationId: String? = null
        private var bannerId: String? = null
        private var bannerHomeId: String? = null
        private var interstitialId: String? = null
        private var enable : Boolean = false

        fun setApplicationId(applicationId: String?): Builder {
            this.applicationId = applicationId
            return this
        }

        fun setBannerId(bannerId: String?): Builder {
            this.bannerId = bannerId
            return this
        }

        fun setBannerHomeId(bannerHomeId: String?): Builder {
            this.bannerHomeId = bannerHomeId
            return this
        }

        fun setInterstitialId(interstitialId: String?): Builder {
            this.interstitialId = interstitialId
            return this
        }

        fun setEnable(enable: Boolean): Builder {
            this.enable = enable
            return this
        }


        // Build function to create and save the ZaifSDKBuilder instance
        fun build(): FanSDKBuilder {
            val zaifSDKBuilder = FanSDKBuilder(
                applicationId, bannerId, bannerHomeId, interstitialId,enable)

            return zaifSDKBuilder
        }
    }

    companion object {
        // Get a Builder instance
        fun builder(context: Context): Builder {
            return Builder(context)
        }
    }
}

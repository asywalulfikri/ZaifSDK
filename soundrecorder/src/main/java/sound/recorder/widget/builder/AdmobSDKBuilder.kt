package sound.recorder.widget.builder

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import sound.recorder.widget.util.Constant

class AdmobSDKBuilder private constructor(
    val admobId : String?,
    val bannerId: String?,
    val interstitialId: String?,
    val rewardId: String?,
    val rewardInterstitialId: String?,
    val nativeId : String?,
    val appOpenId : String?,
    val orientationAds : Int?
) {

    // Builder class
    class Builder(private val context: Context) {
        private var admobId : String? =null
        private var bannerId: String? =null
        private var interstitialId: String? =null
        private var rewardId: String? =null
        private var rewardInterstitialId: String? =null
        private var nativeId : String? = null
        private var appOpenId : String? =null
        private var orientationAds : Int? =null

        fun setAdmobId(admobId: String?): Builder {
            this.admobId = admobId
            return this
        }

        fun setBannerId(bannerId: String?): Builder {
            this.bannerId = bannerId
            return this
        }

        fun setInterstitialId(interstitialId: String?): Builder {
            this.interstitialId = interstitialId
            return this
        }

        fun setRewardId(rewardId: String?): Builder {
            this.rewardId = rewardId
            return this
        }

        fun setRewardInterstitialId(rewardInterstitialId: String?): Builder {
            this.rewardInterstitialId = rewardInterstitialId
            return this
        }

        fun setNativeId(nativeId: String?): Builder {
            this.nativeId = nativeId
            return this
        }

        fun setAppOpenId(appOpenId: String?): Builder {
            this.appOpenId = appOpenId
            return this
        }

        fun setOrientationAds(orientationAds: Int?): Builder {
            this.orientationAds = orientationAds
            return this
        }


        // Build function to create and save the ZaifSDKBuilder instance
        fun build(): AdmobSDKBuilder {
            val zaifSDKBuilder = AdmobSDKBuilder(
                admobId, bannerId, interstitialId, rewardId, rewardInterstitialId,
                nativeId, appOpenId ,orientationAds)

            // Save the object to SharedPreferences as JSON
            saveToSharedPreferences(zaifSDKBuilder)

            return zaifSDKBuilder
        }

        private fun saveToSharedPreferences(zaifSDKBuilder: AdmobSDKBuilder) {
            val sharedPreferences: SharedPreferences = context.getSharedPreferences(
                Constant.KeyShared.shareKey,
                Context.MODE_PRIVATE
            )
            val editor = sharedPreferences.edit()

            // Convert the object to JSON and save it
            val gson = Gson()
            val json = gson.toJson(zaifSDKBuilder)
            editor.putString(Constant.KeyShared.admobSDKBuilder, json)
            Log.d("json_value", "$json--")
            editor.apply()
        }

        // Load the ZaifSDKBuilder from SharedPreferences
        fun loadFromSharedPreferences(): AdmobSDKBuilder? {
            val sharedPreferences: SharedPreferences = context.getSharedPreferences(
                Constant.KeyShared.shareKey,
                Context.MODE_PRIVATE
            )
            val gson = Gson()
            val json = sharedPreferences.getString(Constant.KeyShared.admobSDKBuilder, null)
            return if (json != null) {
                gson.fromJson(json, AdmobSDKBuilder::class.java)
            } else {
                null
            }
        }
    }

    companion object {
        // Get a Builder instance
        fun builder(context: Context): Builder {
            return Builder(context)
        }
    }
}

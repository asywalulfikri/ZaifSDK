package sound.recorder.widget.builder

import android.content.Context
import android.content.SharedPreferences
import sound.recorder.widget.util.Constant

class AdmobAdsBuilder private constructor(
    val admobId : String?,
    val bannerId: String?,
    val interstitialId: String?,
    val rewardId: String?,
    val rewardInterstitialId: String?,
    val nativeId : String?
) {

    // Builder class to construct MyObject
    class Builder(private val context: Context) {
        private var admobId : String? =null
        private var bannerId: String? =null
        private var interstitialId: String? =null
        private var rewardId: String? =null
        private var rewardInterstitialId: String? =null
        private var nativeId : String? = null

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

        // Build function to create an instance of MyObject
        fun build(): AdmobAdsBuilder {
            val myObject = AdmobAdsBuilder(admobId, bannerId, interstitialId,rewardId,rewardInterstitialId,nativeId)

            // Save values to SharedPreferences
            saveToSharedPreferences(myObject)

            return myObject
        }

        private fun saveToSharedPreferences(admobAdsBuilder: AdmobAdsBuilder) {
            val sharedPreferences: SharedPreferences = context.getSharedPreferences(
                Constant.KeyShared.shareKey,
                Context.MODE_PRIVATE
            )
            val editor = sharedPreferences.edit()

            editor.putString(Constant.KeyShared.admobId, admobAdsBuilder.admobId)
            editor.putString(Constant.KeyShared.admobBannerId, admobAdsBuilder.bannerId)
            editor.putString(Constant.KeyShared.admobInterstitialId, admobAdsBuilder.interstitialId)
            editor.putString(Constant.KeyShared.admobRewardId, admobAdsBuilder.rewardId)
            editor.putString(Constant.KeyShared.admobRewardInterstitialId, admobAdsBuilder.rewardInterstitialId)
            editor.putString(Constant.KeyShared.admobNativeId, admobAdsBuilder.nativeId)

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

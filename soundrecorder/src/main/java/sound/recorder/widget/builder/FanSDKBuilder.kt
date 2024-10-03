package sound.recorder.widget.builder

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import sound.recorder.widget.util.Constant

class FanSDKBuilder private constructor(
    val applicationId : String?,
    val bannerId: String?,
    val interstitialId: String?,
    val enable : Boolean
) {

    // Builder class
    class Builder(private val context: Context) {
        private var applicationId: String? = null
        private var bannerId: String? = null
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
                applicationId, bannerId, interstitialId,enable)

            // Save the object to SharedPreferences as JSON
            saveToSharedPreferences(zaifSDKBuilder)

            return zaifSDKBuilder
        }

        private fun saveToSharedPreferences(zaifSDKBuilder: FanSDKBuilder) {
            val sharedPreferences: SharedPreferences = context.getSharedPreferences(
                Constant.KeyShared.shareKey,
                Context.MODE_PRIVATE
            )
            val editor = sharedPreferences.edit()

            // Convert the object to JSON and save it
            val gson = Gson()
            val json = gson.toJson(zaifSDKBuilder)
            editor.putString(Constant.KeyShared.fanSDKBuilder, json)
            Log.d("json_value", "$json--")
            editor.apply()
        }

        // Load the ZaifSDKBuilder from SharedPreferences
        fun loadFromSharedPreferences(): FanSDKBuilder? {
            val sharedPreferences: SharedPreferences = context.getSharedPreferences(
                Constant.KeyShared.shareKey,
                Context.MODE_PRIVATE
            )
            val gson = Gson()
            val json = sharedPreferences.getString(Constant.KeyShared.fanSDKBuilder, null)
            return if (json != null) {
                gson.fromJson(json, FanSDKBuilder::class.java)
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

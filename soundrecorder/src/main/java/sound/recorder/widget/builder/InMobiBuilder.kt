package sound.recorder.widget.builder

import android.content.Context
import android.content.SharedPreferences
import sound.recorder.widget.util.Constant

class InMobiBuilder private constructor(
    val applicationId : String?,
    val bannerId: Long = 0,
    val interstitialId: Long = 0,
    val enable : Boolean) {

    // Builder class to construct MyObject
    class Builder(private val context: Context) {
        private var applicationId: String? = null
        private var bannerId: Long = 0
        private var interstitialId: Long = 0
        private var enable : Boolean = false

        fun setApplicationId(applicationId: String?): Builder {
            this.applicationId = applicationId
            return this
        }

        fun setBannerId(bannerId: Long): Builder {
            this.bannerId = bannerId
            return this
        }

        fun setInterstitialId(interstitialId: Long):Builder {
            this.interstitialId = interstitialId
            return this
        }

        fun setEnable(enable: Boolean): Builder {
            this.enable = enable
            return this
        }

        // Build function to create an instance of MyObject
        fun build(): InMobiBuilder {
            val myObject = InMobiBuilder(applicationId,bannerId,interstitialId,enable)

            // Save values to SharedPreferences
            saveToSharedPreferences(myObject)

            return myObject
        }

        private fun saveToSharedPreferences(inMobiBuilder: InMobiBuilder) {
            val sharedPreferences: SharedPreferences = context.getSharedPreferences(
                Constant.KeyShared.shareKey,
                Context.MODE_PRIVATE
            )
            val editor = sharedPreferences.edit()

            editor.putString(Constant.KeyShared.inMobiId, inMobiBuilder.applicationId)
            editor.putLong(Constant.KeyShared.inMobiBannerId, inMobiBuilder.bannerId)
            editor.putLong(Constant.KeyShared.inMobiInterstitialId, inMobiBuilder.interstitialId)
            editor.putBoolean(Constant.KeyShared.inMobiEnable,inMobiBuilder.enable)

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

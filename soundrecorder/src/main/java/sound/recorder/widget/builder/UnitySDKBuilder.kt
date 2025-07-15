package sound.recorder.widget.builder

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import sound.recorder.widget.util.Constant

class UnitySDKBuilder private constructor(
    val unityId : String?,
    var enable : Boolean?
) {

    // Builder class
    class Builder(private val context: Context) {
        private var unityId : String? =null
        private var testMode: Boolean? =null
        private var enable: Boolean? =null



        fun setUnityId(unityId: String?): Builder {
            this.unityId = unityId
            return this
        }

        fun setEnable(enable: Boolean): Builder {
            this.enable = enable
            return this
        }



        // Build function to create and save the ZaifSDKBuilder instance
        fun build(): UnitySDKBuilder {
            val zaifSDKBuilder = UnitySDKBuilder(
                unityId,enable)

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

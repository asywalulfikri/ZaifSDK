package sound.recorder.widget.builder

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import sound.recorder.widget.util.Constant

class UnitySDKBuilder private constructor(
    val unityId : String?,
    var testMode : Boolean?,
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

        fun setTestMode(testMode: Boolean?): Builder {
            this.testMode = testMode
            return this
        }

        fun setEnable(enable: Boolean): Builder {
            this.enable = enable
            return this
        }



        // Build function to create and save the ZaifSDKBuilder instance
        fun build(): UnitySDKBuilder {
            val zaifSDKBuilder = UnitySDKBuilder(
                unityId,testMode,enable)

            // Save the object to SharedPreferences as JSON
            saveToSharedPreferences(zaifSDKBuilder)

            return zaifSDKBuilder
        }

        private fun saveToSharedPreferences(zaifSDKBuilder: UnitySDKBuilder) {
            val sharedPreferences: SharedPreferences = context.getSharedPreferences(
                Constant.KeyShared.shareKey,
                Context.MODE_PRIVATE
            )
            val editor = sharedPreferences.edit()

            // Convert the object to JSON and save it
            val gson = Gson()
            val json = gson.toJson(zaifSDKBuilder)
            editor.putString(Constant.KeyShared.unitySDKBuilder, json)
            Log.d("json_value", "$json--")
            editor.apply()
        }

        // Load the ZaifSDKBuilder from SharedPreferences
        fun loadFromSharedPreferences(): UnitySDKBuilder? {
            val sharedPreferences: SharedPreferences = context.getSharedPreferences(
                Constant.KeyShared.shareKey,
                Context.MODE_PRIVATE
            )
            val gson = Gson()
            val json = sharedPreferences.getString(Constant.KeyShared.unitySDKBuilder, null)
            return if (json != null) {
                gson.fromJson(json, UnitySDKBuilder::class.java)
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

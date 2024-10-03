package sound.recorder.widget.builder

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import sound.recorder.widget.util.Constant

class ZaifSDKBuilder private constructor(
    val appName: String?,
    val versionCode: Int?,
    val versionName: String?,
    val applicationId: String?,
    val developerName: String?,
    val showNote: Boolean,
    val showChangeColor: Boolean,
    val backgroundWidgetColor: String,
    val showListSong: Boolean,
    val showVolume: Boolean
) {

    // Builder class
    class Builder(private val context: Context) {
        private var appName: String? = null
        private var versionCode: Int? = null
        private var versionName: String? = null
        private var applicationId: String? = null
        private var developerName: String? = null
        private var showNote = false
        private var showChangeColor = false
        private var backgroundWidgetColor: String? = null
        private var showListSong = false
        private var showVolume = false
        private var volumeMusic : Float? =null
        private var volumeInstrument : Float? =null

        fun setAppName(appName: String?): Builder {
            this.appName = appName
            return this
        }

        fun setVersionCode(versionCode: Int?): Builder {
            this.versionCode = versionCode
            return this
        }

        fun setVersionName(versionName: String?): Builder {
            this.versionName = versionName
            return this
        }

        fun setApplicationId(applicationId: String?): Builder {
            this.applicationId = applicationId
            return this
        }

        fun setDeveloperName(developerName: String?): Builder {
            this.developerName = developerName
            return this
        }

        fun showNote(showNote: Boolean): Builder {
            this.showNote = showNote
            return this
        }

        fun showChangeColor(showChangeColor: Boolean): Builder {
            this.showChangeColor = showChangeColor
            return this
        }

        fun setBackgroundWidgetColor(backgroundWidgetColor: String?): Builder {
            this.backgroundWidgetColor = backgroundWidgetColor
            return this
        }

        fun showListSong(showListSong: Boolean): Builder {
            this.showListSong = showListSong
            return this
        }

        fun showVolume(showVolume: Boolean): Builder {
            this.showVolume = showVolume
            return this
        }

        // Build function to create and save the ZaifSDKBuilder instance
        fun build(): ZaifSDKBuilder {
            val zaifSDKBuilder = ZaifSDKBuilder(
                appName, versionCode, versionName, applicationId, developerName,
                showNote, showChangeColor, backgroundWidgetColor ?: "#FFFFFF", showListSong, showVolume
            )

            // Save the object to SharedPreferences as JSON
            saveToSharedPreferences(zaifSDKBuilder)

            return zaifSDKBuilder
        }

        private fun saveToSharedPreferences(zaifSDKBuilder: ZaifSDKBuilder) {
            val sharedPreferences: SharedPreferences = context.getSharedPreferences(
                Constant.KeyShared.shareKey,
                Context.MODE_PRIVATE
            )
            val editor = sharedPreferences.edit()

            // Convert the object to JSON and save it
            val gson = Gson()
            val json = gson.toJson(zaifSDKBuilder)
            editor.putString(Constant.KeyShared.zaifSDKBuilder, json)
            Log.d("json_inserted ",json)
            editor.apply()
        }

        // Load the ZaifSDKBuilder from SharedPreferences
        fun loadFromSharedPreferences(): ZaifSDKBuilder? {
            val sharedPreferences: SharedPreferences = context.getSharedPreferences(
                Constant.KeyShared.shareKey,
                Context.MODE_PRIVATE
            )
            val gson = Gson()
            val json = sharedPreferences.getString(Constant.KeyShared.zaifSDKBuilder, null)
            return if (json != null) {
                gson.fromJson(json, ZaifSDKBuilder::class.java)
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

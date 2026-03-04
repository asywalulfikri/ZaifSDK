package sound.recorder.widget.builder

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import sound.recorder.widget.util.Constant

internal object ZaifSDKStorage {

    private val gson = Gson()

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(
            Constant.KeyShared.shareKey,
            Context.MODE_PRIVATE
        )

    @SuppressLint("UseKtx")
    fun save(context: Context, config: ZaifSDKConfig) {
        prefs(context)
            .edit()
            .putString(Constant.KeyShared.zaifSDKBuilder, gson.toJson(config))
            .apply()
    }

    fun load(context: Context): ZaifSDKConfig? {
        return prefs(context)
            .getString(Constant.KeyShared.zaifSDKBuilder, null)
            ?.let { runCatching { gson.fromJson(it, ZaifSDKConfig::class.java) }.getOrNull() }
    }

    @SuppressLint("UseKtx")
    fun clear(context: Context) {
        prefs(context)
            .edit()
            .remove(Constant.KeyShared.zaifSDKBuilder)
            .apply()
    }
}
package sound.recorder.widget.builder

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import sound.recorder.widget.util.Constant

internal object ZaifSDKStorage {

    private val gson = Gson()

    // In-memory cache untuk avoid async SharedPreferences delay
    @Volatile
    private var cachedConfig: ZaifSDKConfig? = null

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(
            Constant.KeyShared.shareKey,
            Context.MODE_PRIVATE
        )

    @SuppressLint("UseKtx")
    fun save(context: Context, config: ZaifSDKConfig) {
        // Simpan ke memory dulu (instant)
        cachedConfig = config

        // Simpan ke SharedPreferences (async, untuk persistensi)
        prefs(context)
            .edit()
            .putString(
                Constant.KeyShared.zaifSDKBuilder,
                gson.toJson(config)
            )
            .apply()
    }

    fun load(context: Context): ZaifSDKConfig? {
        // Return dari memory kalau ada (no I/O, instant)
        cachedConfig?.let { return it }

        // Fallback ke SharedPreferences (saat cold start / app restart)
        val json = prefs(context)
            .getString(Constant.KeyShared.zaifSDKBuilder, null)

        return json?.let {
            runCatching {
                gson.fromJson(it, ZaifSDKConfig::class.java)
            }.getOrNull()
        }?.also { cachedConfig = it } // cache hasil load dari disk
    }

    @SuppressLint("UseKtx")
    fun clear(context: Context) {
        cachedConfig = null // clear memory juga
        prefs(context)
            .edit()
            .remove(Constant.KeyShared.zaifSDKBuilder)
            .apply()
    }
}
package sound.recorder.widget.util

import android.content.Context
import android.content.SharedPreferences
import java.util.*

class LanguageManager(private val context: Context) {

    private val LANG_PREF_KEY = "language"
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)

    fun setLanguage(language: String) {
        val editor = sharedPreferences.edit()
        editor.putString(LANG_PREF_KEY, language)
        editor.apply()
    }

    fun getCurrentLanguage(): String {
        return sharedPreferences.getString(LANG_PREF_KEY, Locale.getDefault().language) ?: Locale.getDefault().language
    }

    fun updateLanguage() {
        val lang = getCurrentLanguage()
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val resources = context.resources
        val configuration = resources.configuration
        configuration.setLocale(locale)
        context.createConfigurationContext(configuration)
    }
}
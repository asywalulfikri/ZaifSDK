package sound.recorder.widget.testutil

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import java.io.File
import java.nio.file.Files
import java.util.Calendar

/**
 * Context palsu untuk unit test JVM: SharedPreferences di memori, tanpa Robolectric.
 * Butuh `testOptions.unitTests.returnDefaultValues = true` di build.gradle.
 */
class FakeContext : ContextWrapper(null) {

    private val prefs = mutableMapOf<String, FakePrefs>()
    private val files: File by lazy { Files.createTempDirectory("zaif-test").toFile() }

    override fun getApplicationContext(): Context = this

    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
        prefs.getOrPut(name) { FakePrefs() }

    override fun getFilesDir(): File = files

    override fun getCacheDir(): File = files

    override fun getPackageName(): String = "sound.recorder.widget.test"
}

/** SharedPreferences di memori; apply() dan commit() langsung tersimpan. */
class FakePrefs : SharedPreferences {
    private val data = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = data.toMutableMap()
    override fun getString(key: String, defValue: String?) = data[key] as String? ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: MutableSet<String>?) =
        data[key] as MutableSet<String>? ?: defValues
    override fun getInt(key: String, defValue: Int) = data[key] as Int? ?: defValue
    override fun getLong(key: String, defValue: Long) = data[key] as Long? ?: defValue
    override fun getFloat(key: String, defValue: Float) = data[key] as Float? ?: defValue
    override fun getBoolean(key: String, defValue: Boolean) = data[key] as Boolean? ?: defValue
    override fun contains(key: String) = data.containsKey(key)
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val changes = mutableMapOf<String, Any?>()
        private val removed = mutableSetOf<String>()
        private var clear = false

        override fun putString(key: String, value: String?) = apply { changes[key] = value }
        override fun putStringSet(key: String, values: MutableSet<String>?) = apply { changes[key] = values }
        override fun putInt(key: String, value: Int) = apply { changes[key] = value }
        override fun putLong(key: String, value: Long) = apply { changes[key] = value }
        override fun putFloat(key: String, value: Float) = apply { changes[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { changes[key] = value }
        override fun remove(key: String) = apply { removed += key }
        override fun clear() = apply { clear = true }
        override fun commit(): Boolean { apply(); return true }
        override fun apply() {
            // Urutan sama dengan Android: clear, lalu remove, lalu put.
            if (clear) data.clear()
            removed.forEach { data.remove(it) }
            changes.forEach { (k, v) -> if (v == null) data.remove(k) else data[k] = v }
        }
    }
}

/** Format hari yyyyDDD, sama dengan DailyRewards. */
fun dayOfYearId(offsetDays: Int = 0): Int {
    val c = Calendar.getInstance()
    if (offsetDays != 0) c.add(Calendar.DAY_OF_YEAR, offsetDays)
    return c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR)
}

/** Format tanggal yyyyMMdd, sama dengan jatah export dan video. */
fun dateId(offsetDays: Int = 0): Int {
    val c = Calendar.getInstance()
    if (offsetDays != 0) c.add(Calendar.DAY_OF_YEAR, offsetDays)
    return c.get(Calendar.YEAR) * 10_000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH)
}

const val HOUR_MS = 60 * 60 * 1000L
const val DAY_MS = 24 * HOUR_MS

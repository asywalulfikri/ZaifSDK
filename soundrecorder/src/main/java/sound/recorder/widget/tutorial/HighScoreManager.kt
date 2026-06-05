package sound.recorder.widget.tutorial

import android.content.Context

object HighScoreManager {
    private const val PREFS_NAME = "pianika_tiles_highscores"

    private fun key(songName: String) =
        "hs_${songName.trim().lowercase().replace(" ", "_")}"

    fun getHighScore(context: Context, songName: String): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(key(songName), 0)

    /** Simpan score jika lebih besar dari record lama. Return true jika rekor baru. */
    fun saveIfBetter(context: Context, songName: String, score: Int): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getInt(key(songName), 0)
        return if (score > current) {
            prefs.edit().putInt(key(songName), score).apply()
            true
        } else false
    }

    fun resetAll(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }
}

package sound.recorder.widget.util

import android.app.Dialog
import android.os.Build
import android.view.View
import android.view.Window
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Dialog punya jendela sendiri dan tidak mewarisi mode fullscreen Activity, jadi navigation bar
 * muncul tiap dialog terbuka (terlihat jelas di Android 11 dan di Realme/MIUI). Panggil sebelum
 * dialog ditampilkan agar bar tidak sempat muncul.
 */
fun Window.hideSystemBars() {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val controller = WindowCompat.getInsetsController(this, decorView)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            @Suppress("DEPRECATION")
            decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
    } catch (_: Exception) {
        // Murni tampilan; gagal menyembunyikan bar tidak boleh menggagalkan dialog.
    }
}

/** `Dialog(context).immersive()`: jendela dialog menyembunyikan status & navigation bar. */
fun <T : Dialog> T.immersive(): T {
    window?.hideSystemBars()
    return this
}

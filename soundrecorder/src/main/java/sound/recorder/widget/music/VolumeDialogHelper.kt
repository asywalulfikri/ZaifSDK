package sound.recorder.widget.music

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import sound.recorder.widget.R
import sound.recorder.widget.util.Constant
import java.lang.ref.WeakReference

object VolumeDialogHelper {

    // Dialog & view di-cache per-Context supaya tidak inflate layout XML ulang
    // setiap kali dialog dibuka (inflate berat = rawan kena GC stall di device low-end).
    // Semua dipegang lewat WeakReference — object ini singleton yang hidup selama proses app,
    // jadi kalau pakai strong reference, Activity/Fragment pemilik context bakal leak permanen.
    private var cachedDialogRef: WeakReference<AlertDialog>? = null
    private var cachedDialogViewRef: WeakReference<View>? = null
    private var cachedContextRef: WeakReference<Context>? = null

    private fun isContextInvalid(context: Context): Boolean {
        val activity = context as? Activity ?: return false
        return activity.isFinishing || activity.isDestroyed
    }

    fun showVolumeDialog(
        context: Context,
        currentVolumeAudio: Float,
        onAudioVolumeChanged: (Float) -> Unit
    ) {
        if (isContextInvalid(context)) return

        val sameContext = cachedContextRef?.get() === context
        val reusableDialog = if (sameContext) cachedDialogRef?.get() else null
        val reusableView = if (sameContext) cachedDialogViewRef?.get() else null

        val dialog: AlertDialog
        val dialogView: View

        if (reusableDialog != null && reusableView != null) {
            dialog = reusableDialog
            dialogView = reusableView
        } else {
            try {
                cachedDialogRef?.get()?.let { if (it.isShowing) it.dismiss() }
            } catch (_: Exception) {
                // Context lama sudah invalid (mis. Activity destroyed) — abaikan, dialog lama akan di-GC.
            }

            dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_volume_control, null)
            dialog = AlertDialog.Builder(context).setView(dialogView).create()
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

            cachedDialogRef = WeakReference(dialog)
            cachedDialogViewRef = WeakReference(dialogView)
            cachedContextRef = WeakReference(context)
        }

        if (dialog.isShowing) return

        val seekBarMusic = dialogView.findViewById<SmoothSeekBar>(R.id.seekBarMusic)
        val seekBarAudio = dialogView.findViewById<SmoothSeekBar>(R.id.seekBarAudio)

        val musicPrefs    = context.getSharedPreferences("music_player_prefs", Context.MODE_PRIVATE)
        val sharedPrefs   = context.getSharedPreferences("recordingWidget", Context.MODE_PRIVATE)

        // Set progress awal
        seekBarMusic.progress = (musicPrefs.getFloat("music_volume", 0.7f) * 100).toInt()
        seekBarAudio.progress = (currentVolumeAudio * 100).toInt()

        // Listener Volume Music (MP3) — pakai SmoothSeekBar.OnProgressChangeListener
        seekBarMusic.listener = object : SmoothSeekBar.OnProgressChangeListener {
            override fun onProgressChanged(progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val v = progress / 100f
                    MusicPlayerManager.setVolume(v, v)
                    musicPrefs.edit().putFloat("music_volume", v).apply()
                }
            }
            override fun onStartTrackingTouch() {}
            override fun onStopTrackingTouch()  {}
        }

        // Listener Volume Instrumen (SoundPool) — pakai SmoothSeekBar.OnProgressChangeListener
        seekBarAudio.listener = object : SmoothSeekBar.OnProgressChangeListener {
            override fun onProgressChanged(progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val v = progress / 100f
                    onAudioVolumeChanged(v)
                    sharedPrefs.edit().putFloat(Constant.KeyShared.volumeAudio, v).apply()
                }
            }
            override fun onStartTrackingTouch() {}
            override fun onStopTrackingTouch()  {}
        }

        try {
            dialog.show()
        } catch (_: Exception) {
            // Context/window sudah tidak valid saat mau ditampilkan — biarkan gagal secara silent
            // daripada crash (mis. Activity di-destroy tepat sebelum dialog show).
        }
    }
}
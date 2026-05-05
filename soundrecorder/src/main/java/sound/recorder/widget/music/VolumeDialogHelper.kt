package sound.recorder.widget.music

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import sound.recorder.widget.R
import sound.recorder.widget.util.Constant

object VolumeDialogHelper {

    fun showVolumeDialog(
        context: Context,
        currentVolumeAudio: Float,
        onAudioVolumeChanged: (Float) -> Unit
    ) {
        val dialogView   = LayoutInflater.from(context).inflate(R.layout.dialog_volume_control, null)
        val seekBarMusic = dialogView.findViewById<SmoothSeekBar>(R.id.seekBarMusic)
        val seekBarAudio = dialogView.findViewById<SmoothSeekBar>(R.id.seekBarAudio)

        val musicPrefs    = context.getSharedPreferences("music_player_prefs", Context.MODE_PRIVATE)
        val sharedPrefs   = context.getSharedPreferences("recordingWidget", Context.MODE_PRIVATE)

        // Set progress awal
        seekBarMusic.progress = (musicPrefs.getFloat("music_volume", 0.7f) * 100).toInt()
        seekBarAudio.progress = (currentVolumeAudio * 100).toInt()

        val dialog = AlertDialog.Builder(context).setView(dialogView).create()

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

        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }
}
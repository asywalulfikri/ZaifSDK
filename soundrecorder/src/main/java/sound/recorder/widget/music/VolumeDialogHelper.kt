package sound.recorder.widget.music


import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.widget.SeekBar
import androidx.appcompat.app.AlertDialog
import sound.recorder.widget.R
import sound.recorder.widget.util.Constant

object VolumeDialogHelper {

    fun showVolumeDialog(
        context: Context,
        currentVolumeAudio: Float,
        onAudioVolumeChanged: (Float) -> Unit
    ) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_volume_control, null)
        val seekBarMusic = dialogView.findViewById<SeekBar>(R.id.seekBarMusic)
        val seekBarAudio = dialogView.findViewById<SeekBar>(R.id.seekBarAudio)

        val musicPrefs = context.getSharedPreferences("music_player_prefs", Context.MODE_PRIVATE)
        val sharedPreferences = context.getSharedPreferences("recordingWidget", Context.MODE_PRIVATE)

        // Set Progress Awal
        seekBarMusic.progress = (musicPrefs.getFloat("music_volume", 0.7f) * 100).toInt()
        seekBarAudio.progress = (currentVolumeAudio * 100).toInt()

        val dialog = AlertDialog.Builder(context).setView(dialogView).create()

        // Listener Volume Music (MP3)
        seekBarMusic.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
                if (f) {
                    val v = p / 100f
                    MusicPlayerManager.setVolume(v, v)
                    musicPrefs.edit().putFloat("music_volume", v).apply()
                }
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        // Listener Volume Instrumen (SoundPool)
        seekBarAudio.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
                if (f) {
                    val v = p / 100f
                    // Kirim balik ke Fragment untuk update variabel local & ViewModel
                    onAudioVolumeChanged(v)

                    // Simpan ke SharedPreferences
                    sharedPreferences.edit().putFloat(Constant.KeyShared.volumeAudio, v).apply()
                }
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }
}
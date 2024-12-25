package sound.recorder.widget.util

import android.content.Context
import android.view.LayoutInflater
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sound.recorder.widget.R
import java.io.File
import java.lang.ref.WeakReference

class DialogUtils {

    fun showRecordDialog(
        context: Context,
        title: String,
        message: String,
        onYesClick: () -> Unit
    ) {
        try {
            val builder = AlertDialog.Builder(context)
            val dialogView = LayoutInflater.from(context).inflate(R.layout.custom_cancel_dialog, null)

            val tvDialogTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
            val tvDialogMessage = dialogView.findViewById<TextView>(R.id.tvDialogMessage)
            val btnNo = dialogView.findViewById<TextView>(R.id.btnNo)
            val btnYes = dialogView.findViewById<TextView>(R.id.btnYes)

            tvDialogTitle.text = title
            tvDialogMessage.text = message

            builder.setView(dialogView)
            val dialog = builder.create()

            btnYes.setOnClickListener {
                onYesClick()
                dialog.dismiss()
            }

            btnNo.setOnClickListener {
                dialog.dismiss()
            }

            dialog.show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun showCancelDialog(
        context: Context,
        title: String,
        message: String,
        dirPath: String,
        fileName: String,
        stopRecordingAudio: (String) -> Unit
    ) {
        try {
            val builder = AlertDialog.Builder(context)
            val dialogView = LayoutInflater.from(context).inflate(R.layout.custom_cancel_dialog, null)

            val tvDialogTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
            val tvDialogMessage = dialogView.findViewById<TextView>(R.id.tvDialogMessage)
            val btnNo = dialogView.findViewById<TextView>(R.id.btnNo)
            val btnYes = dialogView.findViewById<TextView>(R.id.btnYes)

            tvDialogTitle.text = title
            tvDialogMessage.text = message

            builder.setView(dialogView)
            val dialog = builder.create()

            btnYes.setOnClickListener {
                CoroutineScope(Dispatchers.IO).launch {
                    File(dirPath + fileName).delete()
                    withContext(Dispatchers.Main) {
                        stopRecordingAudio(context.getString(R.string.record_canceled))
                        dialog.dismiss()
                    }
                }
            }

            btnNo.setOnClickListener {
                dialog.dismiss()
            }

            dialog.show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun showVolumeDialog(
        context: Context,
        initialVolumeMusic: Float,
        initialVolumeAudio: Float,
        onVolumeMusicChanged: (Float) -> Unit,
        onVolumeAudioChanged: (Float) -> Unit
    ) {
        try {
            val builder = AlertDialog.Builder(context)
            val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_volume_control, null)

            val seekBarMusic = dialogView.findViewById<SeekBar>(R.id.seekBarMusic)
            val seekBarAudio = dialogView.findViewById<SeekBar>(R.id.seekBarAudio)

            seekBarMusic.progress = (initialVolumeMusic * 100).toInt()
            seekBarAudio.progress = (initialVolumeAudio * 100).toInt()

            seekBarMusic.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val newVolumeMusic = progress / 100f
                    onVolumeMusicChanged(newVolumeMusic)
                    CoroutineScope(Dispatchers.IO).launch {
                        DataSession(context).saveVolumeMusic(newVolumeMusic)
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })

            seekBarAudio.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val newVolumeAudio = progress / 100f
                    onVolumeAudioChanged(newVolumeAudio)
                    CoroutineScope(Dispatchers.IO).launch {
                        DataSession(context).saveVolumeAudio(newVolumeAudio)
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })

            builder.setView(dialogView)

            val dialog = builder.create()
            dialog.show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

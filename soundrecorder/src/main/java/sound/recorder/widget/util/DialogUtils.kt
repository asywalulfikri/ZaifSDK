package sound.recorder.widget.util

import android.content.Context
import android.view.LayoutInflater
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sound.recorder.widget.R
import sound.recorder.widget.music.VolumeDialogHelper
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
            val dialogScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            dialog.setOnDismissListener { dialogScope.cancel() }

            btnYes.setOnClickListener {
                dialogScope.launch(Dispatchers.IO) {
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
        // Dulu layout ini dibaca sebagai SeekBar padahal isinya SmoothSeekBar → ClassCastException
        // (tertangkap try/catch sehingga dialog tidak pernah muncul). Sekarang pakai dialog volume bersama.
        val session = DataSession(context)
        VolumeDialogHelper.show(
            context = context,
            musicVolume = initialVolumeMusic,
            audioVolume = initialVolumeAudio,
            onMusicChanged = { v ->
                onVolumeMusicChanged(v)
                session.saveVolumeMusic(v)
            },
            onAudioChanged = { v ->
                onVolumeAudioChanged(v)
                session.saveVolumeAudio(v)
            }
        )
    }
}

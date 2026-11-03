package sound.recorder.widget.tutorial

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Typeface
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import sound.recorder.widget.R
import sound.recorder.widget.recording.RecordingDialogPalette
import sound.recorder.widget.recording.RecordingDialogStyle
import sound.recorder.widget.recording.RecordingDialogViews.applyWindow
import sound.recorder.widget.recording.RecordingDialogViews.boxButton
import sound.recorder.widget.recording.RecordingDialogViews.rounded
import sound.recorder.widget.recording.RecordingDialogViews.sdp
import sound.recorder.widget.recording.RecordingDialogViews.textButton
import sound.recorder.widget.recording.RecordingDialogViews.vSpace
import sound.recorder.widget.recording.export.RecordingExportDialog
import sound.recorder.widget.recording.export.RecordingExporter
import java.io.File
import com.intuit.sdp.R as SdpR

/** Hasil export video: otomatis disimpan ke galeri, lalu bisa dibagikan seperti MP3. */
internal object RecordingVideoResultDialog {

    fun show(context: Context, file: File, title: String) {
        val activity = findActivity(context) ?: return
        if (activity.isFinishing || activity.isDestroyed) return

        val c = RecordingDialogPalette(context, RecordingDialogStyle.default)
        val dialog = AlertDialog.Builder(context).create()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        dialog.setOnDismissListener { scope.cancel() }
        val shareTitle = "$title.${file.extension}"

        val pad = context.sdp(SdpR.dimen._16sdp)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(context, c.surface, SdpR.dimen._12sdp)
            setPadding(pad, pad, pad, context.sdp(SdpR.dimen._8sdp))
        }
        root.addView(TextView(context).apply {
            text = context.getString(R.string.tutorial_video_done_title)
            setTextColor(c.text)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(context).apply {
            text = title
            setTextColor(c.textSecondary)
            textSize = 12f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        root.addView(vSpace(context, SdpR.dimen._10sdp))

        val statusTv = TextView(context).apply {
            textSize = 12f
            setTextColor(c.textSecondary)
        }
        root.addView(statusTv)
        root.addView(vSpace(context, SdpR.dimen._10sdp))

        lateinit var saveBtn: View

        fun save() {
            saveBtn.visibility = View.GONE
            statusTv.setTextColor(c.textSecondary)
            statusTv.text = context.getString(R.string.tutorial_video_saving)
            scope.launch {
                try {
                    if (!RecordingExportDialog.ensureStoragePermission(context)) {
                        statusTv.setTextColor(c.error)
                        statusTv.text = context.getString(R.string.export_permission_denied)
                        saveBtn.visibility = View.VISIBLE
                        return@launch
                    }
                    val location = RecordingExporter.saveVideoToDevice(context, file)
                    statusTv.setTextColor(c.success)
                    statusTv.text = "✓ " + context.getString(R.string.export_saved_to, location)
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    Log.e("TutorialVideo", "save failed", t)
                    statusTv.setTextColor(c.error)
                    statusTv.text = context.getString(R.string.export_failed)
                    saveBtn.visibility = View.VISIBLE
                }
            }
        }

        fun shareAll() {
            scope.launch { RecordingExporter.shareVideo(context, file, shareTitle) }
        }

        val actionRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val buttonHeight = context.sdp(SdpR.dimen._36sdp)
        val gap = context.sdp(SdpR.dimen._8sdp)
        actionRow.addView(
            boxButton(context, c, context.getString(R.string.share), primary = true) { shareAll() },
            LinearLayout.LayoutParams(0, buttonHeight, 1f)
        )
        actionRow.addView(View(context), LinearLayout.LayoutParams(gap, 1))
        actionRow.addView(
            boxButton(context, c, "WhatsApp", primary = false) {
                scope.launch {
                    if (!RecordingExporter.shareVideoToWhatsApp(context, file, shareTitle)) {
                        statusTv.setTextColor(c.error)
                        statusTv.text = context.getString(R.string.export_whatsapp_not_installed)
                        RecordingExporter.shareVideo(context, file, shareTitle)
                    }
                }
            },
            LinearLayout.LayoutParams(0, buttonHeight, 1f)
        )
        saveBtn = boxButton(context, c, context.getString(R.string.export_save_device), primary = false) { save() }
        actionRow.addView(saveBtn, LinearLayout.LayoutParams(0, buttonHeight, 1f).apply { marginStart = gap })
        root.addView(actionRow)

        root.addView(vSpace(context, SdpR.dimen._8sdp))
        root.addView(LinearLayout(context).apply {
            gravity = Gravity.END
            addView(textButton(context, c, context.getString(R.string.close)).apply {
                setOnClickListener { dialog.dismiss() }
            })
        }, LinearLayout.LayoutParams(-1, -2))

        dialog.setCanceledOnTouchOutside(false)
        dialog.setView(root)
        dialog.show()
        applyWindow(dialog, context, 0.90, 0.50)
        save()
    }

    private tailrec fun findActivity(context: Context?): Activity? = when (context) {
        is Activity -> context
        is ContextWrapper -> findActivity(context.baseContext)
        else -> null
    }
}

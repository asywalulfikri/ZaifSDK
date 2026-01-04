package sound.recorder.widget.util

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.Log
import android.view.LayoutInflater
import android.view.Window
import android.widget.ProgressBar
import android.widget.TextView
import sound.recorder.widget.R

/**
 * Utility untuk menampilkan Progress Dialog yang aman dari Memory Leak dan Window Manager Crash.
 */
object ProgressDialogUtil {

    private var progressDialog: AlertDialog? = null

    /**
     * Mengambil Activity dari Context, menangani ContextWrapper jika perlu.
     */
    private fun getActivity(context: Context): Activity? {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    @SuppressLint("UseKtx")
    fun show(context: Context) {
        val activity = getActivity(context) ?: return

        // Cek apakah Activity masih hidup
        if (activity.isFinishing || activity.isDestroyed) return

        // Jika dialog sudah tampil, tidak perlu buat baru
        if (progressDialog?.isShowing == true) return

        try {
            val dialogView = LayoutInflater.from(activity)
                .inflate(R.layout.dialog_progress, null, false)

            progressDialog = AlertDialog.Builder(activity)
                .setView(dialogView)
                .setCancelable(false)
                .create().apply {
                    setCanceledOnTouchOutside(false)
                    requestWindowFeature(Window.FEATURE_NO_TITLE)
                    window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                    show()
                }
        } catch (e: Exception) {
            Log.e("ProgressDialogUtil", "Gagal menampilkan dialog: ${e.message}")
        }
    }

    @SuppressLint("SetTextI18n")
    fun update(progress: Int) {
        try {
            val dialog = progressDialog ?: return
            if (!dialog.isShowing) return

            val progressBar = dialog.findViewById<ProgressBar>(R.id.progressBarDialog)
            val tvProgress = dialog.findViewById<TextView>(R.id.tvProgressDialog)

            progressBar?.progress = progress
            tvProgress?.text = "Loading $progress%"
        } catch (e: Exception) {
            Log.e("ProgressDialogUtil", "Gagal update progress: ${e.message}")
        }
    }

    fun dismiss() {
        val dialog = progressDialog ?: return

        try {
            // Cek window != null untuk memastikan view masih menempel pada WindowManager
            if (dialog.isShowing && dialog.window != null) {
                val context = dialog.context
                val activity = getActivity(context)

                // Hanya dismiss jika Activity masih aktif
                if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                    dialog.dismiss()
                }
            }
        } catch (e: IllegalArgumentException) {
            // Menangkap error "View not attached to window manager" secara spesifik
            Log.w("ProgressDialogUtil", "Dialog sudah tidak menempel pada window")
        } catch (e: Exception) {
            Log.e("ProgressDialogUtil", "Error saat dismiss: ${e.message}")
        } finally {
            // PENTING: Set null agar tidak terjadi memory leak
            progressDialog = null
        }
    }
}
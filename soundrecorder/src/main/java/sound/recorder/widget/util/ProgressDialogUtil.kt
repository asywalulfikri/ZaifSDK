package sound.recorder.widget.util

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.Window
import android.widget.ProgressBar
import android.widget.TextView
import sound.recorder.widget.R

object ProgressDialogUtil {
    private var progressDialog: AlertDialog? = null

    @SuppressLint("UseKtx")
    fun show(context: Context) {
        if (progressDialog?.isShowing == true) return

        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_progress, null)

        progressDialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setCancelable(false)
            .create().apply {
                setCanceledOnTouchOutside(false)
                requestWindowFeature(Window.FEATURE_NO_TITLE)
                window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            }

        progressDialog?.show()
    }

    @SuppressLint("SetTextI18n")
    fun update(progress: Int) {
        val progressBar = progressDialog?.findViewById<ProgressBar>(R.id.progressBarDialog)
        val tvProgress = progressDialog?.findViewById<TextView>(R.id.tvProgressDialog)

        progressBar?.progress = progress
        tvProgress?.text = "Loading $progress%"
    }

    fun dismiss() {
        progressDialog?.dismiss()
        progressDialog = null
    }
}

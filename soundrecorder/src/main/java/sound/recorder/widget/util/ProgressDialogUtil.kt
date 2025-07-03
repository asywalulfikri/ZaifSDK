package sound.recorder.widget.util

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.widget.ProgressBar
import android.widget.TextView
import sound.recorder.widget.R

object ProgressDialogUtil {
    private var progressDialog: AlertDialog? = null

    fun show(context: Context) {
        if (progressDialog?.isShowing == true) return

        val builder = AlertDialog.Builder(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_progress, null)
        builder.setView(view)
        builder.setCancelable(false)
        progressDialog = builder.create()
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

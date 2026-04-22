package sound.recorder.widget.music

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.Window
import android.widget.Button
import android.widget.TextView
import sound.recorder.widget.R

object InstrumentDialogHelper {

    @SuppressLint("UseKtx", "SetTextI18n")
    fun showUnlockDialog(
        context: Context,
        title: String,
        onAccept: () -> Unit
    ) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        // Inflate custom layout
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_unlock_instrument, null)
        dialog.setContentView(view)

        // Atur agar background dialog transparan (supaya radius layout kita kelihatan)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val tvTitle = view.findViewById<TextView>(R.id.tvTitle)
        val tvMessage = view.findViewById<TextView>(R.id.tvMessage)
        val btnWatch = view.findViewById<TextView>(R.id.btnWatch)
        val btnCancel = view.findViewById<TextView>(R.id.btnCancel)

        // Set teks sesuai resource & instrumen
        tvTitle.text = "${context.getString(R.string.open)} $title".uppercase()
        tvMessage.text = "${context.getString(R.string.watch_ads)} $title?"
        btnWatch.text = context.getString(R.string.watch).uppercase()
        btnCancel.text = context.getString(R.string.cancel).uppercase()

        btnWatch.setOnClickListener {
            onAccept()
            dialog.dismiss()
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }


    @SuppressLint("UseKtx", "SetTextI18n")
    fun showBuyAdsDialog(
        context: Context,
        onAccept: () -> Unit
    ) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        // Inflate custom layout
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_unlock_instrument, null)
        dialog.setContentView(view)

        // Atur agar background dialog transparan (supaya radius layout kita kelihatan)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val tvTitle = view.findViewById<TextView>(R.id.tvTitle)
        val tvMessage = view.findViewById<TextView>(R.id.tvMessage)
        val btnWatch = view.findViewById<Button>(R.id.btnWatch)
        val btnCancel = view.findViewById<Button>(R.id.btnCancel)

        // Set teks sesuai resource & instrumen
        tvTitle.text = context.getString(R.string.remove_ads)
        tvMessage.text = context.getString(R.string.are_you_buy_no_ads)
        btnWatch.text = context.getString(R.string.watch).uppercase()
        btnCancel.text = context.getString(R.string.cancel).uppercase()

        btnWatch.setOnClickListener {
            onAccept()
            dialog.dismiss()
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }
}
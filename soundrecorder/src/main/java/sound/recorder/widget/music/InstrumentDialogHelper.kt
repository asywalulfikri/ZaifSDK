package sound.recorder.widget.music

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.Window
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import sound.recorder.widget.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object InstrumentDialogHelper {

    @SuppressLint("UseKtx", "SetTextI18n")
    fun showUnlockDialog(
        context: Context,
        title: String,
        onAccept: () -> Unit
    ) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val view = LayoutInflater.from(context).inflate(R.layout.dialog_unlock_instrument, null)
        dialog.setContentView(view)

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val tvTitle = view.findViewById<TextView>(R.id.tvTitle)
        val tvMessage = view.findViewById<TextView>(R.id.tvMessage)
        val btnWatch = view.findViewById<TextView>(R.id.btnWatch)
        val btnCancel = view.findViewById<TextView>(R.id.btnCancel)

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

        val view = LayoutInflater.from(context).inflate(R.layout.dialog_unlock_instrument, null)
        dialog.setContentView(view)

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val tvTitle = view.findViewById<TextView>(R.id.tvTitle)
        val tvMessage = view.findViewById<TextView>(R.id.tvMessage)
        val btnWatch = view.findViewById<TextView>(R.id.btnWatch)
        val btnCancel = view.findViewById<TextView>(R.id.btnCancel)

        tvTitle.text = context.getString(R.string.remove_ads)
        tvMessage.text = context.getString(R.string.are_you_buy_no_ads)
        btnWatch.text = context.getString(R.string.buy).uppercase() // Diganti jadi BUY biasanya
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

    /**
     * Dialog untuk memasukkan nama rekaman sebelum disimpan.
     * Menggunakan callback onSave untuk mengirimkan nama yang diinput user.
     */
    @SuppressLint("UseKtx", "SetTextI18n")
    fun showSaveRecordDialog(
        context: Context,
        onSave: (name: String) -> Unit
    ) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_save_record, null)
        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val tvTitle = view.findViewById<TextView>(R.id.tvTitle)
        val etName = view.findViewById<EditText>(R.id.tvMessage) // Nanti kita sembunyikan atau ganti jadi EditText
        val btnSave = view.findViewById<TextView>(R.id.btnWatch)
        val btnCancel = view.findViewById<TextView>(R.id.btnCancel)

        tvTitle.text = context.getString(R.string.saved_recording_ask).uppercase()
        btnSave.text = context.getString(R.string.save).uppercase()
        val defaultName = context.getString(R.string.recording)+" "+"${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())}"
        etName.setText(defaultName)

        btnSave.text = context.getString(R.string.save).uppercase()
        btnCancel.text = context.getString(R.string.cancel).uppercase()

        btnSave.setOnClickListener {
            val name = etName.text.toString().ifEmpty { defaultName }
            onSave(name)
            dialog.dismiss()
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    @SuppressLint("UseKtx", "SetTextI18n")
    fun showRecordDialog(
        context: Context,
        onAccept: () -> Unit
    ) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val view = LayoutInflater.from(context).inflate(R.layout.dialog_unlock_instrument, null)
        dialog.setContentView(view)

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val tvTitle = view.findViewById<TextView>(R.id.tvTitle)
        val tvMessage = view.findViewById<TextView>(R.id.tvMessage)
        val btnWatch = view.findViewById<TextView>(R.id.btnWatch)
        val btnCancel = view.findViewById<TextView>(R.id.btnCancel)

        tvTitle.text = context.getString(R.string.record)
        tvMessage.text = context.getString(R.string.title_recording_dialog)
        btnWatch.text = context.getString(R.string.yes).uppercase()
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
    fun showRecordChooseDialog(
        context: Context,
        onAccept: (useMic: Boolean) -> Unit
    ) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val view = LayoutInflater.from(context).inflate(R.layout.dialog_start_record, null)
        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val btnInstrumentOnly = view.findViewById<TextView>(R.id.btnInstrumentOnly)
        val btnWithMic = view.findViewById<TextView>(R.id.btnWithMic)
        val btnCancel = view.findViewById<TextView>(R.id.btnCancel)

        // Klik: Rekam Alat Musik Saja
        btnInstrumentOnly.setOnClickListener {
            onAccept(false)
            dialog.dismiss()
        }

        // Klik: Rekam dengan Mic
        btnWithMic.setOnClickListener {
            onAccept(true)
            dialog.dismiss()
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    @SuppressLint("UseKtx", "SetTextI18n")
    fun showCancelRecordDialog(
        context: Context,
        onAccept: () -> Unit
    ) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val view = LayoutInflater.from(context).inflate(R.layout.dialog_unlock_instrument, null)
        dialog.setContentView(view)

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val tvTitle = view.findViewById<TextView>(R.id.tvTitle)
        val tvMessage = view.findViewById<TextView>(R.id.tvMessage)
        val btnWatch = view.findViewById<TextView>(R.id.btnWatch)
        val btnCancel = view.findViewById<TextView>(R.id.btnCancel)

        tvTitle.text = context.getString(R.string.record)
        tvMessage.text = context.getString(R.string.title_recording_canceled)
        btnWatch.text = context.getString(R.string.yes).uppercase()
        btnCancel.text = context.getString(R.string.no).uppercase()

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
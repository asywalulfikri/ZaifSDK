package sound.recorder.widget.music

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
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

        val view = LayoutInflater.from(context).inflate(R.layout.dialog_unlock_instrument, null)
        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val tvTitle   = view.findViewById<TextView>(R.id.tvTitle)
        val tvMessage = view.findViewById<TextView>(R.id.tvMessage)
        val btnWatch  = view.findViewById<TextView>(R.id.btnWatch)
        val btnCancel = view.findViewById<TextView>(R.id.btnCancel)

        tvTitle.text   = "${context.getString(R.string.open)} $title".uppercase()
        tvMessage.text = "${context.getString(R.string.watch_ads)} $title?"
        btnWatch.text  = context.getString(R.string.watch).uppercase()
        btnCancel.text = context.getString(R.string.cancel).uppercase()

        btnWatch.setOnClickListener  { onAccept(); dialog.dismiss() }
        btnCancel.setOnClickListener { dialog.dismiss() }

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

        val tvTitle   = view.findViewById<TextView>(R.id.tvTitle)
        val tvMessage = view.findViewById<TextView>(R.id.tvMessage)
        val btnWatch  = view.findViewById<TextView>(R.id.btnWatch)
        val btnCancel = view.findViewById<TextView>(R.id.btnCancel)

        tvTitle.text   = context.getString(R.string.remove_ads)
        tvMessage.text = context.getString(R.string.are_you_buy_no_ads)
        btnWatch.text  = context.getString(R.string.buy).uppercase()
        btnCancel.text = context.getString(R.string.cancel).uppercase()

        btnWatch.setOnClickListener  { onAccept(); dialog.dismiss() }
        btnCancel.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    /**
     * Dialog untuk memasukkan nama rekaman sebelum disimpan.
     * onSave   → user klik Save   (nama rekaman dikirim)
     * onCancel → user klik Cancel (rekaman di-discard)
     */
    @SuppressLint("UseKtx", "SetTextI18n")
    fun showSaveRecordDialog(
        context: Context,
        onSave: (name: String) -> Unit,
        onCancel: (() -> Unit)? = null       // ← nullable, backward-compatible
    ) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_save_record, null)
        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val tvTitle   = view.findViewById<TextView>(R.id.tvTitle)
        val etName    = view.findViewById<EditText>(R.id.tvMessage)
        val btnSave   = view.findViewById<TextView>(R.id.btnWatch)
        val btnCancel = view.findViewById<TextView>(R.id.btnCancel)

        tvTitle.text   = context.getString(R.string.saved_recording_ask).uppercase()
        btnSave.text   = context.getString(R.string.save).uppercase()
        btnCancel.text = context.getString(R.string.cancel).uppercase()
        etName.setText("")
        etName.hint = context.getString(R.string.input_record_title)

        btnSave.setOnClickListener {
            val name = etName.text.toString().trim()
            when {
                name.isEmpty() -> {
                    etName.error = context.getString(R.string.name_min_length)
                    Toast.makeText(context, context.getString(R.string.title_cannot_be_empty), Toast.LENGTH_SHORT).show()
                }
                name.length <= 5 -> {
                    etName.error = context.getString(R.string.name_min_length)
                    Toast.makeText(context, context.getString(R.string.name_min_length), Toast.LENGTH_SHORT).show()
                }
                else -> {
                    onSave(name)
                    dialog.dismiss()
                }
            }
        }

        btnCancel.setOnClickListener {
            onCancel?.invoke()
            dialog.dismiss()
        }

        dialog.show()
        val dm = context.resources.displayMetrics
        dialog.window?.setLayout((dm.widthPixels * 0.50).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
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

        val tvTitle   = view.findViewById<TextView>(R.id.tvTitle)
        val tvMessage = view.findViewById<TextView>(R.id.tvMessage)
        val btnWatch  = view.findViewById<TextView>(R.id.btnWatch)
        val btnCancel = view.findViewById<TextView>(R.id.btnCancel)

        tvTitle.text   = context.getString(R.string.record)
        tvMessage.text = context.getString(R.string.title_recording_dialog)
        btnWatch.text  = context.getString(R.string.yes).uppercase()
        btnCancel.text = context.getString(R.string.cancel).uppercase()

        btnWatch.setOnClickListener  { onAccept(); dialog.dismiss() }
        btnCancel.setOnClickListener { dialog.dismiss() }

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
        val btnWithMic        = view.findViewById<TextView>(R.id.btnWithMic)
        val btnCancel         = view.findViewById<TextView>(R.id.btnCancel)

        btnInstrumentOnly.setOnClickListener { onAccept(false); dialog.dismiss() }
        btnWithMic.setOnClickListener        { onAccept(true);  dialog.dismiss() }
        btnCancel.setOnClickListener         { dialog.dismiss() }

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

        val tvTitle   = view.findViewById<TextView>(R.id.tvTitle)
        val tvMessage = view.findViewById<TextView>(R.id.tvMessage)
        val btnWatch  = view.findViewById<TextView>(R.id.btnWatch)
        val btnCancel = view.findViewById<TextView>(R.id.btnCancel)

        tvTitle.text   = context.getString(R.string.record)
        tvMessage.text = context.getString(R.string.title_recording_canceled)
        btnWatch.text  = context.getString(R.string.yes).uppercase()
        btnCancel.text = context.getString(R.string.no).uppercase()

        btnWatch.setOnClickListener  { onAccept(); dialog.dismiss() }
        btnCancel.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }
}
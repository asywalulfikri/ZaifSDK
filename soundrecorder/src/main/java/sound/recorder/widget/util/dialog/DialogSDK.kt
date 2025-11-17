package sound.recorder.widget.util.dialog

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import sound.recorder.widget.R
import sound.recorder.widget.util.AppRatingHelper
import sound.recorder.widget.util.Constant

class DialogSDK(
    private val context: Context,
    private val dialogType : String,
    private val action: (Boolean) -> Unit
) : DialogFragment() {

    @SuppressLint("UseGetLayoutInflater", "SetTextI18n", "UseKtx")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_sdk, null)
        val tvTitle    = dialogView.findViewById<TextView>(R.id.tvTitle)
        val tvContent  = dialogView.findViewById<TextView>(R.id.tvContent)
        val tvAction   = dialogView.findViewById<TextView>(R.id.tvAction)
        val tvCancel   = dialogView.findViewById<TextView>(R.id.tvCancel)


        if(dialogType== Constant.DialogType.RATING){
            tvTitle.text = context.getString(R.string.rate_us) +" ⭐⭐⭐⭐⭐"
            tvContent.text = context.getString(R.string.rating_content)
            tvAction.text = context.getString(R.string.rate)
            tvCancel.text = context.getString(R.string.later)

            tvAction.setOnClickListener {
                action(true)
                dismiss()
                AppRatingHelper(context).openRating()
            }

        }else if(dialogType== Constant.DialogType.ADD_SONG){
            tvTitle.text = context.getString(R.string.how_to_add_song_title) +"\uD83C\uDFB5"
            tvContent.text = context.getString(R.string.how_to_add_song_content)

            tvAction.setOnClickListener {
                action(true)
                dismiss()
            }

            tvCancel.visibility = View.GONE
        }
        tvCancel.setOnClickListener {
            action(false)
            dismiss()
        }



        return AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .create().apply {
                setCanceledOnTouchOutside(true)
                requestWindowFeature(Window.FEATURE_NO_TITLE)
                window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            }
    }
}

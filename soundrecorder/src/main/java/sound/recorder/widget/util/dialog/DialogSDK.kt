package sound.recorder.widget.util.dialog

import android.annotation.SuppressLint
import android.app.Dialog
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

class DialogSDK : DialogFragment() {

    private var dialogType: String? = null
    private var action: ((Boolean) -> Unit)? = null

    companion object {
        private const val ARG_DIALOG_TYPE = "arg_dialog_type"

        fun newInstance(
            dialogType: String,
            action: (Boolean) -> Unit
        ): DialogSDK {
            return DialogSDK().apply {
                arguments = Bundle().apply {
                    putString(ARG_DIALOG_TYPE, dialogType)
                }
                this.action = action
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dialogType = arguments?.getString(ARG_DIALOG_TYPE)
    }

    // FIX: helper agar dismiss tidak crash setelah onSaveInstanceState
    private fun safeDismiss() {
        if (isAdded && !isStateSaved) {
            dismiss()
        } else {
            dismissAllowingStateLoss()
        }
    }

    @SuppressLint("UseKtx", "SetTextI18n", "UseGetLayoutInflater")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_sdk, null)

        val tvTitle   = dialogView.findViewById<TextView>(R.id.tvTitle)
        val tvContent = dialogView.findViewById<TextView>(R.id.tvContent)
        val tvAction  = dialogView.findViewById<TextView>(R.id.tvAction)
        val tvCancel  = dialogView.findViewById<TextView>(R.id.tvCancel)

        when (dialogType) {
            Constant.DialogType.RATING -> {
                tvTitle.text = getString(R.string.rate_us) + " ⭐⭐⭐⭐⭐"
                tvContent.text = getString(R.string.rating_content)
                tvAction.text = getString(R.string.rate)
                tvCancel.text = getString(R.string.later)

                tvAction.setOnClickListener {
                    if (!isAdded) return@setOnClickListener  // FIX: guard
                    action?.invoke(true)
                    safeDismiss()
                    AppRatingHelper(requireContext()).openRating()
                }
            }

            Constant.DialogType.ADD_SONG -> {
                tvTitle.text = getString(R.string.how_to_add_song_title) + " 🎵"
                tvContent.text = getString(R.string.how_to_add_song_content)

                tvAction.setOnClickListener {
                    if (!isAdded) return@setOnClickListener  // FIX: guard
                    action?.invoke(true)
                    safeDismiss()
                }

                tvCancel.visibility = View.GONE
            }
        }

        tvCancel.setOnClickListener {
            if (!isAdded) return@setOnClickListener  // FIX: guard
            action?.invoke(false)
            safeDismiss()
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

    // FIX: clear action saat fragment di-destroy untuk hindari memory leak
    override fun onDestroyView() {
        super.onDestroyView()
        action = null
    }
}
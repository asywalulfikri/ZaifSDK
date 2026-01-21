package sound.recorder.widget.ui.bottomSheet

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import sound.recorder.widget.R
import sound.recorder.widget.notes.Note

class ActionsDialogFragment(
    private val position: Int,
    private val notesList: List<Note>,
    private val callback: ActionCallback
) : DialogFragment() {

    interface ActionCallback {
        fun onUse(note: Note)
        fun onEdit(note: Note, position: Int)
        fun onDelete(position: Int)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.FullScreenDialog)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val activity = requireActivity()
        val useNote = activity.getString(R.string.use_note)
        val editNote = activity.getString(R.string.edit_note)
        val deleteNote = activity.getString(R.string.delete_not)

        return AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.choose))
            .setItems(arrayOf(useNote, editNote, deleteNote)) { _, which ->
                when (which) {
                    0 -> callback.onUse(notesList[position])
                    1 -> callback.onEdit(notesList[position], position)
                    2 -> callback.onDelete(position)
                }
                dismiss()
            }
            .create()
    }

    override fun onStart() {
        super.onStart()

        try {
            dialog?.window?.let { window ->
                // Fullscreen ukuran
                window.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                // Background transparan (opsional)
                window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

                hideSystemUI(window)
            }
        }catch (e : Exception){

        }

    }

    private fun hideSystemUI(window: Window) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val controller = window.insetsController
                controller?.let {
                    it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                    it.systemBarsBehavior =
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility =
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                            View.SYSTEM_UI_FLAG_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            }
        }catch (e : Exception){

        }

    }

}

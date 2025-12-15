package sound.recorder.widget.ui.bottomSheet

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.textfield.TextInputEditText
import sound.recorder.widget.R
import sound.recorder.widget.ui.fragment.FragmentVideo
import java.io.File


class BottomSheet(private var dirPath: String? =null, private var filename: String? =null, private var listener: OnClickListener? =null) : BottomSheetDialogFragment() {

    // Step 1 - This interface defines the type of messages I want to communicate to my owner
    interface OnClickListener {
        // These methods are the different events and
        // need to pass relevant arguments related to the event triggered
        fun onCancelClicked()
        fun onOkClicked(filePath: String, filename: String, isChange : Boolean)
    }

    private var isChange = false

    companion object {
        fun newInstance(): BottomSheet {
            return BottomSheet()
        }
    }


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.bottom_sheet, container)
        val editText = view.findViewById<TextInputEditText>(R.id.filenameInput)

        if(activity!=null&&context!=null){

            (dialog as? BottomSheetDialog)?.behavior?.state = STATE_EXPANDED

            dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

            if(isDarkTheme()){
                editText.setTextColor(Color.parseColor("#000000"))
            }

            // set edittext to filename
            filename = filename.toString().split(".mp3")[0]
            editText.setText(filename)

            // showKeyboard(editText)

            // deal with OK button
            view.findViewById<Button>(R.id.okBtn).setOnClickListener {
                // hide keyboard
                hideKeyboard(view)

                // update filename if need
                val updatedFilename = editText.text.toString()
                if(updatedFilename != filename){
                    isChange = true
                    /* val newFile = File("$dirPath$updatedFilename.mp3")
                     File(dirPath+filename).renameTo(newFile)
                     Log.d("namaya",dirPath+filename)
                     Log.d("namayaTO",dirPath+updatedFilename)*/
                }

                // add entry to db

                // dismiss dialog
                dismiss()

                // fire ok callback
                listener?.onOkClicked("$dirPath$updatedFilename.mp3", updatedFilename,isChange)
            }

            // deal with cancel button
            view.findViewById<Button>(R.id.cancelBtn).setOnClickListener {
                // hide keyboard
                hideKeyboard(view)
                // delete file from storage
                File(dirPath+filename).delete()

                // dismiss dialog
                dismiss()

                // fire cancel callback
                listener?.onCancelClicked()
            }
        }

        return view

    }

    private fun isDarkTheme(): Boolean {
        return activity?.resources?.configuration?.uiMode!! and
                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }



    private fun showKeyboard(view: View) {
        if (view.requestFocus()) {
            val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
            imm?.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
        }
    }

    private fun hideKeyboard(view: View) {
        val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    override fun onStart() {
        super.onStart()
        applyImmersiveMode()
    }

    private fun applyImmersiveMode() {
        val window = dialog?.window ?: return
        val decorView = window.decorView

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // ANDROID 11+
            WindowCompat.setDecorFitsSystemWindows(window, false)

            val controller = WindowCompat.getInsetsController(window, decorView)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

            controller.hide(WindowInsetsCompat.Type.systemBars())

        } else {
            // ANDROID 10 DAN DI BAWAH
            @Suppress("DEPRECATION")
            decorView.systemUiVisibility =
                (View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        }
    }

}
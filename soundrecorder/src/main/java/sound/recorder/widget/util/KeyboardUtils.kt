package sound.recorder.widget.util

import android.content.Context
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager

object KeyboardUtils {

    /**
     * Show the soft keyboard for a specific view.
     *
     * @param view The view that should receive focus.
     */
    fun showKeyboard(view: View) {
        try {
            if (view.requestFocus()) {
                val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
                imm?.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
            }
        } catch (e: Exception) {
            Log.e("KeyboardUtils", "Error showing keyboard: ${e.message}")
        }
    }

    /**
     * Hide the soft keyboard for a specific view.
     *
     * @param view The view whose keyboard should be hidden.
     */
    fun hideKeyboard(view: View) {
        try {
            val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
            imm?.hideSoftInputFromWindow(view.windowToken, 0)
        } catch (e: Exception) {
            Log.e("KeyboardUtils", "Error hiding keyboard: ${e.message}")
        }
    }
}
package sound.recorder.widget.colorpicker

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.Window
import androidx.appcompat.app.AppCompatDialog


class CustomDialog(context: Context?, private val view: View) :
    AppCompatDialog(context!!) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(view)
    }

    init {
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
    }
}
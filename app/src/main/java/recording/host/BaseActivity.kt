package recording.host

import android.annotation.SuppressLint
import android.os.Bundle
import sound.recorder.widget.base.BaseActivityWidget


@SuppressLint("Registered")
open class BaseActivity : BaseActivityWidget(){


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }
}
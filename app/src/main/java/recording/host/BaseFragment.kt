package recording.host

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.animation.Animation
import sound.recorder.widget.base.BaseFragmentWidget


@SuppressLint("Registered")
open class BaseFragment : BaseFragmentWidget(){

    var myAnim: Animation? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }
}
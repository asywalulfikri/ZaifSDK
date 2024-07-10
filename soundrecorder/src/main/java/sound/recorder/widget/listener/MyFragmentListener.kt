package sound.recorder.widget.listener

import androidx.fragment.app.Fragment

object MyFragmentListener {
    private var myListener: FragmentListener? = null

    fun setMyListener(listener: FragmentListener?=null) {
        myListener = listener
    }
    fun openFragment(fragment : Fragment?) {
        myListener?.openFragment(fragment)
    }

}
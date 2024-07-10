package sound.recorder.widget

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import sound.recorder.widget.databinding.LayoutEmptyVerticalBinding
import sound.recorder.widget.ui.fragment.NoteFragmentFirebase
import sound.recorder.widget.ui.fragment.VoiceRecordFragmentVertical

class RecordWidgetBAckup : LinearLayout {

    private var fragmentManagers: FragmentManager? =null
    private val imkasFragment = NoteFragmentFirebase()
    private var isAdd = false
    private var binding: LayoutEmptyVerticalBinding


    constructor(_context: Context) : super(_context) {
        fragmentManagers = (_context as FragmentActivity).supportFragmentManager
        binding = LayoutEmptyVerticalBinding.inflate(LayoutInflater.from(context))
    }

    constructor(_context: Context, attributeSet: AttributeSet?) : super(_context, attributeSet) {
        fragmentManagers = (_context as FragmentActivity).supportFragmentManager
        binding = LayoutEmptyVerticalBinding.inflate(LayoutInflater.from(context))
        addView(binding.root)
    }

    fun loadData(){
        if(isAdd){
            removeAllViews()
            resetView()
        }else{
            setupViews()
        }
    }

    private fun setupViewsAgain(){
        isAdd = true
        fragmentManagers?.beginTransaction()?.replace(binding.recordWidgetVertical.id, imkasFragment)?.commit()
        addView(binding.root)
    }


    private fun setupViews(){
        fragmentManagers?.beginTransaction()?.replace(binding.recordWidgetVertical.id, imkasFragment)?.commitAllowingStateLoss()
        if(!imkasFragment.isAdded){
            addView(binding.recordWidgetVertical)
            isAdd = true
        }else{
            removeAllViews()
        }

    }

    private fun resetView(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            fragmentManagers?.beginTransaction()?.detach(imkasFragment)?.commitAllowingStateLoss()
            fragmentManagers?.beginTransaction()?.attach(imkasFragment)?.commitNow();
        } else {
            fragmentManagers?.beginTransaction()?.detach(imkasFragment)?.attach(imkasFragment)?.commitAllowingStateLoss()
        }
        addView(binding.recordWidgetVertical)
    }

    fun setToast(message : String){
        Toast.makeText(context, "$message.",Toast.LENGTH_SHORT).show()
    }



}
package recording.host

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import androidx.fragment.app.activityViewModels
import recording.host.databinding.FragmentExampleBinding
import sound.recorder.widget.base.BaseFragmentWidget
import sound.recorder.widget.ui.viewmodel.MusicViewModel
import sound.recorder.widget.util.Toastic
import kotlin.getValue

open class FragmentTesting : BaseFragmentWidget() {

    private var binding: FragmentExampleBinding? = null

    private val musicViewModel: MusicViewModel by activityViewModels()

    companion object {
        fun newInstance(): FragmentTesting {
            return FragmentTesting()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        newInstance()
        val b = Bundle()
        super.onCreate(b)
    }


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): RelativeLayout? {
        binding = FragmentExampleBinding.inflate(inflater, container, false)
        return binding?.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if(!musicViewModel.isInitialized) {
            setToastTic(Toastic.SUCCESS,musicViewModel.isInitialized.toString())
            musicViewModel.initializeIfNeeded()
        }else{
            setToastTic(Toastic.SUCCESS,musicViewModel.isInitialized.toString())
        }

        musicViewModel.init.observe(viewLifecycleOwner) { isInit ->
            isInit?.let {
                if(isInit){

                }else{
                }
            }
        }

    }

    override fun onPause() {
        super.onPause()
       // musicViewModel.marqueeScrollX = speedMarquee.textScroller?.currX ?: 0
       // speedMarquee.pauseScroll()
    }



}
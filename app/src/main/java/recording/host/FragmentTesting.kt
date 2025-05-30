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
import sound.recorder.widget.listener.CompleteMarqueeListener
import sound.recorder.widget.listener.MyCompleteMarqueeListener
import sound.recorder.widget.ui.viewmodel.MusicViewModel
import sound.recorder.widget.util.Toastic
import kotlin.getValue

open class FragmentTesting : BaseFragmentWidget(), CompleteMarqueeListener {

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

        MyCompleteMarqueeListener.setMyListener(this)

        musicViewModel.init.observe(viewLifecycleOwner) { isInit ->
            isInit?.let {
                if(isInit){

                }else{
                }
            }
        }


        musicViewModel.setNote.observe(viewLifecycleOwner) { note->
            if (note.toString().isNotEmpty()) {
                binding?.tvSpeedPlus?.visibility = View.VISIBLE
                binding?.tvSpeedMin?.visibility = View.VISIBLE
                binding?.tvRunningText?.text = note
                binding?.tvRunningText?.startScroll()
            } else {
                try {
                    binding?.tvRunningText?.text = activity?.getString(sound.recorder.widget.R.string.text_choose_not)
                } catch (e: Exception) {
                    setLog(e.message.toString())
                }
                binding?.tvRunningText?.pauseScroll()
                binding?.tvSpeedPlus?.visibility = View.GONE
                binding?.tvSpeedMin?.visibility = View.GONE
            }
        }

        binding?.tvSpeedPlus?.setOnClickListener {
            try {
                binding?.tvSpeedMin?.visibility = View.VISIBLE
                binding?.tvRunningText?.setSpeed(binding?.tvRunningText?.getSpeed()!! + 25.0f)
            } catch (e: Exception) {
                setToastError(activity, e.message.toString())
            }
        }

        binding?.tvSpeedMin?.setOnClickListener {
            try {
                binding?.tvRunningText?.setSpeed(binding?.tvRunningText?.getSpeed()!! - 50.0f)
                if (binding?.tvRunningText?.getSpeed() == 25.0f) {
                    binding?.tvSpeedMin?.visibility = View.GONE
                }
            } catch (e: Exception) {
                setToastError(activity, e.message.toString())
            }
        }

    }

    override fun onPause() {
        super.onPause()
       // musicViewModel.marqueeScrollX = speedMarquee.textScroller?.currX ?: 0
       // speedMarquee.pauseScroll()
    }

    override fun onDestroy() {
        super.onDestroy()
        MyCompleteMarqueeListener.setMyListener(null)
    }

    override fun onCompleteTextMarquee() {
        try {
            requireActivity().runOnUiThread {
                binding?.tvRunningText?.text = requireContext().getString(sound.recorder.widget.R.string.text_choose_not)
                binding?.tvSpeedPlus?.visibility  = View.GONE
                binding?.tvSpeedMin?.visibility = View.GONE
            }
        }catch (e : Exception){
            setLog(e.message)
        }
    }



}
package recording.host

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import androidx.activity.OnBackPressedCallback
import androidx.navigation.fragment.findNavController
import recording.host.databinding.FragmentHomeBinding
import sound.recorder.widget.base.BaseFragmentWidget
import sound.recorder.widget.listener.MyAdsListener
import sound.recorder.widget.util.Toastic
import kotlin.system.exitProcess
import kotlin.toString

class HomeFragment : BaseFragmentWidget() {

    private var binding: FragmentHomeBinding? =null

    companion object {
        fun newInstance(): HomeFragment {
            return HomeFragment()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): RelativeLayout? {
        binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding?.root

    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        onViewAds(false)
        setupButtonClick()


    }

    private fun setupButtonClick(){

        binding?.btnNext?.setOnClickListener {
            try {
                MyAdsListener.setBannerHome(true)
                findNavController().navigate(R.id.action_home_fragment_to_dholak_fragment)
            } catch (e: Exception) {
                setToast(e.message.toString())
            }
        }

        binding?.btnSetting?.setOnClickListener {
            try {
                MyAdsListener.setBannerHome(false)
                findNavController().navigate(R.id.action_home_fragment_to_setting_fragment)
            } catch (e: Exception) {
                setToastTic(Toastic.ERROR, e.message.toString())
            }
        }

        binding?.btnRating?.setOnClickListener {
            try {
                MyAdsListener.setBannerHome(false)
                rating()
            } catch (e: Exception) {
                setToastTic(Toastic.ERROR, e.message.toString())
            }
        }


    }

    fun onViewAds(show : Boolean){
        MyAdsListener.setBannerHome(show)
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

}

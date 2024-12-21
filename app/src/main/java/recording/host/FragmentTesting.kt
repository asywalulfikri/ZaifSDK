package recording.host

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import recording.host.databinding.FragmentExampleBinding
import sound.recorder.widget.base.BaseFragmentWidget
open class FragmentTesting : BaseFragmentWidget() {

    private var binding: FragmentExampleBinding? = null


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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentExampleBinding.inflate(inflater, container, false)

        setupBanner(binding?.bannerView)


        return binding?.root
    }

}
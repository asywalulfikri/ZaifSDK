package recording.host

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import recording.host.databinding.FragmentSplashscreenBinding
import sound.recorder.widget.base.BaseFragmentWidget
import sound.recorder.widget.listener.MyAdsListener

@SuppressLint("CustomSplashScreen")
class SplashScreenFragment : BaseFragmentWidget() {

    private var _binding: FragmentSplashscreenBinding? = null
    private val binding get() = _binding!!

    private val soundViewModel: SoundViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSplashscreenBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        observeViewModel()
        MyAdsListener.setHideAllBanner()
    }

    @SuppressLint("SetTextI18n")
    private fun observeViewModel() {
        soundViewModel.loadingProgress.observe(viewLifecycleOwner) { progress ->
            binding.progressBar.progress = progress
            binding.tvProgress.text =requireContext().getString(sound.recorder.widget.R.string.loading) + " "+progress +"%"
        }

        soundViewModel.isAllSoundsLoaded.observe(viewLifecycleOwner) { allLoaded ->
            if (allLoaded && isAdded) {
               // val intent = Intent(activity, MainActivity::class.java)
               // startActivity(intent)
               findNavController().navigate(R.id.action_splash_to_home)
            }
        }



        binding.tvVersion.text = "V"+BuildConfig.VERSION_NAME
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

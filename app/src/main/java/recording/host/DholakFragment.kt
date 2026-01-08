package recording.host

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import recording.host.databinding.ActivityDholakBinding
import sound.recorder.widget.listener.MusicListener
import sound.recorder.widget.listener.MyAdsListener
import sound.recorder.widget.listener.MyMusicListener
import sound.recorder.widget.ui.viewmodel.MusicViewModel
import sound.recorder.widget.util.Constant
import sound.recorder.widget.util.DataSession
import sound.recorder.widget.util.ProgressDialogUtil

class DholakFragment : BaseFragment(),
    MusicListener,
    SharedPreferences.OnSharedPreferenceChangeListener {

    private var _binding: ActivityDholakBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MusicViewModel by activityViewModels()
    private val soundViewModel: SoundViewModel by activityViewModels()

    private var volumeAudio = 1.0f
    private val type = "dholak"

    private var backCallback: OnBackPressedCallback? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ActivityDholakBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        observeViewModel()
        setupBackPressed()
        setupPreferences()
        setupUI()
        setupListeners()
    }

    private fun setupUI() {
        myAnim = AnimationUtils.loadAnimation(requireContext(), R.anim.button)

        val background = DataSession(requireContext()).getBackgroundColor()
        if (background != -1) {
            binding.layoutBackground.setBackgroundColor(
                getSharedPreferenceUpdate().getInt(Constant.KeyShared.backgroundColor, -1)
            )
        }

        initAnim(binding.ivStop)
    }

    private fun setupListeners() {
        MyMusicListener.setMyListener(this)

        binding.ivStop.setOnClickListener {
            viewModel.stopMusic()
        }

        buttonClick()

        viewModel.isPlaying.observe(viewLifecycleOwner) { isPlaying ->
            if (isPlaying) {
                binding.ivStop.visibility = View.VISIBLE
                startAnimation()
            } else {
                stopAnimation()
            }
        }
    }

    private fun setupBackPressed() {
        backCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                isEnabled = false
                MyAdsListener.setBannerHome(false)
                MyAdsListener.setOnShowInterstitial()
                findNavController().navigateUp()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            backCallback!!
        )
    }

    private fun setupPreferences() {
        sharedPreferences = DataSession(requireContext()).getShared()
        sharedPreferences?.registerOnSharedPreferenceChangeListener(this)

        volumeAudio =
            sharedPreferences?.getFloat(Constant.KeyShared.volumeAudio, 1.0f) ?: 1.0f
        soundViewModel.setVolume(volumeAudio)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buttonClick() {
        fun play(view: View, sound: String) {
            view.startAnimation(myAnim)
            soundViewModel.playSound(type, sound)
        }

        binding.rlDholak1.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_DOWN) play(binding.rlDholak1, "dholak1")
            true
        }

        binding.dohakTengah1.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_DOWN) play(binding.rlDholak1, "dholak2")
            true
        }

        binding.dohakAtas1.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_DOWN) play(binding.rlDholak1, "dholak3")
            true
        }

        binding.dohakKiri1.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_DOWN) play(binding.rlDholak1, "dholak4")
            true
        }

        binding.dohakKanan1.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_DOWN) play(binding.rlDholak1, "dholak5")
            true
        }

        binding.dohakBawah1.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_DOWN) play(binding.rlDholak1, "dholak6")
            true
        }
    }

    private fun startAnimation() {
        binding.ivStop.startAnimation(mPanAnim)
    }

    private fun stopAnimation() {
        binding.ivStop.clearAnimation()
        binding.ivStop.visibility = View.GONE
    }

    override fun onVolumeAudio(volume: Float?) {
        volume?.let { soundViewModel.setVolume(it) }
    }



    override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
        // 1. Validasi key
        if (key != Constant.KeyShared.backgroundColor) return

        // 2. Pastikan Fragment masih aktif & binding ada
        if (!isAdded) return
        val b = binding ?: return

        // 3. Ambil value dengan aman
        val color = prefs?.getInt(Constant.KeyShared.backgroundColor, -1) ?: return

        // 4. Update UI
        b.layoutBackground.setBackgroundColor(color)

        // 5. Update status bar (pastikan Activity masih ada)
    }

    private fun getSharedPreferenceUpdate(): SharedPreferences { return DataSession(requireContext()).getSharedUpdate() }

    private fun observeViewModel() {
        ProgressDialogUtil.show(requireContext())

        soundViewModel.loadingProgress.observe(viewLifecycleOwner) {
            ProgressDialogUtil.update(it)
        }

        soundViewModel.isAllSoundsLoaded.observe(viewLifecycleOwner) {
            if (it) ProgressDialogUtil.dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        // 🔥 INI WAJIB
        MyMusicListener.setMyListener(null)
        backCallback?.remove()
        sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        ProgressDialogUtil.dismiss()

        _binding = null
    }
}

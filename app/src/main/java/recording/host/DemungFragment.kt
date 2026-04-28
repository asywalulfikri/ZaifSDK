package recording.host

import android.Manifest
import android.content.SharedPreferences
import android.graphics.Color
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import recording.host.databinding.FragmentDemungBinding
import sound.recorder.widget.R
import sound.recorder.widget.listener.CompleteMarqueeListener
import sound.recorder.widget.listener.MusicListener
import sound.recorder.widget.listener.MyAdsListener
import sound.recorder.widget.listener.MyCompleteMarqueeListener
import sound.recorder.widget.listener.MyMusicListener
import sound.recorder.widget.listener.MyNoteListener
import sound.recorder.widget.listener.NoteListener
import sound.recorder.widget.music.MusicListDialogHelper
import sound.recorder.widget.music.MusicPlayerManager
import sound.recorder.widget.music.VolumeDialogHelper
import sound.recorder.widget.recording.InstrumentControlPanel
import sound.recorder.widget.recording.database.RecordedTap
import sound.recorder.widget.ui.viewmodel.MusicViewModel
import sound.recorder.widget.util.Constant
import sound.recorder.widget.util.DataSession
import sound.recorder.widget.util.ProgressDialogUtil
import sound.recorder.widget.util.SpeedMarquee

class DemungFragment : BaseFragment(), SharedPreferences.OnSharedPreferenceChangeListener,
    NoteListener, MusicListener, CompleteMarqueeListener,
    InstrumentControlPanel.InstrumentControlListener {

    private var binding: FragmentDemungBinding? = null
    private val viewModel: MusicViewModel by activityViewModels()
    private val soundViewModel: SoundViewModel by activityViewModels()
    private var volumeAudio: Float = 1.0f

    private val instrumentType = "demung"
    private val typePelog = "_pelog"
    private val typeSlendro = "_slendro"

    private var rewardedAdListener: InstrumentControlPanel.AdRequestListener? = null

    // ─── 1. REGISTER PERMISSION MIC ───
    private val requestPermissionMic = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            binding?.controlPanel?.startRecording(true)
        } else {
            setToast(requireContext().getString(R.string.allow_permission_audio_mic))
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentDemungBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (context != null && activity != null) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            setupBackNavigation()
            observeViewModel()

            if (!viewModel.isInitialized) {
                viewModel.initializeIfNeeded()
                setupInitialData()
            }

            setupControlPanel()
            setupTouchListener()
            setupMarquee()
            musicStatus()

            activity?.volumeControlStream = AudioManager.STREAM_MUSIC
        }
    }

    // ─── 2. MARQUEE SETUP ───
    private fun setupMarquee() {
        // Listener ketika scroll selesai sampai ujung
        binding?.marqueeControl?.setOnScrollCompleteListener(object : SpeedMarquee.OnScrollCompleteListener {
            override fun onScrollComplete() {
                binding?.marqueeControl?.clear()
            }
        })
    }

    // ─── 3. CONTROL PANEL SETUP ───
    private fun setupControlPanel() {
        val b = binding ?: return

        binding?.controlPanel?.onRequestAudioPermissionMic = {
            requestPermissionMic.launch(Manifest.permission.RECORD_AUDIO)
        }

        binding?.controlPanel?.onRequestAudioPermissionAudio = {
            val permission = if (Build.VERSION.SDK_INT >= 33)
                Manifest.permission.READ_MEDIA_AUDIO
            else
                Manifest.permission.READ_EXTERNAL_STORAGE

            requestPermissionMusic.launch(permission)
        }

        val style = InstrumentControlPanel.ControlConfig(
            textColor = Color.parseColor("#FFFFFF"),
            btnColor = Color.parseColor("#3D2510"),
            strokeColor = Color.parseColor("#9B6A14")
        )

        b.controlPanel.setup(instrumentType, LinearLayout.VERTICAL, style, this)
        b.controlPanel.setVolumeButtonVisible(true)

        rewardedAdListener = object : InstrumentControlPanel.AdRequestListener {
            override fun onShowRewardedAd(type: String, onComplete: () -> Unit) {
                val act = activity as? GameActivity
                if (act == null || !isAdded) return

                act.showRewardedAd(soundViewModel.isPremium) {
                    val currentBinding = binding
                    if (isAdded && currentBinding != null) {
                        onComplete()
                        if (type == "music") soundViewModel.isMusicUnlocked = true
                        else if (type == "list_record") soundViewModel.isRecordUnlocked = true

                        currentBinding.controlPanel.setUnlockedStatus(
                            soundViewModel.isPremium,
                            soundViewModel.isMusicUnlocked,
                            soundViewModel.isRecordUnlocked
                        )
                    }
                }
            }
        }

        b.controlPanel.adRequestListener = rewardedAdListener
        b.controlPanel.setUnlockedStatus(
            soundViewModel.isPremium,
            soundViewModel.isMusicUnlocked,
            soundViewModel.isRecordUnlocked
        )
    }

    // ─── 4. INSTRUMENT CONTROL LISTENER ───
    override fun onMuteControl(mute: Boolean) {
        if (mute) {
            SoundPlayUtils.setVolume(0.0f)
            soundViewModel.setVolume(0.0f)
        } else {
            val currentVol = if (volumeAudio > 0) volumeAudio else 1.0f
            SoundPlayUtils.setVolume(currentVol)
            soundViewModel.setVolume(currentVol)
        }
    }

    override fun onRecordStatusChanged(isRecording: Boolean) {
        if (isRecording) {
            setToast(getString(R.string.record_started))
        } else {
            SoundPlayUtils.setVolume(volumeAudio)
            soundViewModel.setVolume(volumeAudio)
        }
    }

    override fun onStopAll() {
        SoundPlayUtils.setVolume(volumeAudio)
        soundViewModel.setVolume(volumeAudio)
        MusicPlayerManager.stop()
    }

    override fun onPlaybackEvent(event: RecordedTap) {
        val b = binding ?: return
        val metadata = event.metadata ?: ""

        if (metadata == instrumentType + typeSlendro) b.instrumentView.setSelectedTab(1)
        else b.instrumentView.setSelectedTab(0)

        b.instrumentView.triggerHitAnimation(event.padIndex)

        val typeKey = metadata.ifEmpty { instrumentType + typePelog }
        SoundPlayUtils.playSound(typeKey, "type${event.padIndex + 1}")
    }

    override fun onVolume() {

        VolumeDialogHelper.showVolumeDialog(requireContext(), volumeAudio) { newVolume ->
            // Update variabel di Fragment
            volumeAudio = newVolume

            // Update SoundPool & ViewModel
            SoundPlayUtils.setVolume(newVolume)
            soundViewModel.setVolume(newVolume)
        }
    }

    // ─── 5. TOUCH & NAVIGATION ───
    private fun setupTouchListener() {
        binding?.instrumentView?.onBilahHitListener = { index, metadata ->
            binding?.controlPanel?.recordEvent(index, metadata)
        }
    }

    private fun setupBackNavigation() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    try {
                        (activity as? GameActivity)?.showInterstitialIfAllowed(soundViewModel.isPremium) {
                            if (!isAdded) return@showInterstitialIfAllowed
                            isEnabled = false
                            MyAdsListener.setBannerHome(false)
                            findNavController().navigateUp()
                        }
                    } catch (e: Exception) {
                        findNavController().navigateUp()
                    }
                }
            })
    }

    // ─── 6. SETUP & LIFECYCLE ───
    private fun setupInitialData() {
        try {
            sharedPreferences = activity?.let { DataSession(it).getShared() }
            sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
            setBottomStatusColor(R.color.color_yellow)
            loadSharedPreferencesAudio()
            initListener()
            initAnim(binding?.ivStop)
        } catch (e: Exception) {
            setLog(e.message.toString())
        }
    }

    private fun loadSharedPreferencesAudio() {
        lifecycleScope.launch(Dispatchers.IO) {
            volumeAudio = sharedPreferences?.getFloat(Constant.KeyShared.volumeAudio, 1.0f) ?: 1.0f
            SoundPlayUtils.setVolume(volumeAudio)
        }
    }

    fun musicStatus() {
       musicStatusManager(binding?.ivStop)
    }

    override fun onDestroyView() {
        SoundPlayUtils.setVolume(volumeAudio)
        soundViewModel.setVolume(volumeAudio)

        binding?.controlPanel?.let { panel ->
            panel.adRequestListener = null
            panel.releaseAndStop()
        }
        rewardedAdListener = null

        // Cleanup marquee via MarqueeControlView
        binding?.marqueeControl?.apply {
            setOnScrollCompleteListener(null)
            pauseScroll()
        }

        MusicListDialogHelper.statusListener = null
        viewLifecycleOwner.lifecycleScope.coroutineContext.cancelChildren()
        sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        ProgressDialogUtil.dismiss()

        viewModel.isInitialized = false
        binding = null
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        try {
            sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
            // Restore posisi scroll marquee
            binding?.marqueeControl?.startScrollAfterUpdate(viewModel.marqueeLastScrollX)
        } catch (e: Exception) { setLog(e.message) }
    }

    override fun onPause() {
        super.onPause()
        try {
            sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
            // Simpan posisi scroll marquee
            viewModel.marqueeLastScrollX = binding?.marqueeControl?.getCurrentScrollX() ?: 0
            binding?.marqueeControl?.pauseScroll()
        } catch (e: Exception) { setLog(e.message) }
    }

    private fun initListener() {
        MyAdsListener.loadInterstitial()
        MyAdsListener.loadReward()
        MyNoteListener.setMyListener(this)
        MyMusicListener.setMyListener(this)
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, k: String?) {
        if (k == Constant.KeyShared.backgroundColor) {
            val c = DataSession(requireActivity()).getSharedUpdate().getInt(
                Constant.KeyShared.backgroundColor, -1
            )
            binding?.layoutBackground?.setBackgroundColor(c)
        }
    }

    override fun onVolumeAudio(v: Float?) {
        v?.let {
            volumeAudio = it
            SoundPlayUtils.setVolume(it)
            soundViewModel.setVolume(it)
        }
    }


    // onCompleteTextMarquee tidak perlu lagi karena MarqueeControlView
    // sudah handle clear otomatis via setOnScrollCompleteListener di setupMarquee()
    override fun onCompleteTextMarquee() {}

    private fun observeViewModel() {
        soundViewModel.loadingProgress.observe(viewLifecycleOwner) { ProgressDialogUtil.update(it) }
        soundViewModel.isAllSoundsLoaded.observe(viewLifecycleOwner) { if (it && isAdded) ProgressDialogUtil.dismiss() }
    }

    override fun onNoteCallback(n: String?) {
        if (!isAdded) return
        viewLifecycleOwner.lifecycleScope.launch {
            // Cukup satu baris — MarqueeControlView handle tombol +/- dan clear otomatis
            binding?.marqueeControl?.setText(n.toString())
        }
    }

    override fun onDestroy() {
        MyMusicListener.setMyListener(null)
        MyNoteListener.setMyListener(null)
        MyCompleteMarqueeListener.setMyListener(null)
        super.onDestroy()
    }
}
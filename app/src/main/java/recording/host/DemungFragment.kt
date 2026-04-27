package recording.host

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.SeekBar
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
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
import sound.recorder.widget.recording.InstrumentControlPanel
import sound.recorder.widget.recording.InstrumentRecorderManager
import sound.recorder.widget.recording.database.RecordedTap
import sound.recorder.widget.recording.database.RecordingEntity
import sound.recorder.widget.ui.viewmodel.MusicViewModel
import sound.recorder.widget.util.Constant
import sound.recorder.widget.util.DataSession
import sound.recorder.widget.util.ProgressDialogUtil
import kotlin.apply

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
    private var layoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null

    private var rewardedAdListener: InstrumentControlPanel.AdRequestListener? = null

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

            // Setup navigasi back (Stop playback sebelum iklan)
            setupBackNavigation()

            observeViewModel()

            if (!viewModel.isInitialized) {
                viewModel.initializeIfNeeded()
                setupInitialData()
            }

            setupControlPanel()
            setupTouchListener()
            musicStatus()

            activity?.volumeControlStream = AudioManager.STREAM_MUSIC
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
                        setLog(e.message.toString())
                        findNavController().navigateUp()
                    }
                }
            })

    }

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

    private fun setupTouchListener() {
        binding?.instrumentView?.onBilahHitListener = { index, metadata ->
            binding?.controlPanel?.recordEvent(index, metadata)
        }

        binding?.tvSpeedPlus?.setOnClickListener {
            val b = binding ?: return@setOnClickListener
            val speed = b.tvRunningText.getSpeed()

            b.tvSpeedMin.visibility = View.VISIBLE
            b.tvRunningText.setSpeed(speed + 25f)
        }

        binding?.tvSpeedMin?.setOnClickListener {
            val b = binding ?: return@setOnClickListener
            val speed = b.tvRunningText.getSpeed()

            val newSpeed = (speed - 50f).coerceAtLeast(25f)
            b.tvRunningText.setSpeed(newSpeed)

            if (newSpeed == 25f) {
                b.tvSpeedMin.visibility = View.GONE
            }
        }

        binding?.tvClear?.setOnClickListener {
            val b = binding ?: return@setOnClickListener

            b.tvRunningText.clear()
            b.tvSpeedPlus.visibility = View.GONE
            b.tvSpeedMin.visibility = View.GONE
            b.tvClear.visibility = View.GONE
        }
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

    override fun onResume() {
        super.onResume()
        try {
            sharedPreferences?.registerOnSharedPreferenceChangeListener(this)

            // Perbaikan GlobalLayoutListener agar tidak leak
            val runningText = binding?.tvRunningText ?: return
            layoutListener = object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    val vto = binding?.tvRunningText?.viewTreeObserver
                    if (vto?.isAlive == true) {
                        vto.removeOnGlobalLayoutListener(this)
                    }
                    layoutListener = null
                    binding?.tvRunningText?.startScrollAfterUpdate(viewModel.marqueeLastScrollX)
                }
            }
            runningText.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
        } catch (e: Exception) {
            setLog(e.message)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)

            // Lepas listener saat pause untuk mencegah kebocoran
            layoutListener?.let {
                binding?.tvRunningText?.viewTreeObserver?.removeOnGlobalLayoutListener(it)
                layoutListener = null
            }

            viewModel.marqueeLastScrollX = binding?.tvRunningText?.textScroller?.currX ?: 0
            binding?.tvRunningText?.pauseScroll()
        } catch (e: Exception) {
            setLog(e.message)
        }
    }

    override fun onDestroyView() {
        // 3. PUTUS HUBUNGAN REWARD SECARA PAKSA (CRITICAL)
        // Kita null-kan listener di View DAN variabel class
        binding?.controlPanel?.let { panel ->
            panel.adRequestListener = null
            panel.releaseAndStop()
        }
        rewardedAdListener = null

        // Cleanup Marquee & Singleton
        binding?.tvRunningText?.apply {
            setOnScrollCompleteListener(null)
            pauseScroll()
        }
        MusicListDialogHelper.statusListener = null

        // Cleanup Coroutine & SharedPreferences
        viewLifecycleOwner.lifecycleScope.coroutineContext.cancelChildren()
        sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)

        ProgressDialogUtil.dismiss()

        // ViewModel & Binding
        viewModel.isInitialized = false
        viewModel.setNote("")

        binding = null
        super.onDestroyView()
    }

    override fun onDestroy() {
        MyMusicListener.setMyListener(null)
        MyNoteListener.setMyListener(null)
        MyCompleteMarqueeListener.setMyListener(null)
        super.onDestroy()
    }

    private fun setupControlPanel() {

        binding?.controlPanel?.onRequestAudioPermission = {
            val permission = if (Build.VERSION.SDK_INT >= 33)
                Manifest.permission.READ_MEDIA_AUDIO
            else
                Manifest.permission.READ_EXTERNAL_STORAGE

            requestPermissionMusic.launch(permission)
        }

        val b = binding ?: return

        // Konfigurasi standar
        val style = InstrumentControlPanel.ControlConfig(
            textColor = Color.parseColor("#FFFFFF"),
            btnColor = Color.parseColor("#3D2510"),
            strokeColor = Color.parseColor("#9B6A14")
        )

        b.controlPanel.setup(instrumentType, LinearLayout.VERTICAL, style, this)
        b.controlPanel.setVolumeButtonVisible(false)

        // 2. Gunakan object eksplisit untuk Rewarded agar mudah di-null-kan
        rewardedAdListener = object : InstrumentControlPanel.AdRequestListener {
            override fun onShowRewardedAd(type: String, onComplete: () -> Unit) {
                // Cek status fragment sebelum jalankan iklan
                val act = activity as? GameActivity
                if (act == null || !isAdded) return

                act.showRewardedAd(soundViewModel.isPremium) {
                    // Callback Reward: Cek binding null untuk cegah crash/leak
                    val currentBinding = binding
                    if (isAdded && currentBinding != null) {
                        onComplete()
                        if (type == "music") soundViewModel.isMusicUnlocked = true
                        else if (type == "list_record") soundViewModel.isRecordUnlocked = true

                        // Update UI panel
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

    fun musicStatus() {
        MusicListDialogHelper.statusListener = object : MusicListDialogHelper.MusicStatusListener {
            override fun onMusicPlay(track: MusicPlayerManager.MusicTrack) {
                viewLifecycleOwner.lifecycleScope.launch {
                    delay(1000)
                    binding?.ivStop?.visibility = View.VISIBLE
                    startAnimation()
                }
            }

            override fun onMusicPause(track: MusicPlayerManager.MusicTrack?) {
                binding?.ivStop?.visibility = View.GONE; stopAnimation()
            }

            override fun onMusicStop() {
                binding?.ivStop?.visibility = View.GONE; stopAnimation()
            }

            override fun onMusicComplete() {
                binding?.ivStop?.visibility = View.GONE; stopAnimation()
            }

            override fun onMusicProgress(current: Int, max: Int) {}
        }
        binding?.ivStop?.setOnClickListener { MusicPlayerManager.stop() }
    }

    override fun onVolume() {
        val context = requireContext()
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_volume_control, null)
        val seekBarMusic = dialogView.findViewById<SeekBar>(R.id.seekBarMusic)
        val seekBarAudio = dialogView.findViewById<SeekBar>(R.id.seekBarAudio)
        val musicPrefs = context.getSharedPreferences("music_player_prefs", Context.MODE_PRIVATE)
        seekBarMusic.progress = (musicPrefs.getFloat("music_volume", 0.7f) * 100).toInt()
        seekBarAudio.progress = ((sharedPreferences?.getFloat(Constant.KeyShared.volumeAudio, 1.0f)
            ?: 1.0f) * 100).toInt()

        val dialog = AlertDialog.Builder(context).setView(dialogView).create()
        seekBarMusic.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
                if (f) {
                    val v = p / 100f; MusicPlayerManager.setVolume(v, v); musicPrefs.edit()
                        .putFloat("music_volume", v).apply()
                }
            }

            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        seekBarAudio.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
                if (f) {
                    val v = p / 100f; soundViewModel.setVolume(v); sharedPreferences?.edit()
                        ?.putFloat(Constant.KeyShared.volumeAudio, v)?.apply()
                }
            }

            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    override fun onStopAll() {
        soundViewModel.setVolume(volumeAudio)
    }

    override fun onRecordStatusChanged(isRecording: Boolean) {
        if (isRecording) setToast(getString(R.string.record_started))
    }

    private fun loadSharedPreferencesAudio() {
        lifecycleScope.launch(Dispatchers.IO) {
            volumeAudio = sharedPreferences?.getFloat(Constant.KeyShared.volumeAudio, 1.0f) ?: 1.0f
        }
    }

    private fun initListener() {
        MyAdsListener.loadInterstitial();
        MyAdsListener.loadReward();
        MyNoteListener.setMyListener(this);
        MyMusicListener.setMyListener(this)
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, k: String?) {
        if (k == Constant.KeyShared.backgroundColor) {
            val c = DataSession(requireActivity()).getSharedUpdate().getInt(
                Constant.KeyShared.backgroundColor,
                -1
            ); binding?.layoutBackground?.setBackgroundColor(c)
        }
    }

    override fun onVolumeAudio(v: Float?) {
        v?.let { volumeAudio = it; soundViewModel.setVolume(it) }
    }

    private fun startAnimation() {
        try {
            binding?.ivStop?.startAnimation(mPanAnim)
        } catch (e: Exception) {
        }
    }

    private fun stopAnimation() {
        try {
            binding?.ivStop?.clearAnimation(); binding?.ivStop?.visibility = View.GONE
        } catch (e: Exception) {
        }
    }

    override fun onCompleteTextMarquee() {
        binding?.apply {
            tvRunningText.text = getString(R.string.text_choose_not); tvSpeedPlus.visibility =
            View.GONE; tvSpeedMin.visibility = View.GONE; tvClear.visibility = View.GONE
        }
    }

    private fun observeViewModel() {
        soundViewModel.loadingProgress.observe(viewLifecycleOwner) { ProgressDialogUtil.update(it) }; soundViewModel.isAllSoundsLoaded.observe(
            viewLifecycleOwner
        ) { if (it && isAdded) ProgressDialogUtil.dismiss() }
    }

    override fun onNoteCallback(n: String?) {
        if (!isAdded) return; viewLifecycleOwner.lifecycleScope.launch {
            binding?.apply {
                tvRunningText.text =
                    n.toString(); tvRunningText.startScroll();
                tvSpeedPlus.visibility = View.VISIBLE;
                tvClear.visibility = View.VISIBLE
                tvSpeedMin.visibility = View.VISIBLE
            }
        }
    }
}
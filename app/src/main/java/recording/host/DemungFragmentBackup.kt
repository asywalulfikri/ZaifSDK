/*
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
import android.widget.Toast
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
import sound.recorder.widget.recording.database.RecordedTap
import sound.recorder.widget.ui.viewmodel.MusicViewModel
import sound.recorder.widget.util.Constant
import sound.recorder.widget.util.DataSession
import sound.recorder.widget.util.ProgressDialogUtil

class DemungFragmentBackup : BaseFragment(), SharedPreferences.OnSharedPreferenceChangeListener,
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

    // ─── 1. REGISTER PERMISSION MIC ───
    private val requestAudioPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            setToast("granted")
        } else {
            setToast("denied")
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
            musicStatus()

            activity?.volumeControlStream = AudioManager.STREAM_MUSIC
        }
    }

    // ─── 2. CONTROL PANEL SETUP ───
    private fun setupControlPanel() {
        val b = binding ?: return

        b.controlPanel.onRequestAudioPermission = {
            requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT < 33) {
                requestPermissionMusic.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
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

    // ─── 3. INSTRUMENT CONTROL LISTENER ───

    override fun onMuteControl(mute: Boolean) {
        if (mute) {
            // Matikan suara langsung di SoundPlayUtils (yang benar-benar dipakai playSound)
            // dan juga di ViewModel untuk konsistensi state
            SoundPlayUtils.setVolume(0.0f)
            soundViewModel.setVolume(0.0f)
        } else {
            val currentVol = if (volumeAudio > 0) volumeAudio else 1.0f
            // Nyalakan suara langsung di SoundPlayUtils agar playSound() langsung pakai volume benar
            SoundPlayUtils.setVolume(currentVol)
            soundViewModel.setVolume(currentVol)
        }
    }

    override fun onRecordStatusChanged(isRecording: Boolean) {
        if (isRecording) {
            setToast(getString(R.string.record_started))
        } else {
            // Pastikan volume kembali normal setelah selesai rekam
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

        // Pilih jenis suara (Pelog/Slendro) berdasarkan metadata rekaman
        if (metadata == instrumentType + typeSlendro) b.instrumentView.setSelectedTab(1)
        else b.instrumentView.setSelectedTab(0)

        b.instrumentView.triggerHitAnimation(event.padIndex)

        val typeKey = metadata.ifEmpty { instrumentType + typePelog }
        SoundPlayUtils.playSound(typeKey, "type${event.padIndex + 1}")
    }

    override fun onVolume() {
        val context = requireContext()
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_volume_control, null)
        val seekBarMusic = dialogView.findViewById<SeekBar>(R.id.seekBarMusic)
        val seekBarAudio = dialogView.findViewById<SeekBar>(R.id.seekBarAudio)
        val musicPrefs = context.getSharedPreferences("music_player_prefs", Context.MODE_PRIVATE)

        seekBarMusic.progress = (musicPrefs.getFloat("music_volume", 0.7f) * 100).toInt()
        seekBarAudio.progress = (volumeAudio * 100).toInt()

        val dialog = AlertDialog.Builder(context).setView(dialogView).create()

        seekBarMusic.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
                if (f) {
                    val v = p / 100f
                    MusicPlayerManager.setVolume(v, v)
                    musicPrefs.edit().putFloat("music_volume", v).apply()
                }
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        seekBarAudio.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
                if (f) {
                    volumeAudio = p / 100f
                    // Update keduanya agar selalu sinkron
                    SoundPlayUtils.setVolume(volumeAudio)
                    soundViewModel.setVolume(volumeAudio)
                    sharedPreferences?.edit()?.putFloat(Constant.KeyShared.volumeAudio, volumeAudio)?.apply()
                }
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    // ─── 4. TOUCH & NAVIGATION ───

    private fun setupTouchListener() {
        binding?.instrumentView?.onBilahHitListener = { index, metadata ->
            binding?.controlPanel?.recordEvent(index, metadata)
        }

        binding?.tvSpeedPlus?.setOnClickListener {
            val b = binding ?: return@setOnClickListener
            b.tvSpeedMin.visibility = View.VISIBLE
            b.tvRunningText.setSpeed(b.tvRunningText.getSpeed() + 25f)
        }

        binding?.tvSpeedMin?.setOnClickListener {
            val b = binding ?: return@setOnClickListener
            val newSpeed = (b.tvRunningText.getSpeed() - 50f).coerceAtLeast(25f)
            b.tvRunningText.setSpeed(newSpeed)
            if (newSpeed == 25f) b.tvSpeedMin.visibility = View.GONE
        }

        binding?.tvClear?.setOnClickListener {
            binding?.apply {
                tvRunningText.clear()
                tvSpeedPlus.visibility = View.GONE
                tvSpeedMin.visibility = View.GONE
                tvClear.visibility = View.GONE
            }
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

    // ─── 5. SETUP & LIFECYCLE ───

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
            // Sync volume ke SoundPlayUtils setelah load dari SharedPreferences
            SoundPlayUtils.setVolume(volumeAudio)
        }
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

    override fun onDestroyView() {
        // Pastikan volume dikembalikan jika user keluar saat sedang mute/recording
        SoundPlayUtils.setVolume(volumeAudio)
        soundViewModel.setVolume(volumeAudio)

        binding?.controlPanel?.let { panel ->
            panel.adRequestListener = null
            panel.releaseAndStop()
        }
        rewardedAdListener = null

        binding?.tvRunningText?.apply {
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
            val runningText = binding?.tvRunningText ?: return
            layoutListener = object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    val vto = binding?.tvRunningText?.viewTreeObserver
                    if (vto?.isAlive == true) vto.removeOnGlobalLayoutListener(this)
                    layoutListener = null
                    binding?.tvRunningText?.startScrollAfterUpdate(viewModel.marqueeLastScrollX)
                }
            }
            runningText.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
        } catch (e: Exception) { setLog(e.message) }
    }

    override fun onPause() {
        super.onPause()
        try {
            sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
            layoutListener?.let {
                binding?.tvRunningText?.viewTreeObserver?.removeOnGlobalLayoutListener(it)
                layoutListener = null
            }
            viewModel.marqueeLastScrollX = binding?.tvRunningText?.textScroller?.currX ?: 0
            binding?.tvRunningText?.pauseScroll()
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
            SoundPlayUtils.setVolume(it) // sync ke SoundPlayUtils juga
            soundViewModel.setVolume(it)
        }
    }

    private fun startAnimation() {
        try { binding?.ivStop?.startAnimation(mPanAnim) } catch (e: Exception) {}
    }

    private fun stopAnimation() {
        try { binding?.ivStop?.clearAnimation(); binding?.ivStop?.visibility = View.GONE } catch (e: Exception) {}
    }

    override fun onCompleteTextMarquee() {
        binding?.apply {
            tvRunningText.text = getString(R.string.text_choose_not)
            tvSpeedPlus.visibility = View.GONE
            tvSpeedMin.visibility = View.GONE
            tvClear.visibility = View.GONE
        }
    }

    private fun observeViewModel() {
        soundViewModel.loadingProgress.observe(viewLifecycleOwner) { ProgressDialogUtil.update(it) }
        soundViewModel.isAllSoundsLoaded.observe(viewLifecycleOwner) { if (it && isAdded) ProgressDialogUtil.dismiss() }
    }

    override fun onNoteCallback(n: String?) {
        if (!isAdded) return
        viewLifecycleOwner.lifecycleScope.launch {
            binding?.apply {
                tvRunningText.text = n.toString()
                tvRunningText.startScroll()
                tvSpeedPlus.visibility = View.VISIBLE
                tvClear.visibility = View.VISIBLE
                tvSpeedMin.visibility = View.VISIBLE
            }
        }
    }

    override fun onDestroy() {
        MyMusicListener.setMyListener(null)
        MyNoteListener.setMyListener(null)
        MyCompleteMarqueeListener.setMyListener(null)
        super.onDestroy()
    }
}*/

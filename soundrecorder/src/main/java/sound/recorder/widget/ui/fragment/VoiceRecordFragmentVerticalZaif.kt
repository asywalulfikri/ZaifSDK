package sound.recorder.widget.ui.fragment


import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sound.recorder.widget.R
import sound.recorder.widget.base.BaseFragmentWidget
import sound.recorder.widget.builder.ZaifSDKBuilder
import sound.recorder.widget.databinding.WidgetRecordVerticalZaifBinding
import sound.recorder.widget.listener.MyAdsListener
import sound.recorder.widget.listener.MyMusicListener
import sound.recorder.widget.ui.bottomSheet.BottomSheet
import sound.recorder.widget.ui.bottomSheet.BottomSheetNote
import sound.recorder.widget.ui.viewmodel.MusicViewModel
import sound.recorder.widget.util.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.getValue
import kotlin.math.ln


class VoiceRecordFragmentVerticalZaif :
    BaseFragmentWidget(),
    SharedPreferences.OnSharedPreferenceChangeListener,
    BottomSheet.OnClickListener {

    private var _binding: WidgetRecordVerticalZaifBinding? = null
    private val binding get() = _binding!!

    private lateinit var blinkHandler: Handler
    private var isBlinking = false

    private var volumeMusic = 1.0f
    private var volumeAudio = 1.0f

    private var lastNavigateTime = 0L

    private val musicViewModel: MusicViewModel by activityViewModels()

    private val simpleDateFormat = SimpleDateFormat("yyyy.MM.dd_HH.mm.ss", Locale.US)
    private val date: String get() = simpleDateFormat.format(Date())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = WidgetRecordVerticalZaifBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        blinkHandler = Handler(Looper.getMainLooper())

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                withContext(Dispatchers.IO) {
                    val appContext = requireContext().applicationContext
                    dataSession = DataSession(appContext)
                    sharedPreferences = dataSession.getShared()
                    volumeMusic = dataSession.getVolumeMusic()
                    volumeAudio = dataSession.getVolumeAudio()
                    zaifSDKBuilder =
                        ZaifSDKBuilder.builder(appContext).loadFromSharedPreferences()
                }

                setupView()

                if (dataSession.showTooltip() && !dataSession.isDoneTooltip()) {
                    runCatching {

                    }
                }
            }
        }
    }

    // =========================
    // SETUP UI & OBSERVERS
    // =========================

    private fun setupView() {
        setupWidgetVertical(zaifSDKBuilder, _binding)

        musicViewModel.setRecord.observe(viewLifecycleOwner) {
            when (it) {
                true if musicViewModel.isPause -> showLayoutPauseRecord()
                true if musicViewModel.recorder != null -> showLayoutStartRecord()
                else -> showLayoutStopRecord()
            }
        }

        musicViewModel.pauseRecord.observe(viewLifecycleOwner) {
            it?.let {
                setToastTic(Toastic.SUCCESS, getString(R.string.record_paused))
                showLayoutPauseRecord()
            }
        }

        musicViewModel.resumeRecord.observe(viewLifecycleOwner) {
            it.getContentIfNotHandled()?.let {
                setToastTic(Toastic.SUCCESS, getString(R.string.record_resumed))
                showLayoutStartRecord()
            }
        }

        musicViewModel.cancelRecord.observe(viewLifecycleOwner) {
            it.getContentIfNotHandled()?.let {
                showLayoutStopRecord()
            }
        }

        musicViewModel.stopRecord.observe(viewLifecycleOwner) {
            it.getContentIfNotHandled()?.let {
                showLayoutStopRecord()
            }
        }

        binding.ivRecord.setOnClickListener {
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.O ||
                Build.VERSION.SDK_INT == Build.VERSION_CODES.O_MR1
            ) {
                setToast(getString(R.string.device_not_support))
            } else {
                if (musicViewModel.isPause) {
                    musicViewModel.resumeRecord()
                } else {
                    startPermission()
                }
            }
        }

        binding.ivPause.setOnClickListener {
            musicViewModel.pauseRecord()
        }

        binding.ivDone.setOnClickListener {
            musicViewModel.stopRecord()
            showBottomSheet()
        }

        binding.ivDelete.setOnClickListener {
            showCancelDialog()
        }

        binding.ivNote.setOnClickListener {
            if (isAdded && !parentFragmentManager.isStateSaved) {
                BottomSheetNote().show(parentFragmentManager, LOG_TAG)
            }
        }

        binding.ivListRecord.setOnClickListener {
            safeNavigate(R.id.action_widget_to_list_record)
            MyAdsListener.setHideAllBanner()
        }

        binding.ivVolume.setOnClickListener {
            showVolumeDialog()
        }

        binding.ivSong.setOnClickListener {
            startPermissionSong()
        }

    }


    private fun startPermissionSong(){
        if(Build.VERSION.SDK_INT >=Build.VERSION_CODES.TIRAMISU){
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                showBottomSheetSong()
            } else {
                requestPermissionSong.launch(Manifest.permission.READ_MEDIA_AUDIO)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(activity as Context, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionSong.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }else{
                showBottomSheetSong()
            }
        }else{
            showBottomSheetSong()
        }
    }

    private val requestPermissionSong =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            // do something
            if(isGranted){
                showBottomSheetSong()
            }else{
                showAllowPermission()
            }
        }


    /* private fun showBottomSheetSong(){
         try {
             if(activity!=null){
                 findNavController().navigate(R.id.action_widget_to_list_song)
                 MyAdsListener.setBannerHome(false)
             }
         }catch (e : Exception){
             setLog(e.message)
         }
     }*/


    private fun showBottomSheetSong() {
        if (!isAdded) return   // Fragment sudah attach?

        val now = System.currentTimeMillis()
        if (now - lastNavigateTime < 600) return
        lastNavigateTime = now

        val navController = findNavController()
        val currentDestination = navController.currentDestination

        if (currentDestination?.getAction(R.id.action_widget_to_list_song) != null) {
            navController.navigate(R.id.action_widget_to_list_song)
            MyAdsListener.setHideAllBanner()
        }
    }


    private fun showVolumeDialog(){
        try {
            if (!isAdded) return
            DialogUtils().showVolumeDialog(
                context = requireContext(),
                initialVolumeMusic = volumeMusic, // Volume music
                initialVolumeAudio = volumeAudio, // Volume audio
                onVolumeMusicChanged = { newVolumeMusic ->
                    volumeMusic = newVolumeMusic
                    musicViewModel.setVolume(newVolumeMusic)
                    // mp?.setVolume(newVolumeMusic, newVolumeMusic) // Update volumeMediaPlayer
                },
                onVolumeAudioChanged = { newVolumeAudio ->
                    volumeAudio = newVolumeAudio
                    MyMusicListener.postVolumeAudio(newVolumeAudio) // Update volume SoundPool
                }
            )
        }catch (e : Exception){
            setToast(e.message.toString())
        }
    }

    // =========================
    // RECORD UI STATE
    // =========================

    private fun showLayoutStartRecord() {
        binding.ivRecord.visibility = View.GONE
        binding.ivPause.visibility = View.VISIBLE
        binding.ivDone.visibility = View.VISIBLE
        binding.ivDelete.visibility = View.VISIBLE
        startBlinking()
    }

    private fun showLayoutPauseRecord() {
        binding.ivRecord.setImageResource(R.drawable.play_white)
        binding.ivRecord.visibility = View.VISIBLE
        binding.ivPause.visibility = View.GONE
        stopBlinking()
    }

    private fun showLayoutStopRecord() {
        binding.ivRecord.setImageResource(R.drawable.record)
        binding.ivRecord.visibility = View.VISIBLE
        binding.ivPause.visibility = View.GONE
        binding.ivDone.visibility = View.GONE
        binding.ivDelete.visibility = View.GONE
        stopBlinking()
    }

    // =========================
    // BLINK TIMER (SAFE)
    // =========================

    private val blinkRunnable = object : Runnable {
        override fun run() {
            if (!isBlinking || _binding == null) return
            binding.tvTimerView.visibility =
                if (binding.tvTimerView.visibility == View.VISIBLE)
                    View.INVISIBLE else View.VISIBLE
            blinkHandler.postDelayed(this, 500)
        }
    }

    private fun startBlinking() {
        if (isBlinking) return
        isBlinking = true
        blinkHandler.post(blinkRunnable)
    }

    private fun stopBlinking() {
        isBlinking = false
        blinkHandler.removeCallbacksAndMessages(null)
        _binding?.tvTimerView?.visibility = View.GONE
    }

    // =========================
    // PERMISSION & DIALOG
    // =========================

    private fun startPermission() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            showRecordDialog()
        } else {
            requestPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (it) showRecordDialog() else showAllowPermission()
        }

    private fun showRecordDialog() {
        if (!isAdded) return
        DialogUtils().showRecordDialog(
            requireContext(),
            getString(R.string.information),
            getString(R.string.title_recording_dialog)
        ) {
            dirPath = "${requireActivity().externalCacheDir?.absolutePath}/"
            fileName = "record_$date.mp3"
            musicViewModel.recordAudioStart(fileName, dirPath)
        }
    }

    private fun showBottomSheet() {
        if (isAdded && !parentFragmentManager.isStateSaved) {
            BottomSheet(dirPath, fileName, this)
                .show(parentFragmentManager, LOG_TAG)
        }
    }

    // =========================
    // SAFE NAVIGATION
    // =========================

    private fun safeNavigatppe(actionId: Int) {
        runCatching {
            if (findNavController().currentDestination?.id == R.id.VoiceRecordFragmentHorizontalZaif) {
                findNavController().navigate(actionId)
            }
        }
    }


    fun safeNavigate(actionId: Int) {
        val navController = findNavController()
        val currentDestination = navController.currentDestination

        if (currentDestination?.getAction(actionId) != null) {
            navController.navigate(actionId)
        }
    }


    // =========================
    // LIFECYCLE CLEANUP
    // =========================

    override fun onStart() {
        super.onStart()
        sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onStop() {
        super.onStop()
        sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onDestroyView() {
        stopBlinking()
        blinkHandler.removeCallbacksAndMessages(null)
        _binding = null
        super.onDestroyView()
    }

    override fun onDestroy() {
        musicViewModel.releaseMediaPlayerOnDestroy()
        musicViewModel.stopRecord()
        super.onDestroy()
    }

    // =========================
    // SHARED PREF CALLBACK
    // =========================

    override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
        if (key == Constant.KeyShared.volume) {
            val progress = prefs?.getInt(key, 100) ?: return
            val volume = (1 - ln(
                (ToneGenerator.MAX_VOLUME - progress).toDouble()
            ) / ln(ToneGenerator.MAX_VOLUME.toDouble())).toFloat()
            musicViewModel.setVolume(volume)
        }
    }

    override fun onCancelClicked() {
        musicViewModel.cancelRecord()
    }

    override fun onOkClicked(filePath: String, newName: String, isChange: Boolean) {
        if (isAdded) {
            musicViewModel.saveRecord(
                requireActivity(),
                dirPath,
                filePath,
                fileName,
                newName,
                isChange
            )
        }
    }


    private fun showCancelDialog(){
        try {
            DialogUtils().showCancelDialog(
                context = requireContext(),
                title =activity?.getString(R.string.information).toString(),
                message = activity?.getString(R.string.title_recording_canceled).toString(),
                dirPath = dirPath,
                fileName = fileName,
                stopRecordingAudio = { message ->
                    musicViewModel.cancelRecord()
                },
            )
        }catch (e : Exception){
            print(e.message)
        }
    }
}
package sound.recorder.widget.ui.fragment


import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sound.recorder.widget.R
import sound.recorder.widget.RecordingSDK
import sound.recorder.widget.base.BaseFragmentWidget
import sound.recorder.widget.builder.ZaifSDKBuilder
import sound.recorder.widget.databinding.WidgetRecordHorizontalZaifBinding
import sound.recorder.widget.db.AppDatabase
import sound.recorder.widget.db.AudioRecord
import sound.recorder.widget.listener.MyAdsListener
import sound.recorder.widget.listener.MyFragmentListener
import sound.recorder.widget.listener.MyMusicListener
import sound.recorder.widget.listener.MyPauseListener
import sound.recorder.widget.listener.MyStopMusicListener
import sound.recorder.widget.listener.MyStopSDKMusicListener
import sound.recorder.widget.listener.PauseListener
import sound.recorder.widget.ui.bottomSheet.BottomSheet
import sound.recorder.widget.ui.bottomSheet.BottomSheetNote
import sound.recorder.widget.util.*
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.ln


class VoiceRecordFragmentHorizontalZaif : BaseFragmentWidget(), BottomSheet.OnClickListener,
    FragmentSheetListSong.OnClickListener ,SharedPreferences.OnSharedPreferenceChangeListener,PauseListener {


    private var recordingAudio = false
    private var pauseRecordAudio = false

    private lateinit var handler: Handler
    private var _binding: WidgetRecordHorizontalZaifBinding? = null
    private val binding get() = _binding!!

    private var mp :  MediaPlayer? =null
    private var showBtnStop = false
    private var songIsPlaying = false

    private val blinkHandler = Handler(Looper.getMainLooper())
    private var isBlinking = false
    private var zaifSDKBuilder : ZaifSDKBuilder? =null

    private var volumeMusic: Float = 1.0f // Volume default 100% for MediaPlayer
    private var volumeAudio: Float = 1.0f // Volume default 100% for SoundPool


    companion object {
        fun newInstance(): VoiceRecordFragmentHorizontalZaif {
            return VoiceRecordFragmentHorizontalZaif()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        newInstance()
        val b = Bundle()
        super.onCreate(b)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = WidgetRecordHorizontalZaifBinding.inflate(inflater, container, false)
        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (activity != null && context != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    try {
                        zaifSDKBuilder = ZaifSDKBuilder.builder(requireContext()).loadFromSharedPreferences()
                    } catch (e: Exception) {
                        Log.e("VoiceRecordFragment", "Error initializing SDK", e)
                    }

                    val dataSession = DataSession(requireContext())
                    sharedPreferences = dataSession.getShared()
                    volumeMusic = dataSession.getVolumeMusic()
                    volumeAudio = dataSession.getVolumeAudio()

                    MyPauseListener.setMyListener(this@VoiceRecordFragmentHorizontalZaif)
                    handler = Handler(Looper.getMainLooper())
                }

                setupView()
            }
        }

    }


    private fun setupView() {
        try {
            zaifSDKBuilder?.backgroundWidgetColor?.let { colorString ->
                if (colorString.isNotEmpty()) {
                    try {
                        val tintList = ColorStateList.valueOf(Color.parseColor(colorString))
                        ViewCompat.setBackgroundTintList(binding.llBackground, tintList)
                    } catch (e: IllegalArgumentException) {
                        setToast("Invalid color value: $colorString")
                    }
                }
            }
        }catch (e : Exception){
            //
        }

        binding.rlRecord.setOnClickListener {
            when {
                pauseRecordAudio -> resumeRecordingAudio()
                recordingAudio -> pauseRecordingAudio()
                else -> startPermission()
            }
        }

        binding.ivDone.setOnClickListener {
            try {
                stopRecordingAudio("")
                showBottomSheet()
            } catch (e: Exception) {
                setToast(e.message.toString())
            }
        }

        binding.ivListRecord.setOnClickListener {
            activity?.let {
                try {
                    MyFragmentListener.openFragment(ListRecordFragment())
                    MyAdsListener.setAds(false)
                } catch (e: Exception) {
                    setToast(e.message.toString())
                }
            } ?: setToast("Activity is not available")
        }

        binding.ivDelete.setOnClickListener {
            try {
                showCancelDialog()
            } catch (e: Exception) {
                setToast(e.message.toString())
            }
        }

        binding.ivSong.setOnClickListener {
            startPermissionSong()
        }

        binding.ivChangeColor.setOnClickListener {
            activity?.let {
                try {
                    RecordingSDK.showDialogColorPicker(it)
                } catch (e: Exception) {
                    setToast(e.message.toString())
                }
            } ?: setToast("Activity is not available")
        }

        binding.ivNote.setOnClickListener {
            activity?.let {
                try {
                    val bottomSheet = BottomSheetNote()
                    bottomSheet.show(it.supportFragmentManager, LOG_TAG)
                } catch (e: Exception) {
                    setToast(e.message.toString())
                }
            } ?: setToast("Activity is not available")
        }

        binding.ivVolume.setOnClickListener {
            showVolumeDialog()
        }

        setupHideShowMenu()
    }


    private fun setupHideShowMenu() {
        try {
            zaifSDKBuilder?.let { builder ->
                binding.ivNote.visibility = if (builder.showNote) View.VISIBLE else View.GONE
                binding.ivChangeColor.visibility = if (builder.showChangeColor) View.VISIBLE else View.GONE
                binding.ivSong.visibility = if (builder.showListSong) View.VISIBLE else View.GONE
                binding.ivVolume.visibility = if (builder.showVolume) View.VISIBLE else View.GONE
            } ?: run {
                // Optional: Log or handle the case where zaifSDKBuilder is null
                setLog("zaifSDKBuilder is null, menu items not updated")
            }
        }catch (e : Exception){
            //
        }
    }



    override fun onDestroyView() {
        super.onDestroyView()

        // Release MediaPlayer
        if(mp!=null){
            mp?.apply {
                try {
                    release()
                } catch (e: Exception) {
                    setLog("Error releasing MediaPlayer: ${e.message}")
                } finally {
                    mp = null
                }
                showBtnStop = false
                songIsPlaying = false
                MyPauseListener.showButtonStop(false)
                MyMusicListener.setMyListener(null)
                MyStopSDKMusicListener.setMyListener(null)
                MyStopMusicListener.setMyListener(null)
                MyPauseListener.setMyListener(null)
            }
        }


        // Release Recorder
        if (recorder != null && recordingAudio) {
            recorder?.apply {
                try {
                    release()
                } catch (e: Exception) {
                    setLog("Error releasing Recorder: ${e.message}")
                } finally {
                    recorder = null
                }
            }
            recordingAudio = false
            pauseRecordAudio = false
            showLayoutStopRecord()
        }
    }


    private fun showBottomSheetSong(){
        try {
            if(activity!=null){
                MyFragmentListener.openFragment(FragmentSheetListSong(showBtnStop,this))
                MyAdsListener.setAds(false)
            }
        }catch (e : Exception){
            setLog(e.message)
        }
    }


    private fun startPermission(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                // Pass any permission you want while launching
                requestPermission.launch(Manifest.permission.RECORD_AUDIO)
            }else{
                showRecordDialog()

            }
        }else{
            showRecordDialog()
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

    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            // do something
            if(isGranted){
                showRecordDialog()
            }else{
                showAllowPermission()
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


    private fun showLayoutStartRecord(){
        binding.ivDone.visibility = View.VISIBLE
        binding.ivDelete.visibility = View.VISIBLE
        binding.ivDelete.isClickable = true
        binding.ivRecord.setImageResource(R.drawable.ic_pause)

        startBlinking()
    }


    @SuppressLint("SetTextI18n")
    private fun showLayoutPauseRecord(){
        binding.ivRecord.setImageResource(0)
        binding.ivRecord.setImageResource(R.drawable.play_white)
        stopBlinking()

    }

    private fun stopBlinking() {
        isBlinking = false
        blinkHandler.removeCallbacksAndMessages(null)
        binding.tvTimerView.visibility = View.GONE
    }

    @SuppressLint("SetTextI18n")
    private fun showLayoutStopRecord(){
        binding.ivRecord.setImageResource(R.drawable.record)
        binding.ivDone.visibility = View.GONE
        binding.ivDelete.isClickable = false
        binding.ivDelete.visibility = View.GONE

        try {
            stopBlinking()
        }catch (e: Exception) {
            setToastError(activity,e.message.toString())
        }
    }


    private  fun showRecordDialog(){
        DialogUtils().showRecordDialog(
            context = requireContext(),
            title = activity?.getString(R.string.notification).toString(),
            message = activity?.getString(R.string.title_recording_dialog).toString(),
            onYesClick = {
                startRecordingAudio()
            }
        )
    }

    private fun showCancelDialog(){
        DialogUtils().showCancelDialog(
            context = requireContext(),
            title =activity?.getString(R.string.notification).toString(),
            message = activity?.getString(R.string.title_recording_canceled).toString(),
            dirPath = dirPath,
            fileName = fileName,
            stopRecordingAudio = { message ->
                stopRecordingAudio(message) // Logika untuk menghentikan perekaman audio
            },
        )
    }



    private fun showVolumeDialog(){
        DialogUtils().showVolumeDialog(
            context = requireContext(),
            initialVolumeMusic = volumeMusic, // Volume musik awal
            initialVolumeAudio = volumeAudio, // Volume audio awal
            onVolumeMusicChanged = { newVolumeMusic ->
                volumeMusic = newVolumeMusic
                mp?.setVolume(newVolumeMusic, newVolumeMusic) // Update volume pada MediaPlayer
            },
            onVolumeAudioChanged = { newVolumeAudio ->
                volumeAudio = newVolumeAudio
                MyMusicListener.postVolumeAudio(newVolumeAudio) // Update volume pada SoundPool
            }
        )
    }


    @SuppressLint("SimpleDateFormat")
    private fun startRecordingAudio() {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.O || Build.VERSION.SDK_INT == Build.VERSION_CODES.O_MR1) {
            setToast(activity?.getString(R.string.device_not_support).toString())
        } else {
            showLayoutStartRecord()
            recordingAudio = true

            // Format file name with date
            val pattern = "yyyy.MM.dd_hh.mm.ss"
            val simpleDateFormat = SimpleDateFormat(pattern)
            val date: String = simpleDateFormat.format(Date())

            dirPath = "${activity?.externalCacheDir?.absolutePath}/"
            fileName = "record_${date}.mp3"
            binding.tvTimerView.visibility = View.VISIBLE

            try {
                val file = File(dirPath)
                if (!file.exists()) file.mkdirs() // Ensure directory exists

                recorder = MediaRecorder()
                recorder?.apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                    setOutputFile(dirPath + fileName)

                    // Move prepare to a background thread
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            prepare() // Prepare in a background thread
                            withContext(Dispatchers.Main) {
                                start() // Start recording on the main thread
                                setToastTic(Toastic.INFO, activity?.getString(R.string.record_started).toString())
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                e.printStackTrace()
                                setToastError(activity, e.message.toString())
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                setToastError(activity, e.message.toString())
            }
        }
    }

    private fun pauseRecordingAudio() {
        if (recorder != null && recordingAudio) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    recorder?.pause()
                    showLayoutPauseRecord()
                    pauseRecordAudio = true
                    CoroutineScope(Dispatchers.Main).launch {
                        setToastTic(Toastic.INFO, activity?.getString(R.string.record_paused).toString())
                    }
                } else {
                    setToastError(activity, "Pause recording is not supported on this device.")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                CoroutineScope(Dispatchers.Main).launch {
                    setToastError(activity, e.message.toString())
                }
            }
        }
    }


    private fun startBlinking() {
        isBlinking = true
        blinkHandler.post(object : Runnable {
            override fun run() {
                if (isBlinking && isAdded) {
                    // Toggle visibility safely
                    binding.tvTimerView.visibility =
                        if (binding.tvTimerView.visibility == View.VISIBLE) View.INVISIBLE else View.VISIBLE
                    // Repeat every 500ms
                    blinkHandler.postDelayed(this, 500)
                }
            }
        })
    }

    private fun resumeRecordingAudio(){
        if(recorder!=null&&pauseRecordAudio){
            try {
                recorder?.apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        resume()
                        setToastTic(Toastic.INFO,activity?.getString(R.string.record_resumed).toString())
                        pauseRecordAudio = false

                        binding.ivRecord.setImageResource(R.drawable.ic_pause)
                        //animatePlayerView()
                        //timer.start()
                        startBlinking()


                    }
                }

            }catch (e: Exception) {
                // Handle other exceptions
                e.printStackTrace()
                setToastError(activity,e.message.toString())
                // Perform error handling or show appropriate message to the user
            }

        }
    }

    private fun stopRecordingAudio(message : String){
        if(recorder!=null&&recordingAudio){

            try {
                recorder?.apply {
                    binding.tvTimerView.visibility = View.GONE
                    stop()
                    reset()
                    release()
                    recordingAudio = false
                    pauseRecordAudio= false
                    showLayoutStopRecord()
                    if(message.isNotEmpty()){
                        setToastTic(Toastic.INFO,message)
                    }
                }

                recorder = null
            } catch (e: Exception) {
                setToastError(activity,e.message.toString())
            }

        }
    }

    private fun showBottomSheet(){
        val bottomSheet = BottomSheet(dirPath, fileName, this)
        bottomSheet.show(requireActivity().supportFragmentManager, LOG_TAG)
    }

    @SuppressLint("SetTextI18n")
    override fun onCancelClicked() {
        setToastTic(Toastic.SUCCESS, requireContext().getString(R.string.record_canceled))
        stopRecordingAudio("")
    }


    @SuppressLint("SetTextI18n")
    override fun onOkClicked(filePath: String, filename: String, isChange: Boolean) {
        if (activity != null) {
            val db = Room.databaseBuilder(requireActivity(), AppDatabase::class.java, "audioRecords").build()

            stopRecordingAudio("")

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    // Operasi file di thread IO
                    if (isChange) {
                        val newFile = File("$dirPath$filename.mp3")
                        File("$dirPath$fileName").renameTo(newFile)
                    }

                    // Operasi database di thread IO
                    db.audioRecordDAO().insert(
                        AudioRecord(
                            filename,
                            filePath,
                            Date().time,
                            getFormattedAudioDuration(filePath)
                        )
                    )

                    // Update UI di thread utama
                    withContext(Dispatchers.Main) {
                        setToastTic(Toastic.SUCCESS, activity?.getString(R.string.record_saved).toString())
                    }
                } catch (e: Exception) {
                    // Tangani error
                    withContext(Dispatchers.Main) {
                        setToastError(activity, e.message.toString())
                    }
                }
            }
        }
    }



    override fun onPlaySong(filePath: String) {
        if (activity != null) {
            try {
                // Hentikan dan lepaskan MediaPlayer jika ada
                mp?.apply {
                    release()
                    mp = null
                    MyMusicListener.postAction(null)
                }
            } catch (e: Exception) {
                setToastError(activity, e.message.toString())
            }

            // Jalankan operasi MediaPlayer di background thread
            lifecycleScope.launch {
                delay(100) // Gantikan Handler dengan coroutine delay
                try {
                    mp = MediaPlayer()
                    mp?.apply {
                        setDataSource(requireContext(), Uri.parse(filePath))
                        setVolume(volumeMusic, volumeMusic)
                        setOnPreparedListener {
                            start()
                            MyMusicListener.postAction(mp)
                            MyStopSDKMusicListener.onStartAnimation()
                        }
                        prepareAsync()
                        setOnCompletionListener {
                            MyStopSDKMusicListener.postAction(true)
                            MyStopMusicListener.postAction(true)
                            MyPauseListener.showButtonStop(false)
                            showBtnStop = false
                        }
                        MyPauseListener.showButtonStop(true)
                        showBtnStop = true
                        songIsPlaying = true
                    }
                } catch (e: Exception) {
                    MyStopSDKMusicListener.postAction(true)
                    MyStopMusicListener.postAction(true)
                    MyPauseListener.showButtonStop(false)
                    showBtnStop = false
                    setToastError(activity, e.message.toString())
                }
            }
        }
    }



    override fun onPause() {
        super.onPause()
        sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        if (mp != null) {
            mp.apply {
                mp?.release()
                MyMusicListener.postAction(null)
                MyStopSDKMusicListener.postAction(true)
                MyStopMusicListener.postAction(true)
                MyPauseListener.showButtonStop(false)
                showBtnStop = false
                songIsPlaying = false
            }
        }

        if(recorder!=null&&recordingAudio){
            recorder?.apply {
                release()
                recordingAudio = false
                pauseRecordAudio= false
            }
            recorder = null
            showLayoutStopRecord()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mp != null) {
            mp?.apply {
                release()
                showBtnStop = false
                songIsPlaying = false
                MyPauseListener.showButtonStop(false)
                MyMusicListener.setMyListener(null)
                MyStopSDKMusicListener.setMyListener(null)
                MyStopMusicListener.setMyListener(null)
                MyPauseListener.setMyListener(null)
            }
        }
        if(recorder!=null&&recordingAudio){
            recorder?.apply {
                release()
                recordingAudio = false
                pauseRecordAudio= false
            }
            recorder = null
            showLayoutStopRecord()
        }
    }

    override fun onStopSong() {
        if(activity!=null&&mp!=null){
            try {
                mp?.apply {
                    stop()
                    reset()
                    release()
                    mp = null
                    songIsPlaying = false
                    showBtnStop = false
                    MyPauseListener.showButtonStop(false)
                    MyMusicListener.postAction(null)
                    MyStopMusicListener.postAction(true)
                }
            } catch (e: IOException) {
                setToastError(activity,e.message.toString())
            } catch (e: IllegalStateException) {
                setToastError(activity,e.message.toString())
            }catch (e : Exception){
                setToastError(activity,e.message.toString())
            }
        }
    }

    override fun onNoteSong(note: String) {
        MyMusicListener.postNote(note)
    }

    override fun onResume() {
        super.onResume()
        sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
    }



    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if(key==Constant.KeyShared.volume){
            if(mp!=null){
                val progress = sharedPreferences?.getInt(Constant.KeyShared.volume,100)
                val volume = (1 - ln((ToneGenerator.MAX_VOLUME - progress!!).toDouble()) / ln(
                    ToneGenerator.MAX_VOLUME.toDouble())).toFloat()
                if(songIsPlaying){
                    mp?.setVolume(volume,volume)
                }
            }
        }
    }

    override fun onPause(pause: Boolean) {
       if(pause){
           showBtnStop = false
           MyPauseListener.showButtonStop(false)
       }
    }

    override fun showButtonStop(stop: Boolean) {

    }

}
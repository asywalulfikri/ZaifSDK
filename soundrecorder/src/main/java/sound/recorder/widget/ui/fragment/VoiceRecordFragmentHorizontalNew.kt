package sound.recorder.widget.ui.fragment

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.room.Room
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import sound.recorder.widget.R
import sound.recorder.widget.base.BaseFragmentWidget
import sound.recorder.widget.databinding.WidgetRecordHorizontalNewBinding
import sound.recorder.widget.db.AppDatabase
import sound.recorder.widget.db.AudioRecord
import sound.recorder.widget.listener.MyAdsListener
import sound.recorder.widget.listener.MyFragmentListener
import sound.recorder.widget.listener.MyMusicListener
import sound.recorder.widget.listener.MyPauseListener
import sound.recorder.widget.listener.MyStopMusicListener
import sound.recorder.widget.listener.MyStopSDKMusicListener
import sound.recorder.widget.listener.PauseListener
import sound.recorder.widget.tools.Timer
import sound.recorder.widget.ui.bottomSheet.BottomSheet
import sound.recorder.widget.ui.bottomSheet.BottomSheetNote
import sound.recorder.widget.ui.bottomSheet.BottomSheetSetting
import sound.recorder.widget.util.*
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.ln


class VoiceRecordFragmentHorizontalNew : BaseFragmentWidget(), BottomSheet.OnClickListener,
    FragmentSheetListSong.OnClickListener, Timer.OnTimerUpdateListener,SharedPreferences.OnSharedPreferenceChangeListener,PauseListener {

    private var recorder: MediaRecorder? = null
    private var recordingAudio = false
    private var pauseRecordAudio = false
    private var refreshRate : Long = 60
    private lateinit var timer: Timer

    private lateinit var handler: Handler
    private var _binding: WidgetRecordHorizontalNewBinding? = null
    private val binding get() = _binding!!

    private var mp :  MediaPlayer? =null
    private var showBtnStop = false
    private var songIsPlaying = false

    //ScreenRecorder
    private var screenRecorder: ScreenRecorder? =null
    private var recordingScreen = false
    private var pauseRecordScreen = false

    private var volumes : Float? =null
    private var showNote : Boolean? =null


    companion object {
        fun newInstance(): VoiceRecordFragmentHorizontalNew{
            return VoiceRecordFragmentHorizontalNew()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        newInstance()
        val b = Bundle()
        super.onCreate(b)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = WidgetRecordHorizontalNewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if(activity!=null&&context!=null){

            sharedPreferences = DataSession(requireActivity()).getShared()
            sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
            showNote = DataSession(requireActivity()).getShowNote()

            MyPauseListener.setMyListener(this)

            val tintList = ColorStateList.valueOf(Color.parseColor(DataSession(requireActivity()).getBackgroundRecord()))
            ViewCompat.setBackgroundTintList(binding.llRecordBackground, tintList)

            val progress = sharedPreferences?.getInt(Constant.KeyShared.volume,100)
            volumes = (1 - ln((ToneGenerator.MAX_VOLUME - progress!!).toDouble()) / ln(
                ToneGenerator.MAX_VOLUME.toDouble())).toFloat()



            if(showNote==true){
                binding.noteBtn.visibility = View.VISIBLE
            }else{
                binding.noteBtn.visibility = View.GONE
            }

            handler = Handler(Looper.myLooper()!!)


            binding.rlRecord.setOnClickListener {
                /*if(Build.VERSION.SDK_INT> Build.VERSION_CODES.N){
                    when {
                        pauseRecordAudio -> resumeRecordingAudio()
                        recordingAudio -> pauseRecordingAudio()
                        else -> startPermission()
                    }
                }else{
                    setToastError(activity,requireActivity().getString(R.string.device_not_support))
                }*/
                when {
                    pauseRecordAudio -> resumeRecordingAudio()
                    recordingAudio -> pauseRecordingAudio()
                    else -> startPermission()
                }
            }

            binding.doneBtn.setOnClickListener {
                try {
                    stopRecordingAudio("")
                    showBottomSheet()
                }catch (e : Exception){
                    setToast(activity,e.message.toString())
                }
            }

            binding.listBtn.setOnClickListener {
                try {
                    MyFragmentListener.openFragment(ListRecordFragment())
                    MyAdsListener.setAds(false)
                }catch (e : Exception){
                    setToast(activity,e.message.toString())
                }
            }

            binding.deleteBtn.setOnClickListener {
                try {
                    showCancelDialog()
                }catch (e : Exception){
                    setToast(activity,e.message.toString())
                }
            }

            binding.songBtn.setOnClickListener {
                startPermissionSong()
            }

            if(DataSession(requireActivity()).isCoverSong()){
                binding.coverBtn.visibility = View.VISIBLE
                binding.songBtn.visibility = View.GONE
            }else{
                binding.coverBtn.visibility = View.GONE
                binding.songBtn.visibility = View.VISIBLE
            }

            binding.coverBtn.setOnClickListener {
                startPermissionSong()
            }

            binding.deleteBtn.isClickable = false

            binding.settingBtn.setOnClickListener {
                try {
                    val bottomSheet = BottomSheetSetting()
                    bottomSheet.show(requireActivity().supportFragmentManager, LOG_TAG)
                }catch (e : Exception){
                    setToast(activity,e.message.toString())
                }

            }

            binding.noteBtn.setOnClickListener {
                try {
                    val bottomSheet = BottomSheetNote()
                    bottomSheet.show(requireActivity().supportFragmentManager, LOG_TAG)
                }catch (e : Exception){
                    setToast(activity,e.message.toString())
                }
            }
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        if (mp != null) {
            mp?.apply {
                release()
                showBtnStop = false
                songIsPlaying = false
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

    private fun showDialogRecord() {
        // custom dialog
        val dialog = Dialog(requireActivity())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_alert)
        dialog.setCancelable(true)

        // set the custom dialog components - text, image and button
        val btnAudio = dialog.findViewById<View>(R.id.btn_primary) as Button
        val btnScreenAudio = dialog.findViewById<View>(R.id.btn_cancel) as Button

        btnAudio.setOnClickListener {
            showRecordDialog()
            dialog.dismiss()
        }


        btnScreenAudio.visibility = View.GONE
        btnScreenAudio.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (ContextCompat.checkSelfPermission(activity as Context, Manifest.permission.FOREGROUND_SERVICE) != PackageManager.PERMISSION_GRANTED) {
                    // Pass any permission you want while launching
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        requestPermissionForeGround.launch(Manifest.permission.FOREGROUND_SERVICE)
                    }
                } else {
                    recordingScreen = true
                    startScreenRecorder()
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
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
            if (ContextCompat.checkSelfPermission(requireActivity(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
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
            if (ContextCompat.checkSelfPermission(requireActivity(), Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED) {
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


    private val requestPermissionForeGround =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            // do something
            if(isGranted){
                screenRecorder?.start(this,requireActivity())
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



    private fun startScreenRecorder(){
        screenRecorder?.start(this,requireActivity())
        showLayoutStartRecord()

    }

    private fun showLayoutStartRecord(){
        binding.listBtn.visibility = View.GONE
        binding.doneBtn.visibility = View.VISIBLE
        binding.recordText.visibility = View.GONE
        binding.deleteBtn.visibility = View.VISIBLE
        binding.deleteBtn.isClickable = true
        binding.recordBtn.setImageResource(R.drawable.ic_pause)

        timer = Timer(this)
        timer.start()
    }


    @SuppressLint("SetTextI18n")
    private fun showLayoutPauseRecord(){
        binding.recordText.visibility = View.VISIBLE
        binding.recordText.text = requireActivity().getString(R.string.text_continue)
        binding.recordBtn.setImageResource(R.drawable.transparant_bg)
        timer.pause()
    }

    @SuppressLint("SetTextI18n")
    private fun showLayoutStopRecord(){
        binding.recordBtn.setImageResource(R.drawable.transparant_bg)
        binding.recordText.text = requireActivity().getString(R.string.record)
        binding.recordText.visibility = View.VISIBLE
        binding.listBtn.visibility = View.VISIBLE
        binding.doneBtn.visibility = View.GONE
        binding.deleteBtn.isClickable = false
        binding.deleteBtn.visibility = View.GONE

        binding.playerView.reset()
        try {
            timer.stop()
        }catch (e: IllegalStateException) {
            // Handle IllegalStateException (e.g., recording already started)
            e.printStackTrace()
            setToastError(activity,e.message.toString())

            // Perform error handling or show appropriate message to the user
        } catch (e: IOException) {
            // Handle IOException (e.g., failed to prepare or write to file)
            e.printStackTrace()
            setToastError(activity,e.message.toString())
            // Perform error handling or show appropriate message to the user
        } catch (e: Exception) {
            // Handle other exceptions
            e.printStackTrace()
            setToastError(activity,e.message.toString())
            // Perform error handling or show appropriate message to the user
        }

        binding.timerView.text = "00:00.00"
    }


    private fun showRecordDialog() {
        try {
            val builder = AlertDialog.Builder(activity)
            builder.setTitle(activity?.getString(R.string.notification))
            builder.setMessage(activity?.getString(R.string.title_recording_dialog))
            builder.setPositiveButton(activity?.getString(R.string.yes)) { dialog, _ ->
                startRecordingAudio()
                dialog.dismiss()
            }
            builder.setNegativeButton(activity?.getString(R.string.no)) { dialog, _ ->
                dialog.dismiss()
            }
            val dialog = builder.create()
            dialog.show()
        }catch (e : Exception){
            setToast(activity,e.message.toString())
        }
    }


    private fun showCancelDialog() {
        try {
            val builder = AlertDialog.Builder(activity)
            builder.setTitle(activity?.getString(R.string.notification))
            builder.setMessage(activity?.getString(R.string.title_recording_canceled))
            builder.setPositiveButton(activity?.getString(R.string.yes)) { dialog, _ ->

                stopRecordingAudio(requireActivity().getString(R.string.record_canceled))
                File(dirPath+fileName).delete()

                dialog.dismiss()
            }
            builder.setNegativeButton(activity?.getString(R.string.no)) { dialog, _ ->
                dialog.dismiss()
            }
            val dialog = builder.create()
            dialog.show()
        }catch (e : Exception){
            setToast(activity,e.message.toString())
        }
    }

    @SuppressLint("SimpleDateFormat")
    private fun startRecordingAudio(){

        showLayoutStartRecord()

        recordingAudio = true

        // format file name with date
        val pattern = "yyyy.MM.dd_hh.mm.ss"
        val simpleDateFormat = SimpleDateFormat(pattern)
        val date: String = simpleDateFormat.format(Date())

        dirPath = "${activity?.externalCacheDir?.absolutePath}/"
        fileName = "record_${date}.mp3"

        try {
            recorder =  MediaRecorder()
            recorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(dirPath + fileName)
                prepare()
                start()
                animatePlayerView()
                setToastInfo(activity,requireActivity().getString(R.string.record_started))
            }
        } catch (e: IllegalStateException) {
            // Handle IllegalStateException (e.g., recording already started)
            e.printStackTrace()
            setToastError(activity,e.message.toString())
            // Perform error handling or show appropriate message to the user
        } catch (e: IOException) {
            // Handle IOException (e.g., failed to prepare or write to file)
            e.printStackTrace()
            setToastError(activity,e.message.toString())
            // Perform error handling or show appropriate message to the user
        } catch (e: Exception) {
            // Handle other exceptions
            e.printStackTrace()
            setToastError(activity,e.message.toString())
            // Perform error handling or show appropriate message to the user
        }

    }


    private fun animatePlayerView(){
        if(recordingAudio && !pauseRecordAudio){
            try {
                val amp = recorder?.maxAmplitude
                binding.playerView.updateAmps(amp)
                handler.postDelayed(
                    {
                        kotlin.run { animatePlayerView() }
                    }, refreshRate
                )
            }catch (e : Exception){
                setToastError(activity,e.message.toString())
            }
        }
    }

    private fun pauseRecordingAudio(){
        if(recorder!=null&&recordingAudio){
            try {
                recorder?.apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        pause()
                        showLayoutPauseRecord()
                        pauseRecordAudio = true
                        setToastInfo(activity,requireActivity().getString(R.string.record_paused))
                    }
                }
            } catch (e: IllegalStateException) {
                // Handle IllegalStateException (e.g., recording already started)
                e.printStackTrace()
                setToastError(activity,e.message.toString())

                // Perform error handling or show appropriate message to the user
            } catch (e: IOException) {
                // Handle IOException (e.g., failed to prepare or write to file)
                e.printStackTrace()
                setToastError(activity,e.message.toString())
                // Perform error handling or show appropriate message to the user
            } catch (e: Exception) {
                // Handle other exceptions
                e.printStackTrace()
                setToastError(activity,e.message.toString())
                // Perform error handling or show appropriate message to the user
            }
        }
    }

    private fun resumeRecordingAudio(){
        if(recorder!=null&&pauseRecordAudio){
            try {
                recorder?.apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        resume()
                        setToastInfo(activity,requireActivity().getString(R.string.record_resumed))
                        binding.recordText.visibility = View.GONE
                        pauseRecordAudio = false

                        binding.recordBtn.setImageResource(R.drawable.ic_pause)
                        animatePlayerView()
                        timer.start()
                    }
                }

            } catch (e: IllegalStateException) {
                // Handle IllegalStateException (e.g., recording already started)
                e.printStackTrace()
                setToastError(activity,e.message.toString())

                // Perform error handling or show appropriate message to the user
            } catch (e: IOException) {
                // Handle IOException (e.g., failed to prepare or write to file)
                e.printStackTrace()
                setToastError(activity,e.message.toString())
                // Perform error handling or show appropriate message to the user
            } catch (e: Exception) {
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
                    stop()
                    reset()
                    release()
                    recordingAudio = false
                    pauseRecordAudio= false
                    showLayoutStopRecord()
                    if(message.isNotEmpty()){
                        setToastInfo(activity,message)
                    }
                }

                recorder = null
            } catch (e: IllegalStateException) {
                // Handle IllegalStateException (e.g., recording already started)
                //e.printStackTrace()
                setToastError(activity,e.message.toString())

                // Perform error handling or show appropriate message to the user
            } catch (e: IOException) {
                // Handle IOException (e.g., failed to prepare or write to file)
                //e.printStackTrace()
                setToastError(activity,e.message.toString())
                // Perform error handling or show appropriate message to the user
            } catch (e: Exception) {
                // Handle other exceptions
                //e.printStackTrace()
                setToastError(activity,e.message.toString())
                // Perform error handling or show appropriate message to the user
            }

        }
    }


    @SuppressLint("SetTextI18n")
    private fun pauseRecordingScreen(){
        if(screenRecorder!=null){
            showLayoutPauseRecord()
            pauseRecordScreen = true
            screenRecorder?.apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    pause()
                }
            }
        }
    }

    private fun showBottomSheet(){
        val bottomSheet = BottomSheet(dirPath, fileName, this)
        bottomSheet.show(requireActivity().supportFragmentManager, LOG_TAG)
    }

    @SuppressLint("SetTextI18n")
    override fun onCancelClicked() {
        setToastSuccess(activity,requireActivity().getString(R.string.record_canceled))
        binding.recordText.text = requireActivity().getString(R.string.record)
        binding.recordText.visibility = View.VISIBLE
        stopRecordingAudio("")
    }

    @OptIn(DelicateCoroutinesApi::class)
    @SuppressLint("SetTextI18n")
    override fun onOkClicked(filePath: String, filename: String, isChange : Boolean) {
        // add audio record info to database
        if(activity!=null){
            val db = Room.databaseBuilder(requireActivity(), AppDatabase::class.java, "audioRecords").build()

            val duration = timer.format().split(".")[0]

            stopRecordingAudio("")

            if(isChange){
                val newFile = File("$dirPath$filename.mp3")
                File(dirPath+fileName).renameTo(newFile)
            }

            GlobalScope.launch {
                db.audioRecordDAO().insert(AudioRecord(filename, filePath, Date().time, duration))
            }
            setToastSuccess(activity,requireActivity().getString(R.string.record_saved))

            binding.recordText.visibility = View.VISIBLE
            binding.recordText.text = requireActivity().getString(R.string.record)
            showRewardInterstitial()
        }

    }

    override fun onPlaySong(filePath: String) {
        if(activity!=null){
            try {
                if(mp!=null){
                    mp.apply {
                        mp?.release()
                        mp = null
                        MyMusicListener.postAction(null)
                    }
                }
            }catch (e : Exception){
                setToastError(activity,e.message.toString())
            }
            Handler().postDelayed({
                try {
                    mp = MediaPlayer()
                    mp?.apply {
                        setDataSource(requireActivity(),Uri.parse(filePath))
                        volumes?.let { setVolume(it, volumes!!) }
                        setOnPreparedListener{
                            mp?.start()
                            MyMusicListener.postAction(mp)
                            MyStopSDKMusicListener.onStartAnimation()
                        }
                        mp?.prepareAsync()
                        setOnCompletionListener {
                            MyStopSDKMusicListener.postAction(true)
                            MyStopMusicListener.postAction(true)
                            showBtnStop = false
                        }
                        showBtnStop = true
                        songIsPlaying = true

                    }
                } catch (e: Exception) {
                    try {
                        MyStopSDKMusicListener.postAction(true)
                        MyStopMusicListener.postAction(true)
                        showBtnStop = false
                        setToastError(activity,e.message.toString())
                    }catch (e : Exception){
                        setLog(e.message)
                    }
                }
            }, 100)
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

    override fun onTimerUpdate(duration: String) {
        activity?.runOnUiThread{
            if(recordingAudio)
                binding.timerView.text = duration
        }
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
       }
    }

    override fun showButtonStop(stop: Boolean) {

    }

}
package sound.recorder.widget.ui.fragment


import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
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
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.room.Room
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
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

    private var recorder: MediaRecorder? = null
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
    private var isShowCase = true
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

        if(activity!=null&&context!=null){

            //init SDK Zaif
            zaifSDKBuilder = ZaifSDKBuilder.builder(requireActivity()).loadFromSharedPreferences()


            //init sharedPreferences
            sharedPreferences = DataSession(requireActivity()).getShared()
            sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
            isShowCase   = DataSession(requireActivity()).getShowCase()

            volumeMusic =  DataSession(requireActivity()).getVolumeMusic()
            volumeAudio =  DataSession(requireActivity()).getVolumeAudio()


            MyPauseListener.setMyListener(this)

            handler = Handler(Looper.myLooper()!!)


            setupView()

        }
    }


    private fun setupView(){

        val tintList = ColorStateList.valueOf(Color.parseColor(zaifSDKBuilder?.backgroundWidgetColor.toString()))
        ViewCompat.setBackgroundTintList(binding.llBackground, tintList)


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
            }catch (e : Exception){
                setToast(activity,e.message.toString())
            }
        }

        binding.ivListRecord.setOnClickListener {
            try {
                MyFragmentListener.openFragment(ListRecordFragment())
                MyAdsListener.setAds(false)
            }catch (e : Exception){
                setToast(activity,e.message.toString())
            }
        }

        binding.ivDelete.setOnClickListener {
            try {
                showCancelDialog()
            }catch (e : Exception){
                setToast(activity,e.message.toString())
            }
        }

        binding.ivSong.setOnClickListener {
            startPermissionSong()
        }

        binding.ivChangeColor.setOnClickListener {
            try {
                RecordingSDK.showDialogColorPicker(requireActivity())
            }catch (e : Exception){
                setToast(activity,e.message.toString())
            }

        }

        binding.ivNote.setOnClickListener {
            try {
                val bottomSheet = BottomSheetNote()
                bottomSheet.show(requireActivity().supportFragmentManager, LOG_TAG)
            }catch (e : Exception){
                setToast(activity,e.message.toString())
            }
        }

        binding.ivVolume.setOnClickListener {
            showVolumeDialog()
        }

        setupHideShowMenu()

    }


    private fun setupHideShowMenu(){
        if(zaifSDKBuilder?.showNote ==true){
            binding.ivNote.visibility = View.VISIBLE
        }else{
            binding.ivNote.visibility = View.GONE
        }

        if(zaifSDKBuilder?.showChangeColor==true){
            binding.ivChangeColor.visibility = View.VISIBLE
        }else{
            binding.ivChangeColor.visibility = View.GONE
        }

        if(zaifSDKBuilder?.showListSong==true){
            binding.ivSong.visibility = View.VISIBLE
        }else{
            binding.ivSong.visibility = View.GONE
        }

        if(zaifSDKBuilder?.showVolume==true){
            binding.ivVolume.visibility = View.VISIBLE
        }else{
            binding.ivVolume.visibility = View.GONE
        }
    }

    private fun starShowCase(){

       /* if(zaifSDKBuilder?.showNote==true){
            showCaseDialog(binding.ivNote,activity?.getString(R.string.text_guide_note))
        }else{
            if(zaifSDKBuilder?.showListSong==true){
                showCaseDialog(binding.ivSong,activity?.getString(R.string.text_guide_song))
            }else{
                showCaseDialog(binding.rlRecord,activity?.getString(R.string.text_guide_record))
            }
        }*/

    }

    /*private fun showCaseDialog(view: View, message : String? ){
        try {
            //val customFont = Typeface.createFromAsset(activity?.assets, "font/custom_font.ttf")
            val customFont = ResourcesCompat.getFont(requireActivity(), R.font.ooredoo)
            GuideView.Builder(activity)
                .setContentText(message.toString())
                .setContentTypeFace(customFont)
                .setGravity(Gravity.auto)
                .setTargetView(view)
                .setTitleTextSize(16)
                .setContentTextSize(18)
                .setDismissType(DismissType.anywhere) //optional - default dismissible by TargetView
                .setGuideListener {
                    when (view.id) {
                        R.id.ivNote->{
                            if(zaifSDKBuilder?.showListSong==true){
                                showCaseDialog(binding.ivSong,activity?.getString(R.string.text_guide_song))
                            }else{
                                showCaseDialog(binding.rlRecord,activity?.getString(R.string.text_guide_record))
                            }
                        }
                        R.id.ivSong->{
                            showCaseDialog(binding.rlRecord,activity?.getString(R.string.text_guide_record))
                        }
                        R.id.rlRecord -> {
                            showCaseDialog(binding.ivListRecord,activity?.getString(R.string.text_guide_list_record))
                        }
                        R.id.ivListRecord -> {
                            if(zaifSDKBuilder?.showVolume==true){
                                showCaseDialog(binding.ivVolume,activity?.getString(R.string.text_guide_audio))
                            }else{
                                DataSession(requireActivity()).saveShowCase(false)
                                return@setGuideListener
                            }
                        }

                        R.id.ivVolume -> {
                            if(zaifSDKBuilder?.showChangeColor==true){
                                showCaseDialog(binding.ivChangeColor,activity?.getString(R.string.text_guide_color))
                            }else{
                                DataSession(requireActivity()).saveShowCase(false)
                                return@setGuideListener
                            }
                        }
                        R.id.ivChangeColor -> {
                            DataSession(requireActivity()).saveShowCase(false)
                            return@setGuideListener
                        }
                    }

                }
                .build()
                .show()
        }catch (e : Exception){
          setLog(e.message.toString())
        }

    }*/

    override fun onDestroyView() {
        super.onDestroyView()
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


    private fun showRecordDialog() {
        try {
            val builder = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            val dialogView = layoutInflater.inflate(R.layout.custom_cancel_dialog, null)

            val tvDialogTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
            val tvDialogMessage = dialogView.findViewById<TextView>(R.id.tvDialogMessage)
            val btnNo = dialogView.findViewById<TextView>(R.id.btnNo)
            val btnYes = dialogView.findViewById<TextView>(R.id.btnYes)

            tvDialogTitle.text = activity?.getString(R.string.notification)
            tvDialogMessage.text = activity?.getString(R.string.title_recording_dialog)

            builder.setView(dialogView)

            val dialog = builder.create()

            btnYes.setOnClickListener {
                startRecordingAudio()
                dialog.dismiss()
            }

            btnNo.setOnClickListener {
                dialog.dismiss()
            }
            dialog.show()
        } catch (e: Exception) {
            setToast(activity, e.message.toString())
        }
    }

    private fun showCancelDialog() {
        try {
            val builder = AlertDialog.Builder(requireContext())
            val dialogView = layoutInflater.inflate(R.layout.custom_cancel_dialog, null)

            val tvDialogTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
            val tvDialogMessage = dialogView.findViewById<TextView>(R.id.tvDialogMessage)
            val btnNo = dialogView.findViewById<TextView>(R.id.btnNo)
            val btnYes = dialogView.findViewById<TextView>(R.id.btnYes)

            tvDialogTitle.text = activity?.getString(R.string.notification)
            tvDialogMessage.text = activity?.getString(R.string.title_recording_canceled)

            builder.setView(dialogView)

            val dialog = builder.create()
            btnYes.setOnClickListener {
                stopRecordingAudio(activity?.getString(R.string.record_canceled).toString())
                File(dirPath + fileName).delete()
                dialog.dismiss()
            }

            btnNo.setOnClickListener {
                dialog.dismiss()
            }
            dialog.show()

        } catch (e: Exception) {
            setToast(activity, e.message.toString())
        }
    }


    private fun showVolumeDialog() {
        try {
            val builder = AlertDialog.Builder(requireContext())
            val dialogView = layoutInflater.inflate(R.layout.dialog_volume_control, null)

            val seekBarMusic = dialogView.findViewById<SeekBar>(R.id.seekBarMusic)
            val seekBarAudio = dialogView.findViewById<SeekBar>(R.id.seekBarAudio)


            // Set progress default ke 70%
            seekBarMusic.progress = (volumeMusic * 100).toInt()
            seekBarAudio.progress = (volumeAudio * 100).toInt()

            // Listener for SeekBar volume music (MediaPlayer)
            seekBarMusic.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    try {
                        volumeMusic = progress / 100f
                        if(mp!=null){
                            mp?.setVolume(volumeMusic, volumeMusic) // Setting volume MediaPlayer
                            DataSession(requireActivity()).saveVolumeMusic(volumeMusic)
                        }
                    }catch (e : Exception){
                        setLog(e.message)
                    }

                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })



            // Listener For SeekBar volume marching bell (SoundPool)
            seekBarAudio.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    try {
                        volumeAudio = progress / 100f
                        MyMusicListener.postVolumeAudio(volumeAudio)
                        DataSession(requireActivity()).saveVolumeAudio(volumeAudio)
                    }catch (e : Exception){
                        setLog(e.message)
                    }

                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })

            // Set custom layout to the dialog
            builder.setView(dialogView)

            // Make dialog dari builder
            val dialog = builder.create()
            dialog.show()


        } catch (e: Exception) {
            setToast(activity, e.message.toString())
        }
    }




    @SuppressLint("SimpleDateFormat")
    private fun startRecordingAudio(){

        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.O||Build.VERSION.SDK_INT == Build.VERSION_CODES.O_MR1) {
            setToast(activity,requireActivity().getString(R.string.device_not_support))
        }else{
            showLayoutStartRecord()

            recordingAudio = true

            // format file name with date
            val pattern = "yyyy.MM.dd_hh.mm.ss"
            val simpleDateFormat = SimpleDateFormat(pattern)
            val date: String = simpleDateFormat.format(Date())

            dirPath = "${activity?.externalCacheDir?.absolutePath}/"
            fileName = "record_${date}.mp3"
            binding.tvTimerView.visibility = View.VISIBLE

            try {
                recorder =  MediaRecorder()
                recorder?.apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                    setOutputFile(dirPath + fileName)
                    prepare()
                    start()
                    setToastInfo(activity,requireActivity().getString(R.string.record_started))
                }
            } catch (e: Exception) {
                // Handle other exceptions
                e.printStackTrace()
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
            } catch (e: Exception) {
                e.printStackTrace()
                setToastError(activity,e.message.toString())
            }
        }
    }

    private fun startBlinking() {
        isBlinking = true
        blinkHandler.post(object : Runnable {
            override fun run() {
                if (isBlinking) {
                    // Toggle visibility
                    binding.tvTimerView.visibility =
                        if (binding.tvTimerView.visibility == View.VISIBLE) View.INVISIBLE else View.VISIBLE
                    // Repeat every 500ms (adjust this value as needed)
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
                        setToastInfo(activity,requireActivity().getString(R.string.record_resumed))
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
                        setToastInfo(activity,message)
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
        setToastSuccess(activity,requireActivity().getString(R.string.record_canceled))
        stopRecordingAudio("")
    }

    @OptIn(DelicateCoroutinesApi::class)
    @SuppressLint("SetTextI18n")
    override fun onOkClicked(filePath: String, filename: String, isChange : Boolean) {
        // add audio record info to database
        if(activity!=null){
            val db = Room.databaseBuilder(requireActivity(), AppDatabase::class.java, "audioRecords").build()

           // val duration = timer.format().split(".")[0]

            stopRecordingAudio("")

            if(isChange){
                val newFile = File("$dirPath$filename.mp3")
                File(dirPath+fileName).renameTo(newFile)
            }

            GlobalScope.launch {
                db.audioRecordDAO().insert(AudioRecord(filename, filePath, Date().time, getFormattedAudioDuration(filePath)))
            }
            setToastSuccess(activity,requireActivity().getString(R.string.record_saved))
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

            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    mp = MediaPlayer()
                    mp?.apply {
                        setDataSource(requireActivity(), Uri.parse(filePath))
                        setVolume(volumeMusic, volumeMusic)
                        setOnPreparedListener {
                            mp?.start()
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
                    try {
                        MyStopSDKMusicListener.postAction(true)
                        MyStopMusicListener.postAction(true)
                        MyPauseListener.showButtonStop(false)
                        showBtnStop = false
                        setToastError(activity, e.message.toString())
                    } catch (e: Exception) {
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
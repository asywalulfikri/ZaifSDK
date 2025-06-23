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
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sound.recorder.widget.R
import sound.recorder.widget.base.BaseFragmentWidget
import sound.recorder.widget.builder.ZaifSDKBuilder
import sound.recorder.widget.databinding.WidgetRecordHorizontalZaifBinding
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


class VoiceRecordFragmentHorizontalZaif : BaseFragmentWidget(),SharedPreferences.OnSharedPreferenceChangeListener, BottomSheet.OnClickListener{

    private lateinit var handler: Handler
   // private var _binding: WidgetRecordHorizontalZaifBinding? = null
   // private val binding get() = _binding!!
    private var binding : WidgetRecordHorizontalZaifBinding? =null
    private val blinkHandler = Handler(Looper.getMainLooper())
    private var isBlinking = false

    private var volumeMusic: Float = 1.0f // Volume default 100% for MediaPlayer
    private var volumeAudio: Float = 1.0f // Volume default 100% for SoundPool

    private val musicViewModel: MusicViewModel by activityViewModels()
    val pattern = "yyyy.MM.dd_hh.mm.ss"
    @SuppressLint("SimpleDateFormat")
    val simpleDateFormat = SimpleDateFormat(pattern)
    val date: String = simpleDateFormat.format(Date())


    companion object {
        fun newInstance(): VoiceRecordFragmentHorizontalZaif {
            return VoiceRecordFragmentHorizontalZaif()
        }
    }

   /* override fun onCreate(savedInstanceState: Bundle?) {
        newInstance()
        val b = Bundle()
        super.onCreate(b)
    }*/


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)  // Use savedInstanceState Android
    }


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
       // _binding = WidgetRecordHorizontalZaifBinding.inflate(inflater, container, false)
        binding = WidgetRecordHorizontalZaifBinding.inflate(inflater,container,false)
        return binding?.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (activity != null && context != null) {
            viewLifecycleOwner.lifecycleScope.launch {

                val safeContext = requireContext()
                withContext(Dispatchers.IO) {
                    dataSession = DataSession(safeContext)
                    sharedPreferences = dataSession.getShared()
                    volumeMusic = dataSession.getVolumeMusic()
                    volumeAudio = dataSession.getVolumeAudio()
                    zaifSDKBuilder = ZaifSDKBuilder.builder(safeContext).loadFromSharedPreferences()
                    handler = Handler(Looper.getMainLooper())

                }


                if(dataSession.showTooltip()){
                    if(dataSession.isDoneTooltip()==false) {
                        try {
                            showTooltipSequence(binding)
                        }catch (e : Exception){
                            setLog(e.message)
                        }
                    }
                }


                try {
                    setupView()
                }catch (e : Exception){
                    print(e.message)
                }
            }
        }
    }


    private fun setupView() {

        //setup Item Widget like visibility and color
        setupWidget(zaifSDKBuilder,binding)


        musicViewModel.setRecord.observe(viewLifecycleOwner) { isRecord ->
            isRecord?.let {
                if(isRecord){
                    if(musicViewModel.isPause){
                        showLayoutPauseRecord()
                    }else{
                        if(musicViewModel.recorder==null){
                            showLayoutStopRecord()
                        }else{
                            showLayoutStartRecord()
                        }
                    }
                }else{
                    showLayoutStopRecord()
                }
            }
        }

        musicViewModel.pauseRecord.observe(viewLifecycleOwner) { isPause ->
            isPause?.let {
                if(isPause){
                    try {
                        setToastTic(Toastic.SUCCESS,requireContext().getString(R.string.record_paused).toString())
                        showLayoutPauseRecord()
                    }catch (e : Exception){
                        print(e.message)
                    }
                }
            }
        }


        musicViewModel.resumeRecord.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { isResume ->
                if(isResume){
                    try {
                        setToastTic(Toastic.SUCCESS,(requireContext().getString(R.string.record_resumed).toString()))
                        showLayoutStartRecord()
                    }catch (e : Exception){
                        print(e.message)
                    }
                }
            }
        }

        musicViewModel.cancelRecord.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { isCancel ->
                if(isCancel){
                    try {
                        setToastTic(Toastic.SUCCESS,(requireContext().getString(R.string.record_canceled).toString()))
                        showLayoutStopRecord()
                    }catch (e : Exception){
                       print(e.message)
                    }
                }
            }
        }

        musicViewModel.stopRecord.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { isStop ->
                if(isStop){
                    showLayoutStopRecord()
                }
            }
        }

        musicViewModel.saveRecord.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { message ->
                try {
                    setToastTic(Toastic.INFO,requireContext().getString(R.string.recorded_saved))
                    showLayoutStopRecord()
                }catch (e : Exception){
                    print(e.message)
                }
            }
        }

        musicViewModel.showToast.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { message ->
                try {
                    setToastTic(Toastic.INFO,message)
                }catch (e : Exception){
                    print(e.message)
                }
            }
        }

        binding?.ivRecord?.setOnClickListener {
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.O || Build.VERSION.SDK_INT == Build.VERSION_CODES.O_MR1) {
                try {
                    setToast(activity?.getString(R.string.device_not_support).toString())
                }catch (e : Exception){
                    print(e.message)
                }
            } else {
                if(musicViewModel.isPause){
                    musicViewModel.resumeRecord()
                }else{
                    startPermission()
                }
            }

        }

        binding?.ivPause?.setOnClickListener {
            musicViewModel.pauseRecord()
        }

        //action for save record
        binding?.ivDone?.setOnClickListener {
            try {
                musicViewModel.stopRecord()
                showBottomSheet()
            } catch (e: Exception) {
                setToast(e.message.toString())
            }
        }

        binding?.ivListRecord?.setOnClickListener {
            activity?.let {
                try {
                    findNavController().navigate(R.id.action_widget_to_list_record)
                    MyAdsListener.setBannerHome(false)
                } catch (e: Exception) {
                    setToast(e.message.toString())
                }
            } ?: setToast("Activity is not available")
        }

        binding?.ivDelete?.setOnClickListener {
            try {
                showCancelDialog()
            } catch (e: Exception) {
                setToast(e.message.toString())
            }
        }

        binding?.ivSong?.setOnClickListener {
            startPermissionSong()
        }



        binding?.ivNote?.setOnClickListener {
            binding?.ivNote?.isEnabled = false // 🔒 disable click

            activity?.let {
                try {
                    val bottomSheet = BottomSheetNote()
                    bottomSheet.show(it.supportFragmentManager, LOG_TAG)
                } catch (e: Exception) {
                    setToast(e.message.toString())
                } finally {
                    binding?.ivNote?.postDelayed({
                        binding?.ivNote?.isEnabled = true // 🔓 enable after delay
                    }, 500)
                }
            } ?: run {
                binding?.ivNote?.isEnabled = true
                setToast("Activity is not available")
            }
        }


        binding?.ivVolume?.setOnClickListener {
            showVolumeDialog()
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            blinkHandler.removeCallbacksAndMessages(null)
            musicViewModel.releaseMediaPlayerOnDestroy()
            musicViewModel.stopRecord()
            sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        }catch (e : Exception){
           print(e.message)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try {
            stopBlinking()
            blinkHandler.removeCallbacksAndMessages(null)  // Add this to make sure no callback delay
           // _binding = null
            binding = null
        }catch (e : Exception){
           print(e.message)
        }
    }

    private fun showBottomSheetSong(){
        try {
            if(activity!=null){
                findNavController().navigate(R.id.action_widget_to_list_song)
                MyAdsListener.setBannerHome(false)
            }
        }catch (e : Exception){
            setLog(e.message)
        }
    }


    private fun startPermission(){
        try {
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
        }catch (e : Exception){
            print(e.message)
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
        binding?.ivRecord?.visibility = View.GONE
        binding?.ivPause?.visibility = View.VISIBLE
        binding?.ivDone?.visibility = View.VISIBLE
        binding?.ivDelete?.visibility = View.VISIBLE
        binding?.ivDelete?.isClickable = true

        startBlinking()
    }


    @SuppressLint("SetTextI18n")
    private fun showLayoutPauseRecord(){
        binding?.ivRecord?.setImageResource(0)
        binding?.ivRecord?.setImageResource(R.drawable.play_white)
        binding?.ivRecord?.visibility = View.VISIBLE
        binding?.ivPause?.visibility = View.GONE
        binding?.ivDone?.visibility = View.VISIBLE
        binding?.ivDelete?.visibility = View.VISIBLE

        stopBlinking()

    }

    private fun stopBlinking() {
        try {
            isBlinking = false
            blinkHandler.removeCallbacksAndMessages(null)
            binding?.tvTimerView?.visibility = View.GONE
        }catch (e : Exception){
            print(e.message)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun showLayoutStopRecord(){
        binding?.ivRecord?.visibility = View.VISIBLE
        binding?.ivRecord?.setImageResource(0)
        binding?.ivRecord?.setImageResource(R.drawable.record)
        binding?.ivPause?.visibility = View.GONE
        binding?.ivDone?.visibility = View.GONE
        binding?.ivDelete?.isClickable = false
        binding?.ivDelete?.visibility = View.GONE

        try {
            stopBlinking()
        }catch (e: Exception) {
            setToastError(activity,e.message.toString())
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


    private  fun showRecordDialog(){
        try {
            if (!isAdded) return
            DialogUtils().showRecordDialog(
                context = requireContext(),
                title = activity?.getString(R.string.information).toString(),
                message = activity?.getString(R.string.title_recording_dialog).toString(),
                onYesClick = {
                    dirPath = "${activity?.externalCacheDir?.absolutePath}/"
                    fileName = "record_${date}.mp3"
                    musicViewModel.recordAudioStart(fileName,dirPath)
                    setToastTic(Toastic.SUCCESS,requireContext().getString(R.string.record_started))
                }
            )
        }catch (e : Exception){
            setToast(e.message.toString())
        }
    }




    private val blinkRunnable = object : Runnable {
        @SuppressLint("UseKtx")
        override fun run() {
            try {
                if (isBlinking) {
                    binding?.tvTimerView?.visibility =
                        if (binding?.tvTimerView?.visibility == View.VISIBLE) View.INVISIBLE else View.VISIBLE
                    blinkHandler.postDelayed(this, 500)
                }
            }catch (e : Exception){
                e.message
            }
        }
    }

    private fun startBlinking() {
        try {
            if (isBlinking) return
            isBlinking = true
            blinkHandler.post(blinkRunnable)
        }catch (e : Exception){
            print(e.message)
        }
    }


    private fun showBottomSheet(){
        try {
            val bottomSheet = BottomSheet(dirPath, fileName, this)
            bottomSheet.show(requireActivity().supportFragmentManager, LOG_TAG)
        }catch (e : Exception){
            print(e.message)
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onCancelClicked() {
        try {
            musicViewModel.cancelRecord()
        }catch (e : Exception){
            setLog(e.message)
        }
    }


    @SuppressLint("SetTextI18n")
    override fun onOkClicked(filePath: String, newName: String, isChange: Boolean) {
        if (activity != null) {
            musicViewModel.saveRecord(requireActivity(),dirPath,filePath,fileName,newName,isChange)
        }
    }

    override fun onPause() {
        super.onPause()
        sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
    }


    override fun onResume() {
        super.onResume()
        sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if(key==Constant.KeyShared.volume){
            try {
                val progress = sharedPreferences?.getInt(Constant.KeyShared.volume,100)
                val volume = (1 - ln((ToneGenerator.MAX_VOLUME - progress!!).toDouble()) / ln(
                    ToneGenerator.MAX_VOLUME.toDouble())).toFloat()
                musicViewModel.setVolume(volume)
            }catch (e : Exception){
                print(e.message)
            }
        }
    }
}
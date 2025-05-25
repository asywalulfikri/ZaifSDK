package sound.recorder.widget.ui.viewmodel

// MusicViewModel.kt
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sound.recorder.widget.MyApp
import sound.recorder.widget.R
import sound.recorder.widget.db.AppDatabase
import sound.recorder.widget.db.AudioRecord
import sound.recorder.widget.listener.MyMusicListener
import sound.recorder.widget.listener.MyPauseListener
import sound.recorder.widget.listener.MyStopMusicListener
import sound.recorder.widget.listener.MyStopSDKMusicListener
import sound.recorder.widget.util.Toastic
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

class MusicViewModel : ViewModel() {

    private var mediaPlayer: MediaPlayer? = null
    var recorder: MediaRecorder? = null

    private lateinit var handler: Handler

    private val _currentPosition = MutableLiveData<Int>()
    val currentPosition: LiveData<Int> = _currentPosition

    private val _duration = MutableLiveData<Int>()
    val duration: LiveData<Int> = _duration

    private val _pauseRequest= MutableLiveData<Boolean?>()
    val pauseRequest: LiveData<Boolean?> = _pauseRequest


    // handle stop record
    private val _stopRecord= MutableLiveData< Event<Boolean?>>()
    val stopRecord: LiveData<Event<Boolean?>> = _stopRecord

    // handle canceled record
    private val _cancelRecord= MutableLiveData< Event<Boolean?>>()
    val cancelRecord: LiveData<Event<Boolean?>> = _cancelRecord

    // handle saveRecord observer
    private val _saveRecord = MutableLiveData<Event<String>>()
    val saveRecord: LiveData<Event<String>> = _saveRecord

    // handle pausesRecord observer
    private val _pauseRecord= MutableLiveData<Boolean?>()
    val pauseRecord: LiveData<Boolean?> = _pauseRecord


    var isPause: Boolean = false
        private set

    // handel toast in observer
    private val _showToast= MutableLiveData<Event<String?>>()
    val showToast: LiveData<Event<String?>> = _showToast

    private val _resumeRecord= MutableLiveData< Event<Boolean?>>()
    val resumeRecord: LiveData<Event<Boolean?>> = _resumeRecord


    private val _completeRequest= MutableLiveData<Boolean?>()
    val completeRequest: LiveData<Boolean?> = _completeRequest

    private var volumeMusic: Float = 1.0f // Volume default 100% for MediaPlayer
    private var volumeAudio: Float = 1.0f // Volume default 100% for SoundPool


    fun updateProgress(position: Int) {
        _currentPosition.postValue(position)
    }


    fun setDuration(duration: Int) {
        _duration.postValue(duration)
    }


    fun pauseRequest(isPause: Boolean) {
        _pauseRequest.postValue(isPause)
    }

    fun completeRequest(isComplete: Boolean) {
        _completeRequest.postValue(isComplete)
    }

    //action for send to observer
    fun showToast(message: String) {
       viewModelScope.launch {
           try {
               withContext(Dispatchers.Main) {
                   _showToast.value = Event(message)
               }
           }catch (e : Exception){
               setLog(e.message)
           }
       }
    }


    private val _isPlaying = MutableLiveData<Pair<Boolean, Boolean>>()



    val isPlaying = MediatorLiveData<Boolean>().apply {
        addSource(_isPlaying) { value = it.first }
    }

    fun setIsPlaying(playing: Boolean, isActive: Boolean) {
        _isPlaying.postValue(Pair(playing, isActive))
    }

    val isActive = MediatorLiveData<Boolean>().apply {
        addSource(_isPlaying) { value = it.second }
    }


    private val _setRecord = MutableLiveData<Boolean?>()
    val setRecord: LiveData<Boolean?> = _setRecord



    @SuppressLint("UseKtx")
    fun playMusic(context: Context, filePath: String) {
        handler = Handler(Looper.getMainLooper())
        stopMusic()
        try {
            mediaPlayer = MediaPlayer()
            mediaPlayer?.apply {
                setDataSource(context, Uri.parse(filePath))
                setVolume(volumeMusic, volumeMusic)
                setOnPreparedListener {
                    start()
                    MyMusicListener.postAction(mediaPlayer)
                    MyStopSDKMusicListener.onStartAnimation()
                    setDuration(duration)
                    setIsPlaying(true, true)
                    handler.post(updateProgressRunnable)
                }
                prepareAsync()
                setOnCompletionListener {
                    MyStopSDKMusicListener.postAction(true)
                    MyStopMusicListener.postAction(true)
                    MyPauseListener.showButtonStop(false)
                    setIsPlaying(false, false)
                }
                MyPauseListener.showButtonStop(true)
            }
            }catch (e: Exception) {
            MyStopSDKMusicListener.postAction(true)
            MyStopMusicListener.postAction(true)
            MyPauseListener.showButtonStop(false)
        }

    }

    fun pauseMusic() {
        if(mediaPlayer!=null){
            mediaPlayer?.pause()
            setIsPlaying(false,true)
            //pauseRequest(true)
        }
    }

    fun resumeMusic(){
        viewModelScope.launch {

        }
        if(mediaPlayer!=null){
            mediaPlayer?.apply {
                start()
                setDuration(duration)
                setIsPlaying(true,true)
                handler.post(updateProgressRunnable)
            }
        }
    }

    fun stopMusic() {
        viewModelScope.launch {
            try {
                mediaPlayer?.apply {
                    stop()
                    reset()
                    release()
                    mediaPlayer = null
                    setIsPlaying(false,false)
                    MyPauseListener.showButtonStop(false)
                    MyMusicListener.postAction(null)
                    MyStopMusicListener.postAction(true)
                }
            } catch (e: Exception) {
                setLog(e.message)
            }
        }
    }


    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    updateProgress(it.currentPosition)
                    handler.postDelayed(this, 500)
                }
            }
        }
    }

    fun seekbarMusic(position : Int){
        viewModelScope.launch {
            if(mediaPlayer!=null){
                mediaPlayer?.seekTo(position)
            }
        }
    }


    fun releaseMediaPlayerOnDestroy(){
        viewModelScope.launch {
            if(mediaPlayer!=null){
                mediaPlayer?.apply {
                    try {
                        release()
                    } catch (e: Exception) {
                        setLog(e.message)
                    } finally {
                        mediaPlayer = null
                    }

                    MyMusicListener.setMyListener(null)

                    //release stop listener
                    MyStopSDKMusicListener.setMyListener(null)
                    MyStopMusicListener.setMyListener(null)

                    //release pause listener
                    MyPauseListener.showButtonStop(false)
                    MyPauseListener.setMyListener(null)
                    handler.removeCallbacks(updateProgressRunnable)
                }
            }
        }
    }

    fun recordAudioStart(fileName: String, dirPath: String) {
        viewModelScope.launch {
            try {
                val dir = File(dirPath)
                if (!dir.exists()) dir.mkdirs()

                val outputFile = "$dirPath$fileName"
                recorder = MediaRecorder().apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                    setOutputFile(outputFile)

                    try {
                        withContext(Dispatchers.IO) {
                            prepare() // sync  di background
                        }
                        start() // Start main thread

                        _setRecord.postValue(true) // Notify UI
                    } catch (e: Exception) {
                        setLog(e.message)
                        showToast(e.message ?: "Prepare/start error")
                    }
                }
            } catch (e: Exception) {
                setLog(e.message)
                showToast(e.message ?: "Recorder init error")
            }
        }
    }


    fun pauseRecord(){
        viewModelScope.launch{
            if (recorder != null) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        recorder?.pause()
                        withContext(Dispatchers.Main) {
                            _pauseRecord.postValue(true)
                        }

                        isPause = true
                    } else {
                        showToast(MyApp.getApplicationContext().getString(R.string.device_not_support).toString())
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    setLog(e.message)
                }
            }
        }
    }

    fun resumeRecord(){
        viewModelScope.launch {
            if(recorder!=null){
                try {
                    recorder?.apply {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            resume()
                            withContext(Dispatchers.Main) {
                                _resumeRecord.value = Event(true)
                                _pauseRecord.postValue(false)
                            }
                            isPause = false
                        }
                    }

                }catch (e: Exception) {
                    e.printStackTrace()
                    setLog(e.message)
                }

            }
        }
    }


    fun stopRecord() {
        viewModelScope.launch {
            try {
                recorder?.apply {
                    stop()
                    reset()
                    release()
                }
                recorder = null

                withContext(Dispatchers.Main) {
                    _stopRecord.value = Event(true)
                    _setRecord.postValue(false)
                }

            } catch (e: Exception) {
               setLog(e.message)
            }
        }
    }

    fun cancelRecord() {
        viewModelScope.launch {
            try {
                recorder?.let {
                    it.stop()
                    it.reset()
                    it.release()
                    recorder = null
                    withContext(Dispatchers.Main) {
                        _cancelRecord.value = Event(true)
                    }
                }
            } catch (e: Exception) {
                setLog(e.message)
            }
        }
    }



    fun saveRecord(activity: Activity, dirPath : String, filePath: String, originalName : String,newName: String, isChange: Boolean) {
        viewModelScope.launch {
            try {
                // Inisialisasi database (bisa dipindah ke singleton untuk efisiensi)
                val db = Room.databaseBuilder(
                    activity,
                    AppDatabase::class.java,
                    "audioRecords"
                ).build()

                // Rename file jika diperlukan
                if (isChange) {
                    val newFile = File("$dirPath$newName.mp3")
                    File(dirPath+originalName).renameTo(newFile)
                }

                // Ambil durasi audio di thread IO
                val durationFormatted = withContext(Dispatchers.IO) {
                    getFormattedAudioDuration(filePath)
                }

                withContext(Dispatchers.IO) {
                    db.audioRecordDAO().insert(
                        AudioRecord(
                            newName,
                            filePath,
                            Date().time,
                            durationFormatted
                        )
                    )
                }

                withContext(Dispatchers.Main) {
                    _saveRecord.value = Event(
                        MyApp.getApplicationContext().getString(R.string.record_saved)
                    )
                    isPause = false
                }

            } catch (e: Exception) {
                setLog(e.message)
            }
        }
    }




    @SuppressLint("DefaultLocale")
    fun getFormattedAudioDuration(filePath: String): String {

        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(filePath)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()

            val durationMillis = durationStr?.toLong() ?: 0L

            // Convert millisecond to  minutes and second
            val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMillis)
            val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMillis) % 60

            return String.format("%02d:%02d", minutes, seconds)

        }catch (e : Exception){
            Log.d("error",e.message.toString())
        }
        return ""
    }

    fun setLog(message : String? =null){
        Log.e("error_sdk", message ?: "Unknown error")
    }

}

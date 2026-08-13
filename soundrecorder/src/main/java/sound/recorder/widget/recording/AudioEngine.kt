package sound.recorder.widget.recording


import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

class AudioEngine(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var audioPlayer: MediaPlayer? = null
    private val syncHandler = Handler(Looper.getMainLooper())

    var currentAudioFile: File? = null
        private set

    // ─── EARPHONE CHECK ───
    fun isEarphonePlugged(): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { device ->
                device.type in listOf(
                    AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                    AudioDeviceInfo.TYPE_USB_HEADSET,
                    AudioDeviceInfo.TYPE_USB_DEVICE
                )
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.isWiredHeadsetOn || audioManager.isBluetoothA2dpOn
        }
    }

    // ─── MIC RECORDER ───
    fun startMicRecording() {
        val fileName = "REC_${System.currentTimeMillis()}.mp3"
        currentAudioFile = File(context.filesDir, fileName)
        val outputPath = currentAudioFile?.absolutePath
        // prepare()/start() melakukan I/O dan setup encoder secara sinkron,
        // jalankan di background agar tidak memblokir main thread (potensi ANR).
        CoroutineScope(Dispatchers.IO).launch {
            try {
                mediaRecorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(context)
                } else {
                    @Suppress("DEPRECATION")
                    MediaRecorder()
                }).apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setOutputFile(outputPath)
                    try {
                        prepare()
                        start()
                    } catch (e: IOException) {
                        Log.e("AudioEngine", "MediaRecorder prepare/start failed: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e("AudioEngine", "MediaRecorder init failed: ${e.message}")
            }
        }
    }

    fun stopMicRecording() {
        val recorder = mediaRecorder
        mediaRecorder = null
        CoroutineScope(Dispatchers.IO).launch {
            try {
                recorder?.apply { stop(); release() }
            } catch (e: Exception) {
                Log.e("AudioEngine", "Stop mic failed: ${e.message}")
            }
        }
    }

    // ─── MEDIA PLAYER ───
    fun startPlayingAudioSync(path: String, onComplete: (() -> Unit)? = null) {
        stopPlayingAudio()
        val prepareStartTime = System.currentTimeMillis()
        try {
            audioPlayer = MediaPlayer().apply {
                setDataSource(path)
                setOnPreparedListener { mp ->
                    val prepareElapsed = System.currentTimeMillis() - prepareStartTime
                    Log.d("AudioEngine", "prepareElapsed=${prepareElapsed}ms")
                    try { mp.seekTo(prepareElapsed.toInt()) } catch (e: Exception) {
                        Log.e("AudioEngine", "seekTo failed: ${e.message}")
                    }
                    mp.start()
                }
                setOnCompletionListener { stopPlayingAudio(); onComplete?.invoke() }
                setOnErrorListener { _, what, extra ->
                    Log.e("AudioEngine", "MediaPlayer error: what=$what extra=$extra")
                    stopPlayingAudio(); onComplete?.invoke(); true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e("AudioEngine", "Failed to play audio: ${e.message}")
            onComplete?.invoke()
        }
    }

    fun stopPlayingAudio() {
        syncHandler.removeCallbacksAndMessages(null)
        val player = audioPlayer
        audioPlayer = null
        CoroutineScope(Dispatchers.IO).launch {
            try {
                player?.apply { if (isPlaying) stop(); release() }
            } catch (e: Exception) {
                Log.e("AudioEngine", "Failed to stop audio player: ${e.message}")
            }
        }
    }

    fun release() {
        stopMicRecording()
        stopPlayingAudio()
    }
}
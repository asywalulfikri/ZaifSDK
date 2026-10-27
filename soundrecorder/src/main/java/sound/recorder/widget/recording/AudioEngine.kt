package sound.recorder.widget.recording


import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

class AudioEngine(private val context: Context) {

    @Volatile private var mediaRecorder: MediaRecorder? = null
    private var audioPlayer: MediaPlayer? = null
    private val syncHandler = Handler(Looper.getMainLooper())
    private val audioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private var recordingJob: Job? = null
    @Volatile private var recordingRequested = false

    @Volatile
    var currentAudioFile: File? = null
        private set

    /**
     * Waktu (SystemClock.elapsedRealtime) saat mic benar-benar mulai merekam, 0 jika belum/gagal.
     * prepare()/start() jalan di background, jadi mic mulai lebih lambat dari tombol rekam — dipakai untuk sinkron ketukan.
     */
    @Volatile
    var micStartedAtElapsed: Long = 0L
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
    fun startMicRecording(onError: (() -> Unit)? = null) {
        stopMicRecording()
        recordingRequested = true
        micStartedAtElapsed = 0L
        val stamp = System.currentTimeMillis()
        // Path awal = format kualitas tinggi; diganti ke .3gp kalau device harus pakai fallback
        currentAudioFile = File(context.filesDir, "REC_$stamp.m4a")
        // prepare()/start() melakukan I/O dan setup encoder secara sinkron,
        // jalankan di background agar tidak memblokir main thread (potensi ANR).
        recordingJob = audioScope.launch {
            var recorder: MediaRecorder? = null
            try {
                val hqFile = File(context.filesDir, "REC_$stamp.m4a")
                recorder = try {
                    openRecorder(hqFile, highQuality = true)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Sebagian device tidak mendukung 44.1 kHz / bitrate ini → pakai setelan lama (pasti jalan seperti dulu)
                    Log.w("AudioEngine", "HQ recorder failed, fallback to legacy: ${e.message}")
                    hqFile.delete()
                    val legacyFile = File(context.filesDir, "REC_$stamp.3gp")
                    currentAudioFile = legacyFile
                    openRecorder(legacyFile, highQuality = false)
                }
                if (recordingRequested) {
                    mediaRecorder = recorder
                } else {
                    recorder.release()
                    recorder = null
                }
            } catch (_: CancellationException) {
                // Cancellation is expected when recording is stopped immediately.
            } catch (e: Exception) {
                Log.e("AudioEngine", "MediaRecorder init failed: ${e.message}")
                if (recordingRequested) {
                    syncHandler.post { onError?.invoke() }
                }
            } finally {
                // Prevent a recorder created before cancellation/failure from leaking.
                if (recorder != null && mediaRecorder !== recorder) {
                    try { recorder?.release() } catch (_: Exception) { }
                }
            }
        }
    }

    /** prepare + start (jika masih diminta). Gagal di tahap mana pun → recorder di-release lalu dilempar. */
    private fun openRecorder(file: File, highQuality: Boolean): MediaRecorder {
        val recorder = prepareRecorder(file, highQuality)
        if (recordingRequested) {
            try {
                recorder.start()
                micStartedAtElapsed = SystemClock.elapsedRealtime()
            } catch (e: Exception) {
                try { recorder.release() } catch (_: Exception) { }
                throw e
            }
        }
        return recorder
    }

    /**
     * highQuality: AAC 44.1 kHz 128 kbps mono dalam .m4a — cocok untuk alat musik (nada tinggi pianika/angklung tidak hilang).
     * Legacy: 3GP + AAC tanpa setelan (default device, sering 8 kHz) — persis seperti versi lama.
     * Recorder yang gagal di-release di sini supaya tidak bocor sebelum fallback.
     */
    private fun prepareRecorder(file: File, highQuality: Boolean): MediaRecorder {
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        try {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            if (highQuality) {
                recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                recorder.setAudioSamplingRate(44_100)
                recorder.setAudioEncodingBitRate(128_000)
                recorder.setAudioChannels(1)
            } else {
                recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            }
            recorder.setOutputFile(file.absolutePath)
            recorder.prepare()
            return recorder
        } catch (e: Exception) {
            try { recorder.release() } catch (_: Exception) { }
            throw e
        }
    }

    fun stopMicRecording() {
        recordingRequested = false
        recordingJob?.cancel()
        recordingJob = null
        val recorder = mediaRecorder
        mediaRecorder = null
        audioScope.launch {
            try {
                recorder?.apply { stop(); release() }
            } catch (e: Exception) {
                Log.e("AudioEngine", "Stop mic failed: ${e.message}")
            }
        }
    }

    // ─── MEDIA PLAYER ───
    fun startPlayingAudioSync(path: String, onComplete: (() -> Unit)? = null) =
        startPlayingAudioSync(path, onComplete, null)

    /**
     * @param onStarted dipanggil (main thread) tepat setelah audio mulai diputar — mulai putar ketukan di sini
     * supaya tidak mendahului audio selama prepareAsync().
     */
    fun startPlayingAudioSync(path: String, onComplete: (() -> Unit)?, onStarted: (() -> Unit)?) =
        startPlayingAudioSync(path, 0, onComplete, onStarted)

    /** @param startMs mulai dari posisi ini (pratinjau potongan); audio di-seek dulu sebelum diputar. */
    fun startPlayingAudioSync(path: String, startMs: Int, onComplete: (() -> Unit)?, onStarted: (() -> Unit)?) {
        stopPlayingAudio()
        val prepareStartTime = System.currentTimeMillis()
        syncHandler.post {
            try {
                audioPlayer = MediaPlayer().apply {
                    setDataSource(path)
                    setOnPreparedListener { mp ->
                        val prepareElapsed = System.currentTimeMillis() - prepareStartTime
                        Log.d("AudioEngine", "prepareElapsed=${prepareElapsed}ms")
                        // Sudah di-stop selama prepare → jangan mulai audio / ketukan
                        if (audioPlayer !== mp) return@setOnPreparedListener
                        if (startMs > 0) {
                            mp.setOnSeekCompleteListener { p ->
                                p.setOnSeekCompleteListener(null)
                                if (audioPlayer !== p) return@setOnSeekCompleteListener
                                p.start()
                                onStarted?.invoke()
                            }
                            mp.seekTo(startMs)
                        } else {
                            mp.start()
                            onStarted?.invoke()
                        }
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
    }

    fun stopPlayingAudio() {
        syncHandler.removeCallbacksAndMessages(null)
        val player = audioPlayer
        audioPlayer = null
        syncHandler.post {
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

package sound.recorder.widget.music

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import kotlinx.coroutines.*
import java.io.File

object MusicPlayerManager {

    private const val PREFS_NAME = "music_player_prefs"
    private const val KEY_MUSIC_VOLUME = "music_volume"
    private const val DEFAULT_VOLUME = 0.7f

    data class MusicTrack(
        val title: String,
        val duration: Long,
        val isRaw: Boolean,
        val rawResId: Int = 0,
        val deviceUri: Uri? = null,
        // Tambahkan path string untuk memudahkan mapping dari database
        val path: String? = null
    )

    interface PlayerListener {
        fun onPlay(track: MusicTrack)
        fun onPause()
        fun onStop()
        fun onProgress(current: Int, max: Int)
        fun onComplete()
    }

    private var mediaPlayer: MediaPlayer? = null
    private var currentTrack: MusicTrack? = null
    private var listener: PlayerListener? = null
    private var progressJob: Job? = null

    private var _isPlaying = false
    val isPlaying: Boolean get() = _isPlaying

    private var _isPaused = false
    val isPaused: Boolean get() = _isPaused

    private var currentVolume: Float = DEFAULT_VOLUME

    fun setListener(l: PlayerListener?) {
        listener = l
    }

    // ── Volume API ────────────────────────────────────────────────────────────

    fun setVolume(left: Float, right: Float) {
        val safeLeft  = left.coerceIn(0f, 1f)
        val safeRight = right.coerceIn(0f, 1f)
        currentVolume = safeLeft
        mediaPlayer?.setVolume(safeLeft, safeRight)
    }

    fun getVolume(): Float = currentVolume

    // ── SharedPreferences helpers ─────────────────────────────────────────────

    @SuppressLint("UseKtx")
    private fun saveMusicVolume(context: Context, volume: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_MUSIC_VOLUME, volume)
            .apply()
    }

    fun loadMusicVolume(context: Context): Float {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_MUSIC_VOLUME, DEFAULT_VOLUME)
    }

    fun saveAndSetVolume(context: Context, volume: Float) {
        val safe = volume.coerceIn(0f, 1f)
        currentVolume = safe
        mediaPlayer?.setVolume(safe, safe)
        saveMusicVolume(context, safe)
    }

    // ── Playback ──────────────────────────────────────────────────────────────

    /**
     * Fungsi Tambahan: Memutar musik langsung dari Path (untuk Playback Cover)
     */
    fun playFromPath(context: Context, path: String) {
        val file = File(path)
        if (file.exists()) {
            val uri = Uri.fromFile(file)
            val track = MusicTrack(
                title = file.name,
                duration = 0, // Akan terupdate saat prepare
                isRaw = false,
                deviceUri = uri,
                path = path
            )
            play(context, track)
        }
    }

    fun play(context: Context, track: MusicTrack) {
        stop()
        currentTrack = track
        currentVolume = loadMusicVolume(context)

        try {
            mediaPlayer = if (track.isRaw) {
                MediaPlayer.create(context, track.rawResId)
            } else {
                MediaPlayer().apply {
                    setDataSource(context, track.deviceUri!!)
                    prepare()
                }
            }

            mediaPlayer?.apply {
                setVolume(currentVolume, currentVolume)
                start()
                _isPlaying = true
                _isPaused = false

                listener?.onPlay(track)

                setOnCompletionListener {
                    _isPlaying = false
                    _isPaused = false
                    progressJob?.cancel()
                    listener?.onComplete()
                    stop()
                }

                startProgressTracking()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun pause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                _isPlaying = false
                _isPaused = true
                listener?.onPause()
                progressJob?.cancel()
            }
        }
    }

    fun resume() {
        mediaPlayer?.let {
            if (_isPaused) {
                it.start()
                it.setVolume(currentVolume, currentVolume)
                _isPlaying = true
                _isPaused = false
                currentTrack?.let { track -> listener?.onPlay(track) }
                startProgressTracking()
            }
        }
    }

    fun stop() {
        progressJob?.cancel()
        progressJob = null

        mediaPlayer?.apply {
            try {
                if (isPlaying) stop()
            } catch (e: Exception) {}
            release()
        }

        mediaPlayer = null
        _isPlaying = false
        _isPaused = false
        currentTrack = null
        listener?.onStop()
    }

    fun seekTo(ms: Int) {
        try {
            mediaPlayer?.seekTo(ms)
            if (!_isPlaying) {
                listener?.onProgress(ms, getDuration())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getCurrentTrack(): MusicTrack? = currentTrack
    fun getCurrentPosition(): Int = mediaPlayer?.currentPosition ?: 0
    fun getDuration(): Int = mediaPlayer?.duration ?: 0

    private fun startProgressTracking() {
        progressJob?.cancel()
        progressJob = CoroutineScope(Dispatchers.Main).launch {
            while (_isPlaying) {
                val current = mediaPlayer?.currentPosition ?: 0
                val max = mediaPlayer?.duration ?: 0
                if (max > 0) listener?.onProgress(current, max)
                delay(500)
            }
        }
    }

    fun release() {
        stop()
        listener = null
    }
}
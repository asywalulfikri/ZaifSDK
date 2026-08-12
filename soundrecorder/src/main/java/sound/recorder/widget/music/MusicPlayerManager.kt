package sound.recorder.widget.music

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.*

object MusicPlayerManager {

    private const val PREFS_NAME = "music_player_prefs"
    private const val KEY_MUSIC_VOLUME = "music_volume"
    private const val DEFAULT_VOLUME = 0.7f

    data class MusicTrack(
        val title: String,
        val duration: Long,
        val isRaw: Boolean,
        val rawResId: Int = 0,
        val deviceUri: Uri? = null
    )

    interface PlayerListener {
        fun onPlay(track: MusicTrack)
        fun onPause()
        fun onStop()
        fun onProgress(current: Int, max: Int)
        fun onComplete()
    }

    private var mediaPlayer: MediaPlayer? = null
    private val listeners = java.util.concurrent.CopyOnWriteArrayList<PlayerListener>()
    private var currentTrack: MusicTrack? = null
    private var progressJob: Job? = null
    private val managerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var _isPlaying = false
    val isPlaying: Boolean get() = _isPlaying

    private var _isPaused = false
    val isPaused: Boolean get() = _isPaused

    // Volume internal (0.0 – 1.0), tidak terhubung ke volume system
    private var currentVolume: Float = DEFAULT_VOLUME

    fun addListener(l: PlayerListener) {
        if (!listeners.contains(l)) {
            Log.d("MusicPlayerManager", "Adding listener: $l")
            listeners.add(l)
        }
    }

    fun removeListener(l: PlayerListener) {
        Log.d("MusicPlayerManager", "Removing listener: $l")
        listeners.remove(l)
    }

    @Deprecated("Use addListener/removeListener", ReplaceWith("addListener(l)"))
    fun setListener(l: PlayerListener?) {
        if (l == null) {
            // Keep it for safety, but usually we don't want to clear everything.
        } else {
            addListener(l)
        }
    }

    // ── Volume API ────────────────────────────────────────────────────────────

    /**
     * Set volume langsung ke MediaPlayer (terpisah dari volume system/device).
     * Nilai antara 0.0 (diam) hingga 1.0 (penuh).
     */
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

    fun play(context: Context, track: MusicTrack) {
        stop()
        currentTrack = track
        currentVolume = loadMusicVolume(context)

        managerScope.launch {
            try {
                val player = withContext(Dispatchers.IO) {
                    if (track.isRaw) {
                        MediaPlayer.create(context, track.rawResId)
                    } else {
                        MediaPlayer().apply {
                            setDataSource(context, track.deviceUri!!)
                            prepare()
                        }
                    }
                }

                mediaPlayer = player
                mediaPlayer?.apply {
                    setVolume(currentVolume, currentVolume)
                    start()
                    _isPlaying = true
                    _isPaused = false

                    listeners.forEach { 
                        try {
                            it.onPlay(track)
                        } catch (e: Exception) {
                            Log.e("MusicPlayerManager", "Error in onPlay listener", e)
                        }
                    }

                    setOnCompletionListener {
                        _isPlaying = false
                        _isPaused = false
                        progressJob?.cancel()
                        listeners.forEach { 
                            try {
                                it.onComplete()
                            } catch (e: Exception) {
                                Log.e("MusicPlayerManager", "Error in onComplete listener", e)
                            }
                        }
                        stop()
                    }

                    startProgressTracking()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    fun pause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                _isPlaying = false
                _isPaused = true
                listeners.forEach { 
                    try {
                        it.onPause()
                    } catch (e: Exception) {
                        Log.e("MusicPlayerManager", "Error in onPause listener", e)
                    }
                }
                progressJob?.cancel()
            }
        }
    }

    fun resume() {
        mediaPlayer?.let {
            if (_isPaused) {
                it.start()
                // Pastikan volume tetap terjaga setelah resume
                it.setVolume(currentVolume, currentVolume)
                _isPlaying = true
                _isPaused = false
                currentTrack?.let { track -> 
                    listeners.forEach { 
                        try {
                            it.onPlay(track)
                        } catch (e: Exception) {
                            Log.e("MusicPlayerManager", "Error in onPlay listener (resume)", e)
                        }
                    } 
                }
                startProgressTracking()
            }
        }
    }

    fun stop() {
        progressJob?.cancel()
        progressJob = null

        val playerToRelease = mediaPlayer
        mediaPlayer = null

        managerScope.launch(Dispatchers.IO) {
            try {
                playerToRelease?.let {
                    if (it.isPlaying) it.stop()
                    it.release()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        _isPlaying = false
        _isPaused = false
        currentTrack = null
        listeners.forEach { 
            try {
                it.onStop()
            } catch (e: Exception) {
                Log.e("MusicPlayerManager", "Error in onStop listener", e)
            }
        }
    }

    fun seekTo(ms: Int) {
        try {
            mediaPlayer?.seekTo(ms)
            if (!_isPlaying) {
                listeners.forEach { 
                    try {
                        it.onProgress(ms, getDuration())
                    } catch (e: Exception) {
                        Log.e("MusicPlayerManager", "Error in onProgress listener (seek)", e)
                    }
                }
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
        progressJob = managerScope.launch {
            while (_isPlaying) {
                val current = mediaPlayer?.currentPosition ?: 0
                val max = mediaPlayer?.duration ?: 0
                if (max > 0) {
                    listeners.forEach { 
                        try {
                            it.onProgress(current, max)
                        } catch (e: Exception) {
                            Log.e("MusicPlayerManager", "Error in onProgress listener", e)
                        }
                    }
                }
                delay(500)
            }
        }
    }

    fun release() {
        stop()
        listeners.clear()
    }
}

package recording.host


import android.content.Context
import android.media.MediaPlayer
import android.media.SoundPool
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlin.apply
import kotlin.collections.set
import kotlin.let
import kotlin.to

// Menggunakan 'object' di Kotlin adalah cara termudah membuat Singleton
object AudioEngine {

    private var soundPool: SoundPool? = null
    private var mediaPlayer: MediaPlayer? = null

    private val instrumentSounds = mutableMapOf<String, MutableMap<String, Int>>()
    private var volume = 1f

    // Status loading
    private var totalSounds = 0
    private var loadedSounds = 0
    private var isInitialized = false

    // LiveData untuk dipantau oleh ViewModel
    private val _loadingProgress = MutableLiveData(0)
    val loadingProgress: LiveData<Int> = _loadingProgress

    private val _isLoaded = MutableLiveData(false)
    val isLoaded: LiveData<Boolean> = _isLoaded

    /**
     * Metode utama untuk memulai semuanya, dipanggil dari MyApplication.
     */
    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true

        // Inisialisasi SoundPool
        soundPool = SoundPool.Builder()
            .setMaxStreams(15) // Bisa ditambah jika perlu
            .build()
            .apply {
                setOnLoadCompleteListener { _, _, _ ->
                    loadedSounds++
                    val progress = if (totalSounds > 0) (loadedSounds * 100) / totalSounds else 0
                    _loadingProgress.postValue(progress)

                    if (loadedSounds >= totalSounds) {
                        _isLoaded.postValue(true)
                    }
                }
            }

        // Mulai memuat semua suara ketukan
        loadAllSounds(context)
    }

    private fun loadAllSounds(context: Context) {
        // Daftar semua suara Anda ada di sini


        loadInstrumentSounds(
            context, "test", listOf(
                "test1" to R.raw.dek,
                "test2" to R.raw.dum,
                )
        )
    }

    private fun loadInstrumentSounds(
        context: Context,
        instrument: String,
        sounds: List<Pair<String, Int>>
    ) {
        val soundMap = mutableMapOf<String, Int>()
        totalSounds += sounds.size
        for ((name, resId) in sounds) {
            val soundId = soundPool!!.load(context, resId, 1)
            soundMap[name] = soundId
        }
        instrumentSounds[instrument] = soundMap
    }

    /**
     * Memainkan suara ketukan dari SoundPool.
     */
    fun playSound(instrument: String, soundName: String) {
        if (isLoaded.value != true) return // Hanya mainkan jika semua sudah dimuat
        val soundId = instrumentSounds[instrument]?.get(soundName)
        soundId?.let {
            soundPool?.play(it, volume, volume, 1, 0, 1f)
        }
    }

    /**
     * Memainkan musik/track panjang dari MediaPlayer.
     */
    fun playAudioTrack(context: Context, resId: Int) {
        try {
            mediaPlayer?.release() // Hentikan yang lama jika ada
            mediaPlayer = MediaPlayer.create(context, resId)?.apply {
                start()
            }
        } catch (e: Exception) {
            // Tangani error
        }
    }

    fun setVolume(newVolume: Float) {
        this.volume = newVolume
    }

    fun release() {
        soundPool?.release()
        mediaPlayer?.release()
        soundPool = null
        mediaPlayer = null
        isInitialized = false
    }
}
package recording.host

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import kotlin.let

class SoundViewModel(application: Application) : AndroidViewModel(application) {

    // ViewModel sekarang hanya mengamati LiveData dari AudioEngine
    val loadingProgress: LiveData<Int> = SoundPlayUtils.loadingProgress
    val isAllSoundsLoaded: LiveData<Boolean> = SoundPlayUtils.isLoaded

    var isPremium: Boolean = false

    var isMusicUnlocked: Boolean = true
    var isRecordUnlocked: Boolean = true
    /**
     * Meneruskan perintah untuk memainkan suara ketukan ke AudioEngine.
     */
    fun playSound(instrument: String, soundName: String) {
        SoundPlayUtils.playSound(instrument, soundName)
    }

    /**
     * Meneruskan perintah untuk memainkan track audio ke AudioEngine.
     */
    fun playAudioTrack(resId: Int) {
        // Menggunakan getApplication() untuk mendapatkan Context yang aman
        SoundPlayUtils.playAudioTrack(getApplication(), resId)
    }

    /**
     * Meneruskan perintah untuk mengatur volume.
     */
    fun setVolume(volume: Float?) {
        volume?.let { SoundPlayUtils.setVolume(it) }
    }

    // onCleared() sekarang tidak perlu melakukan apa-apa terhadap AudioEngine
    override fun onCleared() {
        super.onCleared()
    }
}
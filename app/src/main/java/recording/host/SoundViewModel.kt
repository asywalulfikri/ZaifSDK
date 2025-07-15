package recording.host

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import kotlin.let

class SoundViewModel(application: Application) : AndroidViewModel(application) {

    // ViewModel sekarang hanya mengamati LiveData dari AudioEngine
    val loadingProgress: LiveData<Int> = AudioEngine.loadingProgress
    val isAllSoundsLoaded: LiveData<Boolean> = AudioEngine.isLoaded

    /**
     * Meneruskan perintah untuk memainkan suara ketukan ke AudioEngine.
     */
    fun playSound(instrument: String, soundName: String) {
        AudioEngine.playSound(instrument, soundName)
    }

    /**
     * Meneruskan perintah untuk memainkan track audio ke AudioEngine.
     */
    fun playAudioTrack(resId: Int) {
        // Menggunakan getApplication() untuk mendapatkan Context yang aman
        AudioEngine.playAudioTrack(getApplication(), resId)
    }

    /**
     * Meneruskan perintah untuk mengatur volume.
     */
    fun setVolume(volume: Float?) {
        volume?.let { AudioEngine.setVolume(it) }
    }

    // onCleared() sekarang tidak perlu melakukan apa-apa terhadap AudioEngine
    override fun onCleared() {
        super.onCleared()
    }
}
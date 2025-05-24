package sound.recorder.widget.ui.viewmodel

// MusicViewModel.kt
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class MusicViewModel : ViewModel() {
    private val _currentPosition = MutableLiveData<Int>()
    val currentPosition: LiveData<Int> = _currentPosition

    private val _duration = MutableLiveData<Int>()
    val duration: LiveData<Int> = _duration

    private val _playRequest= MutableLiveData<String?>()
    val playRequest: LiveData<String?> = _playRequest

    private val _stopRequest= MutableLiveData<Boolean?>()
    val stopRequest: LiveData<Boolean?> = _stopRequest

    private val _pauseRequest= MutableLiveData<Boolean?>()
    val pauseRequest: LiveData<Boolean?> = _pauseRequest

    private val _resumeRequest= MutableLiveData<Boolean?>()
    val resumeRequest: LiveData<Boolean?> = _resumeRequest

    private val _completeRequest= MutableLiveData<Boolean?>()
    val completeRequest: LiveData<Boolean?> = _completeRequest


    fun updateProgress(position: Int) {
        _currentPosition.postValue(position)
    }

    fun requestPlaySong(songPath : String){
        _playRequest.postValue(songPath)
    }

    fun setDuration(duration: Int) {
        _duration.postValue(duration)
    }

    fun stopRequest(isStop : Boolean){
        _stopRequest.postValue(isStop)
    }

    fun pauseRequest(isPause: Boolean) {
        _pauseRequest.postValue(isPause)
    }

    fun clearPauseRequest(){
        _pauseRequest.value = null
    }

    fun completeRequest(isComplete: Boolean) {
        _completeRequest.postValue(isComplete)
    }

    fun resumeRequest(isResume: Boolean) {
        _resumeRequest.postValue(isResume)
    }

    private val _isPlaying = MutableLiveData<Pair<Boolean, Boolean>>()

    // expose hanya isPlaying sebagai LiveData<Boolean>
    val isPlaying = MediatorLiveData<Boolean>().apply {
        addSource(_isPlaying) { value = it.first }
    }

    fun setIsPlaying(playing: Boolean, isActive: Boolean) {
        _isPlaying.postValue(Pair(playing, isActive))
    }

    val isActive = MediatorLiveData<Boolean>().apply {
        addSource(_isPlaying) { value = it.second }
    }

    fun clearPlayRequest() {
        _playRequest.value = null
    }

    fun clearStopRequest() {
        _stopRequest.value = null
    }


    // Request seek to position
    private val _seekToRequest = MutableLiveData<Int?>()
    val seekToRequest: LiveData<Int?> = _seekToRequest
    fun requestSeekTo(position: Int) { _seekToRequest.value = position }
    fun clearSeekToRequest() { _seekToRequest.value = null }
}

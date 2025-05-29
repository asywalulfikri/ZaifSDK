package sound.recorder.widget.listener


interface MusicListener {
    fun onNote(note : String?)
    fun onVolumeAudio(volume : Float?)

}
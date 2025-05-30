package sound.recorder.widget.listener


object MyMusicListener {
    private var myListener: MusicListener? = null

    fun setMyListener(listener: MusicListener?=null) {
        myListener = listener
    }

    fun postVolumeAudio(volume : Float?){
        myListener?.onVolumeAudio(volume)
    }
}
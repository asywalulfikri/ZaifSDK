package sound.recorder.widget.listener

object MyStopMusicListener {
    private var myListener: StopMusicListener? = null

    fun setMyListener(listener: StopMusicListener? =null) {
        myListener = listener
    }
    fun postAction(stop : Boolean) {
        myListener?.onStop(stop)
    }
}
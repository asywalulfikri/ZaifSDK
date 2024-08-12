package sound.recorder.widget.listener

object MyStopSDKMusicListener {
    private var myListener: StopSDKMusicListener? = null

    fun setMyListener(listener: StopSDKMusicListener? =null) {
        myListener = listener
    }
    fun postAction(stop : Boolean) {
        myListener?.onStop(stop)
    }
    fun onStartAnimation(){
        myListener?.onStartAnimation()
    }
}
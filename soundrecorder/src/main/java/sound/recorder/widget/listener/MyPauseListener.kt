package sound.recorder.widget.listener

object MyPauseListener {
    private var myListener: PauseListener? = null

    fun setMyListener(listener: PauseListener?=null) {
        myListener = listener
    }
    fun postAction(pause : Boolean) {
        myListener?.onPause(pause)
    }

    fun showButtonStop(stop : Boolean){
        myListener?.showButtonStop(stop)
    }
}
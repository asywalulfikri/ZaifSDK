package sound.recorder.widget.listener

object MyCompleteSoundListener {
    private var myListener: CompleteSoundListener? = null

    fun setMyListener(listener: CompleteSoundListener?=null) {
        myListener = listener
    }
    fun postOnCompleteSound() {
        myListener?.onCompleteLoadSound()
    }
}
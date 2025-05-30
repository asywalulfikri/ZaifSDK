package sound.recorder.widget.listener

object MyCompleteMarqueeListener {
    private var myListener: CompleteMarqueeListener? = null

    fun setMyListener(listener: CompleteMarqueeListener?=null) {
        myListener = listener
    }
    fun postOnCompleteMarquee() {
        myListener?.onCompleteTextMarquee()
    }
}
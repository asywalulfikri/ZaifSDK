package sound.recorder.widget.listener


object MyAdsListener {
    private var myListener: AdsListener? = null

    fun setMyListener(listener: AdsListener?=null) {
        myListener = listener
    }
    fun setAds(show : Boolean) {
        myListener?.onViewAds(show)
    }
    fun setUnityAds(show : Boolean) {
        myListener?.onViewUnityAds(show)
    }
    fun setShowButtonHome(show : Boolean) {
        myListener?.onViewButtonHome(show)
    }

}
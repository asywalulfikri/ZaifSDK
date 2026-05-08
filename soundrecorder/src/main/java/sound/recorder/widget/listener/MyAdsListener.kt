package sound.recorder.widget.listener


object MyAdsListener {
    private var myListener: AdsListener? = null

    fun setMyListener(listener: AdsListener?=null) {
        myListener = listener
    }
    fun setBanner(show : Boolean) {
        myListener?.onViewBanner(show)
    }

    fun loadInterstitial() {
        myListener?.loadInterstitial()
    }

    fun loadReward(){
        myListener?.loadReward()
    }

}
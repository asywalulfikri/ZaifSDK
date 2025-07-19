package sound.recorder.widget.listener


object MyAdsListener {
    private var myListener: AdsListener? = null

    fun setMyListener(listener: AdsListener?=null) {
        myListener = listener
    }
    fun setBannerHome(show : Boolean) {
        myListener?.onViewBannerHome(show)
    }
    fun setHideAllBanner(){
        myListener?.onHideAllBanner()
    }
    fun setOnShowInterstitial() {
        myListener?.onShowInterstitial()
    }

}
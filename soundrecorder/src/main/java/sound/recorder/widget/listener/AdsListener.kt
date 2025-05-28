package sound.recorder.widget.listener

interface AdsListener {

    fun onViewAds(show: Boolean)
    fun onViewUnityAds(show: Boolean)
    fun onShowInterstitial()
    fun onViewButtonHome(show : Boolean)
}
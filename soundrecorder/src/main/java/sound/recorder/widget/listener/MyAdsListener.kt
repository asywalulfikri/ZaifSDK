package sound.recorder.widget.listener


object MyAdsListener {
    private var myListener: AdsListener? = null

    fun setMyListener(listener: AdsListener?=null) {
        myListener = listener
    }

    /** Lepas listener hanya bila masih milik pemanggil, agar Activity baru tidak ikut terhapus. */
    fun clearMyListener(listener: AdsListener) {
        if (myListener === listener) myListener = null
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
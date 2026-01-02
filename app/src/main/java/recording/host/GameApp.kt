package recording.host

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.*
import recording.host.cons.Constants
import sound.recorder.widget.MyApp
import sound.recorder.widget.ads.AdConfigProvider
import sound.recorder.widget.base.BaseActivityWidget
import sound.recorder.widget.builder.AdmobSDKBuilder
import sound.recorder.widget.builder.ZaifSDKBuilder
import sound.recorder.widget.util.Constant
import java.util.concurrent.CopyOnWriteArrayList

@SuppressLint("Registered")
open class GameApp : MyApp(), AdConfigProvider {

    /**
     * Main dispatcher TANPA immediate
     * → aman untuk Application.onCreate()
     */
    private val appScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main)

    interface AppInitializationListener {
        fun onInitializationComplete()
    }

    companion object {
        @Volatile
        var isInitialized = false
            private set

        @Volatile
        private var admobSDKBuilder: AdmobSDKBuilder? = null

        private val listeners =
            CopyOnWriteArrayList<AppInitializationListener>()


        fun registerListener(listener: AppInitializationListener) {
            listeners.addIfAbsent(listener)

            if (isInitialized) {
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    listener.onInitializationComplete()
                } else {
                    Handler(Looper.getMainLooper()).post {
                        listener.onInitializationComplete()
                    }
                }
            }
        }


        fun unregisterListener(listener: AppInitializationListener) {
            listeners.remove(listener)
        }
    }

    override fun onCreate() {
        super.onCreate()
        BaseActivityWidget.adConfigProvider = this

        appScope.launch {
            try {
                // 1️⃣ Tunggu essential dari MyApp (maks 2 detik)
                waitEssentialsSafe()

                // 2️⃣ Audio → IO (tidak boleh di Main)
                withContext(Dispatchers.IO) {
                    initAudio()
                }

                // 3️⃣ AdMob → Main (wajib)
                initPrimaryAds()

                // 4️⃣ SDK UI → Main
                initSecondaryComponents()

                // 5️⃣ Notify listener
                isInitialized = true
                notifyListeners()

                Log.d("GameApp", "Initialization completed safely")

            } catch (e: Exception) {
                Log.e("GameApp", "Fatal initialization error", e)
            }
        }
    }

    /**
     * Maksimal tunggu 2 detik
     * Jangan bikin startup menggantung
     */
    private suspend fun waitEssentialsSafe() {
        repeat(4) { // 4 x 500ms = 2 detik
            if (areEssentialsInitialized) return
            delay(500)
        }
        Log.w("GameApp", "Essentials not fully ready, continue anyway")
    }

    /**
     * Audio HARUS background
     */
    private fun initAudio() {
        try {
            AudioEngine.init(this)
        } catch (e: Exception) {
            Log.e("GameApp", "Audio init failed", e)
        }
    }

    /**
     * AdMob HARUS Main Thread
     */
    private fun initPrimaryAds() {
        try {
            admobSDKBuilder = AdmobSDKBuilder.builder(this)
                .setAdmobId(Constants.AdsProductionId.admobId)
                .apply {
                    if (BuildConfig.DEBUG) {
                        setBannerId(Constant.AdsTesterId.admobBannerId)
                        setBannerHomeId(Constant.AdsTesterId.admobBannerId)
                        setInterstitialId(Constant.AdsTesterId.admobInterstitialId)
                        setRewardId(Constant.AdsTesterId.admobRewardId)
                        setRewardInterstitialId(Constant.AdsTesterId.admobRewardInterstitialId)
                        setNativeId(Constant.AdsTesterId.admobNativeId)
                    } else {
                        setBannerId(Constants.AdsProductionId.admobBannerId)
                        setBannerHomeId(Constants.AdsProductionId.admobHomeBannerId)
                        setInterstitialId(Constants.AdsProductionId.admobInterstitialId)
                        setRewardId(Constants.AdsProductionId.admobRewardId)
                        setRewardInterstitialId(Constants.AdsProductionId.admobRewardInterstitialId)
                    }
                }
                .setToast(true)
                .build()

        } catch (e: Exception) {
            Log.e("GameApp", "AdMob init failed", e)
        }
    }

    /**
     * SDK UI → Main Thread
     */
    private fun initSecondaryComponents() {
        try {
            ZaifSDKBuilder.builder(this)
                .setAppName(getString(R.string.app_name))
                .setApplicationId(BuildConfig.APPLICATION_ID)
                .setVersionCode(BuildConfig.VERSION_CODE)
                .setVersionName(BuildConfig.VERSION_NAME)
                .setDeveloperName("Developer+Receh")
                .showNote(true)
                .showTooltip(true)
                .showChangeColor(true)
                .setBackgroundWidgetColor("#2596be")
                .showVolume(true)
                .showListSong(true)
                .build()
        } catch (e: Exception) {
            Log.e("GameApp", "Secondary SDK init failed", e)
        }
    }

    /**
     * Listener selalu di Main Thread
     */
    private fun notifyListeners() {
        for (listener in listeners) {
            try {
                listener.onInitializationComplete()
            } catch (e: Exception) {
                Log.e("GameApp", "Listener error", e)
            }
        }
    }

    override fun onTerminate() {
        appScope.cancel()
        super.onTerminate()
    }

    override fun getAdmobBuilder(): AdmobSDKBuilder? =
        admobSDKBuilder
}

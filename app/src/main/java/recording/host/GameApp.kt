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

    // Scope di IO — operasi yang butuh Main Thread pakai withContext(Main) eksplisit
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    interface AppInitializationListener {
        fun onInitializationComplete()
    }

    companion object {
        private const val TAG = "GameApp"

        // Maksimal tunggu MyApp essentials sebelum lanjut
        private const val ESSENTIALS_WAIT_TIMEOUT_MS  = 2_000L
        private const val ESSENTIALS_POLL_INTERVAL_MS = 100L

        @Volatile
        var isInitialized = false
            private set

        @Volatile
        private var admobSDKBuilder: AdmobSDKBuilder? = null

        private val listeners  = CopyOnWriteArrayList<AppInitializationListener>()
        private val mainHandler = Handler(Looper.getMainLooper())

        fun registerListener(listener: AppInitializationListener) {
            listeners.addIfAbsent(listener)
            if (isInitialized) {
                // Selalu callback di Main Thread dari thread manapun
                mainHandler.post {
                    try {
                        listener.onInitializationComplete()
                    } catch (e: Exception) {
                        Log.e(TAG, "Listener immediate callback error: ${e.message}")
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
                // 1. Tunggu MyApp essentials selesai (maks 2 detik, di IO)
                waitEssentialsSafe()

                // 2. Audio — wajib IO
                initAudio()

                // 3. AdMob builder — wajib Main Thread
                withContext(Dispatchers.Main) {
                    initPrimaryAds()
                }

                // 4. ZaifSDK — wajib Main Thread
                withContext(Dispatchers.Main) {
                    initSecondaryComponents()
                }

                // 5. Tandai selesai dan notify listener
                isInitialized = true
                notifyListeners()

                Log.d(TAG, "GameApp initialization completed")

            } catch (e: Exception) {
                Log.e(TAG, "Fatal initialization error", e)
                // Tetap notify agar Activity tidak menggantung selamanya
                isInitialized = true
                notifyListeners()
            }
        }
    }

    /**
     * Poll sampai MyApp.areEssentialsInitialized == true.
     * Berjalan di IO — tidak block Main Thread sama sekali.
     */
    private suspend fun waitEssentialsSafe() {
        val startTime = System.currentTimeMillis()
        while (!areEssentialsInitialized) {
            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed >= ESSENTIALS_WAIT_TIMEOUT_MS) {
                Log.w(TAG, "MyApp essentials timeout after ${elapsed}ms, continuing anyway")
                return
            }
            delay(ESSENTIALS_POLL_INTERVAL_MS)
        }
        Log.d(TAG, "MyApp essentials ready")
    }

    /**
     * Audio init — HARUS background thread.
     * Dipanggil dari appScope (IO) — sudah aman.
     */
    private fun initAudio() {
        try {
            SoundPlayUtils.init(applicationContext)
            Log.d(TAG, "Audio initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Audio init failed: ${e.message}")
        }
    }

    /**
     * AdMob builder — HARUS Main Thread.
     * Dipanggil via withContext(Dispatchers.Main).
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

            Log.d(TAG, "AdMob builder initialized")
        } catch (e: Exception) {
            Log.e(TAG, "AdMob init failed: ${e.message}")
        }
    }

    /**
     * ZaifSDK — HARUS Main Thread.
     * Dipanggil via withContext(Dispatchers.Main).
     */
    private fun initSecondaryComponents() {
        try {
            ZaifSDKBuilder.builder(this)
                .setAppName(getString(R.string.app_name))
                .setApplicationId(BuildConfig.APPLICATION_ID)
                .setVersionCode(BuildConfig.VERSION_CODE)
                .setVersionName(BuildConfig.VERSION_NAME)
                .setDeveloperName(BuildConfig.developerName)
                .showNote(BuildConfig.showNote)
                .showTooltip(BuildConfig.showTooltip)
                .showChangeColor(BuildConfig.showChangeColor)
                .setBackgroundWidgetColor(Constants.Widget.WIDGET_COLOR)
                .showVolume(BuildConfig.showVolume)
                .showListSong(BuildConfig.showListSong)
                .build()

            Log.d(TAG, "Secondary components initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Secondary SDK init failed: ${e.message}")
        }
    }

    /**
     * Notify semua listener di Main Thread.
     * Aman dipanggil dari thread manapun.
     */
    private fun notifyListeners() {
        mainHandler.post {
            listeners.forEach { listener ->
                try {
                    listener.onInitializationComplete()
                } catch (e: Exception) {
                    Log.e(TAG, "Listener notify error: ${e.message}")
                }
            }
        }
    }

    override fun onTerminate() {
        appScope.cancel()
        super.onTerminate()
    }

    override fun getAdmobBuilder(): AdmobSDKBuilder? = admobSDKBuilder
}
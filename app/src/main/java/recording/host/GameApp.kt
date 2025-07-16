package recording.host

import android.annotation.SuppressLint
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import recording.host.cons.Constants
import sound.recorder.widget.MyApp
import sound.recorder.widget.ads.AdConfigProvider
import sound.recorder.widget.base.BaseActivityWidget
import sound.recorder.widget.builder.AdmobSDKBuilder
import sound.recorder.widget.builder.FanSDKBuilder
import sound.recorder.widget.builder.UnitySDKBuilder
import sound.recorder.widget.builder.ZaifSDKBuilder
import sound.recorder.widget.util.Constant
import kotlin.collections.forEach

@SuppressLint("Registered")
open class GameApp : MyApp(), AdConfigProvider {

    // Coroutine scope untuk menjalankan tugas di background tanpa membatalkan saat terjadi error
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    interface AppInitializationListener {
        fun onInitializationComplete()
    }

    companion object {
        var isInitialized = false
            private set

        // Pola Singleton: Menyimpan satu instance untuk seluruh aplikasi.
        var admobSDKBuilder: AdmobSDKBuilder? = null
        var fanSDKBuilder: FanSDKBuilder? = null
        var unitySDKBuilder: UnitySDKBuilder? = null

        private val listeners = mutableListOf<AppInitializationListener>()

        fun registerListener(listener: AppInitializationListener) {
            // Cek dulu agar tidak duplikat
            if (!listeners.contains(listener)) {
                listeners.add(listener)
            }
        }

        fun unregisterListener(listener: AppInitializationListener) {
            listeners.remove(listener)
        }
    }

    override fun onCreate() {
        super.onCreate()

        // Luncurkan satu coroutine untuk mengatur semua proses inisialisasi secara bertahap
        BaseActivityWidget.adConfigProvider = this

        appScope.launch {
            // Jalankan semua proses inisialisasi
            initializePrimaryAds()
            initializeSecondaryComponents()
            initializeCoreComponents()

            // --- PERBAIKAN 3: Beri tahu semua listener bahwa proses telah selesai ---
            // Pindah ke Main thread untuk keamanan saat memanggil listener
            withContext(Dispatchers.Main) {
                isInitialized = true
                listeners.forEach { it.onInitializationComplete() }
            }
        }
    }

    /**
     * Inisialisasi komponen paling penting yang harus ada, namun tetap dijalankan di background.
     */
    private suspend fun initializeCoreComponents() {
        // Memindahkan inisialisasi AudioEngine dari main thread untuk mencegah UI freeze
        try {
            AudioEngine.init(this)
        } catch (e: Exception) {
            Log.e("GameApp", "Error initializing AudioEngine", e)
        }
    }

    /**
     * Inisialisasi SDK iklan utama yang paling sering digunakan.
     */
    private suspend fun initializePrimaryAds() {
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
            .build() // .build() mengembalikan objek AdmobSDKBuilder yang sudah jadi
    }

    /**
     * Inisialisasi komponen lain yang tidak kritis saat startup.
     * Diberi jeda (delay) agar aplikasi bisa menampilkan UI pertamanya tanpa gangguan.
     */
    private suspend fun initializeSecondaryComponents() {
        // JEDA: Beri waktu 4 detik agar aplikasi menjadi responsif sebelum melanjutkan beban kerja.
        // Nilai ini bisa disesuaikan sesuai kebutuhan.
        delay(1000L)

        // Jalankan sisa inisialisasi secara bersamaan setelah jeda selesai
        coroutineScope {
            // Inisialisasi FAN
            launch {
                fanSDKBuilder = FanSDKBuilder.builder(this@GameApp)
                    .setApplicationId(Constants.AdsProductionId.fanId)
                    .setBannerId(Constants.AdsProductionId.fanBannerId)
                    .setInterstitialId(Constants.AdsProductionId.fanInterstitialId)
                    .setEnable(false)
                    .build()
            }

            // Inisialisasi Unity
            launch {
               unitySDKBuilder =  UnitySDKBuilder.builder(this@GameApp)
                    .setUnityId(Constants.AdsProductionId.unityGameId)
                    .setEnable(false)
                    .build()

                var testMode = true
                testMode = BuildConfig.DEBUG

                launch(Dispatchers.Main) {
                    initializeUnity(Constants.AdsProductionId.unityGameId, testMode)
                }
            }

            // Inisialisasi Zaif Widget
            launch {
                ZaifSDKBuilder.builder(this@GameApp)
                    .setAppName(getString(R.string.app_name))
                    .setApplicationId(BuildConfig.APPLICATION_ID)
                    .setVersionCode(BuildConfig.VERSION_CODE)
                    .setVersionName(BuildConfig.VERSION_NAME)
                    .showNote(true)
                    .showTooltip(true)
                    .showChangeColor(true)
                    .setBackgroundWidgetColor("#2596be")
                    .showVolume(true)
                    .showListSong(true)
                    .build()
            }
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        // Batalkan semua coroutine yang berjalan jika aplikasi dihentikan
        appScope.cancel()
    }

    override fun getAdmobBuilder(): AdmobSDKBuilder? = admobSDKBuilder
    override fun getFanBuilder(): FanSDKBuilder? = fanSDKBuilder
    override fun getUnityBuilder(): UnitySDKBuilder? = unitySDKBuilder
}
package sound.recorder.widget

import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.PackageManager
import android.util.Log
import com.facebook.ads.AudienceNetworkAds
import com.google.android.gms.ads.MobileAds
import com.google.firebase.FirebaseApp
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.UnityAds
import kotlinx.coroutines.*

@SuppressLint("Registered")
open class MyApp : Application() {

    // Coroutine scope untuk semua pekerjaan background di level aplikasi
    private val applicationScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        // Menggunakan lateinit untuk instance agar lebih aman
        private lateinit var instance: MyApp
        fun getApplicationContext(): MyApp = instance
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Luncurkan coroutine untuk menjalankan SEMUA inisialisasi secara PARALEL
        applicationScope.launch {
            initializeEssentialSDKs()
        }

        // Penangan crash khusus untuk bug WebView Unity Ads (ini sudah bagus)
        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            if (e.message?.contains("reasonPhrase can't be empty") == true) {
                Log.e("UnityCrashBypass", "Unity Ads SDK WebView bug suppressed")
            } else {
                // Untuk crash lain, teruskan ke handler default agar tetap dilaporkan
                Thread.getDefaultUncaughtExceptionHandler()?.uncaughtException(Thread.currentThread(), e)
            }
        }
    }

    /**
     * Menjalankan semua proses inisialisasi SDK secara paralel untuk mempercepat startup.
     * Menggunakan async untuk memulai setiap tugas dan awaitAll untuk menunggu semuanya selesai.
     */
    private suspend fun initializeEssentialSDKs() = coroutineScope {
        val initializers = mutableListOf<Deferred<Unit>>()

        // 1. Inisialisasi Firebase
        initializers.add(async { initializeFirebase() })

        // 2. Inisialisasi SDK Iklan jika WebView tersedia
        if (isWebViewAvailable()) {
            // Inisialisasi AdMob
            initializers.add(async { initializeAdMob() })
            // Inisialisasi Facebook Audience Network (FAN)
            initializers.add(async { initializeAudienceNetworkAds() })
        }

        // Menunggu semua proses inisialisasi yang dimulai di atas selesai
        initializers.awaitAll()
        Log.d("MyApp", "All essential SDKs have been initialized.")
    }

    /**
     * Inisialisasi Firebase
     */
    private suspend fun initializeFirebase() {
        try {
            FirebaseApp.initializeApp(this@MyApp)
            Log.d("MyApp", "Firebase initialized successfully.")
        } catch (e: Exception) {
            Log.e("MyApp", "Error initializing Firebase: ${e.message}")
        }
    }

    /**
     * Inisialisasi AdMob
     */
    private suspend fun initializeAdMob() {
        try {
            // Cek ketersediaan class sebelum memanggilnya
            Class.forName("com.google.android.gms.ads.MobileAds")
            MobileAds.initialize(this@MyApp) {}
            Log.d("MyApp", "AdMob initialized successfully.")
        } catch (e: Exception) {
            Log.e("MyApp", "Error initializing AdMob: ${e.message}")
        }
    }

    /**
     * Inisialisasi Facebook Audience Network
     */
    private suspend fun initializeAudienceNetworkAds() {
        try {
            AudienceNetworkAds.initialize(this@MyApp)
            Log.d("MyApp", "Audience Network Ads initialized successfully.")
        } catch (e: Exception) {
            Log.e("MyApp", "Error initializing Audience Network Ads: ${e.message}")
        }
    }

    /**
     * Inisialisasi Unity Ads, dipanggil secara eksplisit dari GameApp.
     * Metode ini tidak perlu dijalankan otomatis di MyApp.
     */
    suspend fun initializeUnity(unityId: String) {
        withContext(Dispatchers.IO) {
            try {
                val testMode = BuildConfig.DEBUG
                UnityAds.initialize(this@MyApp, unityId, testMode, object : IUnityAdsInitializationListener {
                    override fun onInitializationComplete() {
                        Log.d("UnityAds", "Initialization Complete.")
                    }

                    override fun onInitializationFailed(error: UnityAds.UnityAdsInitializationError?, message: String?) {
                        Log.e("UnityAds", "Initialization Failed: $message")
                    }
                })
            } catch (e: Exception) {
                Log.e("MyApp", "Error initializing UnityAds: ${e.message}")
            }
        }
    }

    /**
     * Mengecek ketersediaan WebView di perangkat.
     */
    private fun isWebViewAvailable(): Boolean {
        return try {
            packageManager.getPackageInfo("com.google.android.webview", 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e("MyApp", "WebView package not available.")
            false
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        applicationScope.cancel()
    }
}
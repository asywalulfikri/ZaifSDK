package sound.recorder.widget // Ganti dengan package SDK Anda

import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.ads.MobileAds
import com.google.firebase.FirebaseApp
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.UnityAds
import kotlinx.coroutines.*

@SuppressLint("Registered")
open class MyApp : Application() {

    enum class Sdk {
        ALL_ESSENTIALS, // Untuk AdMob & FAN
        UNITY
    }

    interface SdkInitializationListener {
        fun onSdkInitialized(sdk: Sdk)
    }

    private val applicationScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private lateinit var instance: MyApp
        fun getApplicationContext(): MyApp = instance

        var areEssentialsInitialized = false
            private set
        var isUnityInitialized = false
            private set

        private val sdkListeners = mutableListOf<SdkInitializationListener>()

        fun registerListener(listener: SdkInitializationListener) {
            if (!sdkListeners.contains(listener)) {
                sdkListeners.add(listener)
            }
        }

        fun unregisterListener(listener: SdkInitializationListener) {
            sdkListeners.remove(listener)
        }

        fun notifyListeners(sdk: Sdk) {
            Handler(Looper.getMainLooper()).post {
                sdkListeners.forEach { it.onSdkInitialized(sdk) }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        applicationScope.launch {
            initializeEssentialSDKs()
        }

        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            if (e.message?.contains("reasonPhrase can't be empty") == true) {
                Log.e("UnityCrashBypass", "Unity Ads SDK WebView bug suppressed")
            } else {
                Thread.getDefaultUncaughtExceptionHandler()?.uncaughtException(Thread.currentThread(), e)
            }
        }
    }

    private suspend fun initializeEssentialSDKs() = coroutineScope {
        val initializers = mutableListOf<Deferred<Unit>>()

        initializers.add(async { initializeFirebase() })

        if (isWebViewAvailable()) {
            initializers.add(async { initializeAdMob() })
        }

        initializers.awaitAll()
        Log.d("MyApp", "All essential SDKs have been initialized.")

        areEssentialsInitialized = true
        notifyListeners(Sdk.ALL_ESSENTIALS)
    }

    private fun initializeFirebase() {
        try {
            FirebaseApp.initializeApp(this@MyApp)
            Log.d("MyApp", "Firebase initialized successfully.")
        } catch (e: Exception) {
            Log.e("MyApp", "Error initializing Firebase: ${e.message}")
        }
    }

    private fun initializeAdMob() {
        try {
            Class.forName("com.google.android.gms.ads.MobileAds")
            MobileAds.initialize(this@MyApp) {}
            Log.d("ADS_Admob", "AdMob initialized successfully.")
        } catch (e: Exception) {
            Log.e("ADS_Admob", "Error initializing AdMob: ${e.message}")
        }
    }

    suspend fun initializeUnity(unityId: String, testMode: Boolean) {
        if(unityId==""){
            isUnityInitialized = true
            notifyListeners(Sdk.UNITY)
        }else{
            withContext(Dispatchers.IO) {
                try {
                    UnityAds.initialize(this@MyApp, unityId, testMode, object : IUnityAdsInitializationListener {
                        override fun onInitializationComplete() {
                            Log.d("ADS_Unity", "Initialization Complete.")
                            isUnityInitialized = true
                            notifyListeners(Sdk.UNITY)
                        }

                        override fun onInitializationFailed(error: UnityAds.UnityAdsInitializationError?, message: String?) {
                            Log.e("ADS_Unity", "Initialization Failed: $message")
                        }
                    })
                } catch (e: Exception) {
                    Log.e("ADS_Unity", "Error initializing UnityAds: ${e.message}")
                }
            }
        }
    }

    private fun isWebViewAvailable(): Boolean {
        return try {
            packageManager.getPackageInfo("com.google.android.webview", 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e("MyApp", "WebView package not available."+e.message)
            false
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        applicationScope.cancel()
    }
}
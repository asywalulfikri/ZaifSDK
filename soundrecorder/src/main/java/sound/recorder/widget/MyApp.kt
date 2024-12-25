package sound.recorder.widget

import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.PackageManager
import android.util.Log
import com.facebook.ads.AudienceNetworkAds
import com.google.android.gms.ads.MobileAds
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.*

@SuppressLint("Registered")
open class MyApp : Application() {

    // Coroutine scope with SupervisorJob to isolate failures
    private val applicationScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()

        // Initialize SDKs in the background
        applicationScope.launch {
            withTimeoutOrNull(10_000) { // Timeout to prevent hanging
                initializeFirebase()
            }

            if (isWebViewAvailable()) {
                withTimeoutOrNull(10_000) { // Timeout for AdMob initialization
                    if (isAdMobAvailable()) {
                        initializeAdMob()
                    }
                }

                withTimeoutOrNull(10_000) { // Timeout for Audience Network initialization
                    initializeAudienceNetworkAds()
                }
            }
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        // Cancel all running coroutines to avoid memory leaks
        applicationScope.cancel()
    }

    /**
     * Check if WebView package is available on the device
     */
    private suspend fun isWebViewAvailable(): Boolean {
        return withContext(Dispatchers.IO) {
            val packageManager = packageManager
            try {
                packageManager.getPackageInfo("com.google.android.webview", 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                Log.e("MyApp", "WebView package not available: ${e.message}")
                false
            }
        }
    }

    /**
     * Check if AdMob SDK is available
     */
    private suspend fun isAdMobAvailable(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Class.forName("com.google.android.gms.ads.MobileAds")
                true
            } catch (e: ClassNotFoundException) {
                Log.e("MyApp", "AdMob not available: ${e.message}")
                false
            }
        }
    }

    /**
     * Initialize Firebase in a background thread
     */
    private suspend fun initializeFirebase() {
        withContext(Dispatchers.IO) {
            try {
                FirebaseApp.initializeApp(this@MyApp)
                Log.d("MyApp", "Firebase initialized successfully")
            } catch (e: Exception) {
                Log.e("MyApp", "Error initializing Firebase: ${e.message}")
            }
        }
    }

    /**
     * Initialize AdMob SDK in a background thread
     */
    private suspend fun initializeAdMob() {
        withContext(Dispatchers.IO) {
            try {
                MobileAds.initialize(this@MyApp) {
                    Log.d("MyApp", "AdMob initialized successfully")
                }
            } catch (e: Exception) {
                Log.e("MyApp", "Error initializing AdMob: ${e.message}")
            }
        }
    }

    /**
     * Initialize Facebook Audience Network Ads in a background thread
     */
    private suspend fun initializeAudienceNetworkAds() {
        withContext(Dispatchers.IO) {
            try {
                AudienceNetworkAds.initialize(this@MyApp)
                Log.d("MyApp", "Audience Network Ads initialized successfully")
            } catch (e: Exception) {
                Log.e("MyApp", "Error initializing Audience Network Ads: ${e.message}")
            }
        }
    }
}

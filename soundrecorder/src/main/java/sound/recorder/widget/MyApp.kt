package sound.recorder.widget

import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.PackageManager
import android.util.Log
import android.webkit.WebView
import com.facebook.ads.AudienceNetworkAds
import com.google.android.gms.ads.MobileAds
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SuppressLint("Registered")
open class MyApp : Application() {

    private val applicationScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // Initialize SDKs

        initializeFirebase()

        if(isWebViewSupported()&&isWebViewAvailable()){
            if(isAdMobAvailable()){
                initializeAdMob()
            }
            initializeAudienceNetworkAds()
        }
    }

    private fun isWebViewSupported(): Boolean {
        return try {
            WebView(this)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun isWebViewAvailable(): Boolean {
        val packageManager = packageManager
        return try {
            packageManager.getPackageInfo("com.google.android.webview", 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }


    private fun isAdMobAvailable(): Boolean {
        return try {
            Class.forName("com.google.android.gms.ads.MobileAds")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }

    /**
     * Initialize Firebase
     */
    private fun initializeFirebase() {
        applicationScope.launch {
            try {
                withContext(Dispatchers.Main) {
                    FirebaseApp.initializeApp(this@MyApp)
                    Log.d("MyApp", "Firebase initialized successfully")
                }
            } catch (e: Exception) {
                Log.e("MyApp", "Error initializing Firebase: ${e.message}")
            }
        }
    }

    /**
     * Initialize AdMob SDK
     */
    private fun initializeAdMob() {
        try {
            val backgroundScope = CoroutineScope(Dispatchers.IO)
            backgroundScope.launch {
                // Initialize the Google Mobile Ads SDK on a background thread.
                MobileAds.initialize(this@MyApp) {}
            }
        }catch (e : Exception){
            Log.e("MyApp", "Error initializing Admob Ads: ${e.message}")
        }
    }

    /**
     * Initialize Facebook Audience Network Ads
     */
    private fun initializeAudienceNetworkAds() {
        applicationScope.launch {
            try {
                withContext(Dispatchers.Main) {
                    AudienceNetworkAds.initialize(this@MyApp)
                    Log.d("MyApp", "Audience Network Ads initialized successfully")
                }
            } catch (e: Exception) {
                Log.e("MyApp", "Error initializing Audience Network Ads: ${e.message}")
            }
        }
    }
}

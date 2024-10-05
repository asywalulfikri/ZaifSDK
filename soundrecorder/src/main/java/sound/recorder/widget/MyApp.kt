package sound.recorder.widget

import android.annotation.SuppressLint
import android.app.Application
import android.util.Log
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
        initializeAdMob()
        initializeAudienceNetworkAds()
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
        applicationScope.launch {
            try {
                withContext(Dispatchers.Main) {
                    MobileAds.initialize(this@MyApp) {
                        Log.d("MyApp", "AdMob initialized successfully")
                    }
                }
            } catch (e: Exception) {
                Log.e("MyApp", "Error initializing AdMob: ${e.message}")
            }
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

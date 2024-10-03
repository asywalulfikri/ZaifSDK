package sound.recorder.widget

import android.annotation.SuppressLint
import android.app.Application
import android.util.Log
import com.facebook.ads.AudienceNetworkAds
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.FirebaseApp
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SuppressLint("Registered")
open class MyApp : Application() {

    private lateinit var executor: ExecutorService

    override fun onCreate() {
        super.onCreate()

        // Initialize ExecutorService
        executor = Executors.newSingleThreadExecutor()

        // Initialize Firebase
        initializeFirebase()

        // Initialize AdMob
        initializeAdMob()

        // Initialize Facebook Audience Network
        initializeAudienceNetworkAds()

        // Shutdown executor once tasks are completed
        shutdownExecutor()

    }

    /**
     * Initialize Firebase if Play Services are available
     */
    private fun initializeFirebase() {
        executor.execute {
            try {
                if (checkPlayServices()) {
                    FirebaseApp.initializeApp(this@MyApp)
                    Log.d("MyApp", "Firebase initialized successfully")
                } else {
                    Log.w("MyApp", "Firebase initialization skipped due to Play Services unavailability")
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
        executor.execute {
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
     * Initialize Facebook Audience Network Ads
     */
    private fun initializeAudienceNetworkAds() {
        executor.execute {
            try {
                AudienceNetworkAds.initialize(this@MyApp)
                Log.d("MyApp", "Audience Network Ads initialized successfully")
            } catch (e: Exception) {
                Log.e("MyApp", "Error initializing Audience Network Ads: ${e.message}")
            }
        }
    }

    /**
     * Check availability of Google Play Services
     */
    private fun checkPlayServices(): Boolean {
        val apiAvailability = GoogleApiAvailability.getInstance()
        val resultCode = apiAvailability.isGooglePlayServicesAvailable(this)
        return if (resultCode != ConnectionResult.SUCCESS) {
            if (apiAvailability.isUserResolvableError(resultCode)) {
                Log.i("MyApp", "Resolvable error occurred: $resultCode")
            } else {
                Log.i("MyApp", "This device is not supported.")
            }
            false
        } else {
            true
        }
    }

    /**
     * Shutdown the executor to release resources
     */
    private fun shutdownExecutor() {
        executor.shutdown()
        try {
            if (!executor.awaitTermination(1, TimeUnit.MINUTES)) {
                executor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            executor.shutdownNow()
        }
    }
}

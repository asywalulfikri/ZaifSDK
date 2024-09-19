package sound.recorder.widget

import android.annotation.SuppressLint
import android.app.Application
import android.util.Log
import com.facebook.ads.AudienceNetworkAds
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.FirebaseApp

@SuppressLint("Registered")
open class MyApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Inisialisasi Firebase
        initializeFirebase()

        // Inisialisasi AdMob
        initializeAdMob()

        // Inisialisasi Facebook Audience Network
        initializeAudienceNetworkAds()

        // Cek Google Play Services
        checkPlayServices()
    }

    /**
     * Inisialisasi Firebase jika layanan tersedia
     */
    private fun initializeFirebase() {
        try {
            if (checkPlayServices()) {
                FirebaseApp.initializeApp(this)
                Log.d("Initialization", "Firebase initialized successfully")
            } else {
                Log.w("Initialization", "Firebase initialization skipped due to Play Services unavailability")
            }
        } catch (e: Exception) {
            Log.e("Firebase Error", "Error initializing Firebase: ${e.message}")
        }
    }

    /**
     * Inisialisasi AdMob SDK
     */
    private fun initializeAdMob() {
        try {
            MobileAds.initialize(this) {
                Log.d("Initialization", "AdMob initialized successfully")
            }
        } catch (e: Exception) {
            Log.e("AdMob Error", "Error initializing AdMob: ${e.message}")
        }
    }

    /**
     * Inisialisasi Facebook Audience Network Ads
     */
    private fun initializeAudienceNetworkAds() {
        try {
            AudienceNetworkAds.initialize(this)
            Log.d("Initialization", "Audience Network Ads initialized successfully")
        } catch (e: Exception) {
            Log.e("FAN Error", "Error initializing Audience Network Ads: ${e.message}")
        }
    }

    /**
     * Mengecek ketersediaan Google Play Services
     */
    private fun checkPlayServices(): Boolean {
        val apiAvailability = GoogleApiAvailability.getInstance()
        val resultCode = apiAvailability.isGooglePlayServicesAvailable(this)
        return if (resultCode != ConnectionResult.SUCCESS) {
            if (apiAvailability.isUserResolvableError(resultCode)) {
                Log.i("MyApp", "Terjadi kesalahan yang dapat diperbaiki: $resultCode")
            } else {
                Log.i("MyApp", "Perangkat ini tidak didukung.")
            }
            false
        } else {
            true
        }
    }
}

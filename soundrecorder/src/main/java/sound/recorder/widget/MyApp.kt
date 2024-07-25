package sound.recorder.widget

import android.annotation.SuppressLint
import android.app.Application
import android.util.Log
import com.facebook.ads.AudienceNetworkAds
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SuppressLint("Registered")
open class MyApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()

        applicationScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    if (check()) {
                        //FirebaseApp.initializeApp(this@MyApp)
                        Log.d("Initialization", "Firebase initialized successfully")
                    }
                } catch (e: Exception) {
                    Log.e("Firebase Error", "Error initializing Firebase: ${e.message}")
                }

                try {
                    AudienceNetworkAds.initialize(this@MyApp)
                    Log.d("Initialization", "Audience Network Ads initialized successfully")
                } catch (e: Exception) {
                    Log.e("FAN Error", "Error initializing Audience Network Ads: ${e.message}")
                }
            }
        }
    }

    private fun check(): Boolean {
        return checkPlayServices().also { isAvailable ->
            if (!isAvailable) {
                Log.w("MyApp", "Google Play Services not available.")
            }else{
                Log.d("MyApp", "Google Play Services available.")
            }
        }
    }

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

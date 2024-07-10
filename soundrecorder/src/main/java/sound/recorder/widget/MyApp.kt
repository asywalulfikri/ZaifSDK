package sound.recorder.widget

import android.annotation.SuppressLint
import android.app.Application
import android.util.Log
import com.facebook.ads.AudienceNetworkAds
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
                    FirebaseApp.initializeApp(this@MyApp)
                    Log.d("Initialization", "Firebase initialized successfully")
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
}

package sound.recorder.widget // Pastikan ini sesuai dengan struktur folder Anda

import android.annotation.SuppressLint
import android.app.Application
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebView
import com.google.android.gms.ads.MobileAds
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.*
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.resume


open class MyApp : Application() {

    enum class Sdk {
        ALL_ESSENTIALS
    }

    interface SdkInitializationListener {
        fun onSdkInitialized(sdk: Sdk)
    }

    private val applicationScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {

        @Volatile
        private var instance: MyApp? = null

        fun getApplicationContext(): MyApp =
            instance ?: throw IllegalStateException("MyApp not initialized")

        @Volatile
        var areEssentialsInitialized = false
            private set

        private val sdkListeners = CopyOnWriteArrayList<SdkInitializationListener>()

        fun registerListener(listener: SdkInitializationListener) {
            if (!sdkListeners.contains(listener)) {
                sdkListeners.add(listener)
            }

            if (areEssentialsInitialized) {
                Handler(Looper.getMainLooper()).post {
                    listener.onSdkInitialized(Sdk.ALL_ESSENTIALS)
                }
            }
        }

        fun unregisterListener(listener: SdkInitializationListener) {
            sdkListeners.remove(listener)
        }

        private fun notifyListeners(sdk: Sdk) {
            Handler(Looper.getMainLooper()).post {
                sdkListeners.forEach {
                    try {
                        it.onSdkInitialized(sdk)
                    } catch (e: Exception) {
                        Log.e("MyApp", "Listener error: ${e.message}")
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        applicationScope.launch {
            initializeEssentialSDKs()
        }
    }

    private suspend fun initializeEssentialSDKs() = coroutineScope {
        val jobs = mutableListOf<Deferred<Unit>>()

        jobs.add(async { initializeFirebase() })


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val processName = getProcessName()
            if (packageName != processName) {
                try {
                    WebView.setDataDirectorySuffix(processName)
                } catch (e: Exception) {
                    Log.e("MyApp", "WebView suffix error: ${e.message}")
                }
            }
        }

        if (isWebViewAvailableSafely()) {
            jobs.add(async { initializeAdMob() })
        }

        jobs.awaitAll()

        areEssentialsInitialized = true
        notifyListeners(Sdk.ALL_ESSENTIALS)
    }

    private fun initializeFirebase() {
        try {
            FirebaseApp.initializeApp(this)
        } catch (e: Exception) {
            Log.e("MyApp", "Firebase error: ${e.message}")
        }
    }

//cocok untuk processor MALI
    private suspend fun initializeAdMob() = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine<Unit> { cont ->
            try {
                // Delay penting untuk GPU Mali & WebView stability
                Handler(Looper.getMainLooper()).postDelayed({

                    if (!cont.isActive) return@postDelayed

                    try {
                        MobileAds.initialize(this@MyApp) { status ->
                            Log.d("MyApp", "AdMob initialized: $status")
                            if (cont.isActive) cont.resume(Unit)
                        }
                    } catch (e: Exception) {
                        Log.e("MyApp", "AdMob init crash-safe: ${e.message}")
                        if (cont.isActive) cont.resume(Unit)
                    }

                }, 1500L) // 1.5 detik → sweet spot Mali

            } catch (e: Exception) {
                Log.e("MyApp", "AdMob outer error: ${e.message}")
                if (cont.isActive) cont.resume(Unit)
            }
        }
    }


    private fun isWebViewAvailableSafely(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WebView.getCurrentWebViewPackage() != null ||
                        packageManager.hasSystemFeature(
                            android.content.pm.PackageManager.FEATURE_WEBVIEW
                        )
            } else {
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        applicationScope.cancel()
        instance = null
    }
}

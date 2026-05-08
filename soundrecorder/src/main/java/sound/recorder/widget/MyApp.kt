package sound.recorder.widget

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
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

open class MyApp : Application() {

    enum class Sdk {
        ALL_ESSENTIALS
    }

    interface SdkInitializationListener {
        fun onSdkInitialized(sdk: Sdk)
    }

    // Scope di IO agar tidak ada operasi blocking di Main Thread
    private val applicationScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val TAG = "MyApp"
        private const val FIREBASE_TIMEOUT_MS = 15_000L
        private const val ADMOB_TIMEOUT_MS    = 10_000L

        @Volatile
        private var instance: MyApp? = null

        fun getApplicationContext(): MyApp =
            instance ?: throw IllegalStateException("MyApp not initialized")

        // AtomicBoolean agar set + read bersifat atomic
        private val _areEssentialsInitialized = AtomicBoolean(false)

        val areEssentialsInitialized: Boolean
            get() = _areEssentialsInitialized.get()

        private val sdkListeners = CopyOnWriteArrayList<SdkInitializationListener>()
        private val mainHandler  = Handler(Looper.getMainLooper())

        fun registerListener(listener: SdkInitializationListener) {
            if (!sdkListeners.contains(listener)) {
                sdkListeners.add(listener)
            }
            // Jika sudah init, langsung callback di Main Thread
            if (_areEssentialsInitialized.get()) {
                mainHandler.post {
                    try {
                        listener.onSdkInitialized(Sdk.ALL_ESSENTIALS)
                    } catch (e: Exception) {
                        Log.e(TAG, "Listener immediate callback error: ${e.message}")
                    }
                }
            }
        }

        fun unregisterListener(listener: SdkInitializationListener) {
            sdkListeners.remove(listener)
        }

        fun clearAllListeners() {
            sdkListeners.clear()
        }

        private fun notifyListeners(sdk: Sdk) {
            mainHandler.post {
                sdkListeners.forEach { listener ->
                    try {
                        listener.onSdkInitialized(sdk)
                    } catch (e: Exception) {
                        Log.e(TAG, "Listener error: ${e.message}")
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

    private suspend fun initializeEssentialSDKs() {
        coroutineScope {
            // 1. Firebase — parallel di IO
            val firebaseJob = launch(Dispatchers.IO) {
                withTimeoutOrNull(FIREBASE_TIMEOUT_MS) {
                    initializeFirebase()
                } ?: Log.w(TAG, "Firebase initialization timed out")
            }

            // 2. WebView DataDirectory Suffix — wajib Main Thread
            withContext(Dispatchers.Main) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val processName = getProcessName()
                    if (packageName != processName) {
                        try {
                            WebView.setDataDirectorySuffix(processName)
                            Log.d(TAG, "WebView suffix set: $processName")
                        } catch (e: Exception) {
                            Log.e(TAG, "WebView suffix error: ${e.message}")
                        }
                    }
                }
            }

            // 3. AdMob — withContext(Main) ada di dalam initializeAdMob()
            if (isWebViewAvailableSafely()) {
                withTimeoutOrNull(ADMOB_TIMEOUT_MS) {
                    initializeAdMob()
                } ?: Log.w(TAG, "AdMob initialization timed out")
            } else {
                Log.w(TAG, "WebView not available, skipping AdMob init")
            }

            // 4. Tunggu Firebase selesai — aman karena join() di IO bukan Main
            firebaseJob.join()
        }

        // Tandai selesai secara atomic
        _areEssentialsInitialized.set(true)
        Log.d(TAG, "All essential SDKs initialized")
        notifyListeners(Sdk.ALL_ESSENTIALS)
    }

    private suspend fun initializeFirebase() = withContext(Dispatchers.IO) {
        try {
            FirebaseApp.initializeApp(this@MyApp)
            Log.d(TAG, "Firebase initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Firebase error: ${e.message}")
        }
    }
    private suspend fun initializeAdMob() = withContext(Dispatchers.IO) { // ← IO, bukan Main
        suspendCancellableCoroutine<Unit> { cont ->
            try {
                MobileAds.initialize(this@MyApp) { status ->
                    Log.d(TAG, "AdMob initialized: $status")
                    if (cont.isActive) cont.resume(Unit)
                }
            } catch (e: Exception) {
                Log.e(TAG, "AdMob init error: ${e.message}")
                if (cont.isActive) cont.resume(Unit)
            }
        }
    }

    private fun isWebViewAvailableSafely(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WebView.getCurrentWebViewPackage() != null ||
                        packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_WEBVIEW)
            } else {
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "WebView check error: ${e.message}")
            false
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        applicationScope.cancel()
        Log.d(TAG, "Application terminated, scope cancelled")
    }
}
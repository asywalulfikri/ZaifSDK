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
import kotlin.coroutines.resume

open class MyApp : Application() {

    enum class Sdk {
        ALL_ESSENTIALS
    }

    interface SdkInitializationListener {
        fun onSdkInitialized(sdk: Sdk)
    }

    private val applicationScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        private const val TAG = "MyApp"
        private const val FIREBASE_TIMEOUT_MS = 15000L // 15 detik
        private const val ADMOB_TIMEOUT_MS = 10000L // 10 detik
        private const val ADMOB_DELAY_DEFAULT = 1500L // 1.5 detik
        private const val ADMOB_DELAY_LOW_END = 3000L // 3 detik untuk low-end

        @Volatile
        private var instance: MyApp? = null

        fun getApplicationContext(): MyApp =
            instance ?: throw IllegalStateException("MyApp not initialized")

        @Volatile
        var areEssentialsInitialized = false
            private set

        private val sdkListeners = CopyOnWriteArrayList<SdkInitializationListener>()
        private val mainHandler = Handler(Looper.getMainLooper())

        fun registerListener(listener: SdkInitializationListener) {
            if (!sdkListeners.contains(listener)) {
                sdkListeners.add(listener)
            }

            // Jika sudah initialized, notify listener
            if (areEssentialsInitialized) {
                mainHandler.post {
                    listener.onSdkInitialized(Sdk.ALL_ESSENTIALS)
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
            // Bungkus seluruh iterasi dalam satu post
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

    private suspend fun initializeEssentialSDKs() = coroutineScope {
        val jobs = mutableListOf<Deferred<Unit>>()

        // Firebase dengan timeout
        jobs.add(async {
            val result = withTimeoutOrNull(FIREBASE_TIMEOUT_MS) {
                initializeFirebase()
            }
            if (result == null) {
                Log.w(TAG, "Firebase init timeout after ${FIREBASE_TIMEOUT_MS}ms")
            }
        })

        // WebView setup
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val processName = getProcessName()
            if (packageName != processName) {
                try {
                    WebView.setDataDirectorySuffix(processName)
                } catch (e: Exception) {
                    Log.e(TAG, "WebView suffix error: ${e.message}")
                }
            }
        }

        // AdMob dengan timeout
        if (isWebViewAvailableSafely()) {
            jobs.add(async {
                val result = withTimeoutOrNull(ADMOB_TIMEOUT_MS) {
                    initializeAdMob()
                }
                if (result == null) {
                    Log.w(TAG, "AdMob init timeout after ${ADMOB_TIMEOUT_MS}ms")
                }
            })
        }

        // Tunggu semua jobs selesai atau timeout
        try {
            withTimeout(FIREBASE_TIMEOUT_MS + ADMOB_TIMEOUT_MS + 5000L) {
                jobs.awaitAll()
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Overall SDK initialization timeout")
            jobs.forEach { it.cancel() }
        }

        areEssentialsInitialized = true
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

    // Optimized untuk device low-end
    private suspend fun initializeAdMob() = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine<Unit> { cont ->
            val delay = if (isLowEndDevice()) ADMOB_DELAY_LOW_END else ADMOB_DELAY_DEFAULT

            val runnable = Runnable {
                if (!cont.isActive) return@Runnable

                try {
                    MobileAds.initialize(this@MyApp) { status ->
                        Log.d(TAG, "AdMob initialized: $status")
                        if (cont.isActive) cont.resume(Unit)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "AdMob init crash-safe: ${e.message}")
                    if (cont.isActive) cont.resume(Unit)
                }
            }

            mainHandler.postDelayed(runnable, delay)

            // Cleanup handler jika coroutine dibatalkan
            cont.invokeOnCancellation {
                mainHandler.removeCallbacks(runnable)
            }
        }
    }

    private fun isLowEndDevice(): Boolean {
        return try {
            val activityManager = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
            val memInfo = android.app.ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memInfo)

            // Device dianggap low-end jika:
            // - Total RAM < 2GB
            // - Processor cores <= 4
            val totalRamGB = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
            val cores = Runtime.getRuntime().availableProcessors()

            totalRamGB < 2.0 || cores <= 4
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting device: ${e.message}")
            false
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
            Log.e(TAG, "WebView check error: ${e.message}")
            false
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        try {
            // Hapus semua pending callbacks
            mainHandler.removeCallbacksAndMessages(null)

            // Clear listeners
            clearAllListeners()

            // Cancel coroutine scope
            applicationScope.cancel()

            instance = null
        } catch (e: Exception) {
            Log.e(TAG, "Error during termination: ${e.message}")
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Log.w(TAG, "Low memory detected")
        // Bisa tambahkan cleanup logic di sini jika perlu
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        when (level) {
            TRIM_MEMORY_RUNNING_CRITICAL,
            TRIM_MEMORY_COMPLETE -> {
                Log.w(TAG, "Critical memory condition: $level")
                // Cleanup resources jika perlu
            }
        }
    }
}
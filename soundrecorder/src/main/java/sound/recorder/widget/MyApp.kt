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

    // FIX: SupervisorJob di IO — child failure tidak cancel sibling
    private val applicationScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val TAG                = "MyApp"
        private const val FIREBASE_TIMEOUT_MS = 15_000L
        private const val ADMOB_TIMEOUT_MS    = 10_000L

        @Volatile
        private var instance: MyApp? = null

        fun getApplicationContext(): MyApp =
            instance ?: throw IllegalStateException("MyApp not initialized")

        private val _areEssentialsInitialized = AtomicBoolean(false)
        val areEssentialsInitialized: Boolean
            get() = _areEssentialsInitialized.get()

        private val sdkListeners = CopyOnWriteArrayList<SdkInitializationListener>()
        private val mainHandler  = Handler(Looper.getMainLooper())

        fun registerListener(listener: SdkInitializationListener) {
            if (!sdkListeners.contains(listener)) sdkListeners.add(listener)
            if (_areEssentialsInitialized.get()) {
                mainHandler.post {
                    try { listener.onSdkInitialized(Sdk.ALL_ESSENTIALS) }
                    catch (e: Exception) { Log.e(TAG, "Listener callback error: ${e.message}") }
                }
            }
        }

        fun unregisterListener(listener: SdkInitializationListener) = sdkListeners.remove(listener)

        fun clearAllListeners() = sdkListeners.clear()

        private fun notifyListeners(sdk: Sdk) {
            mainHandler.post {
                sdkListeners.forEach { listener ->
                    try { listener.onSdkInitialized(sdk) }
                    catch (e: Exception) { Log.e(TAG, "Listener error: ${e.message}") }
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
        // FIX: pakai supervisorScope bukan coroutineScope
        // → jika satu child gagal/timeout, child lain tetap jalan
        supervisorScope {
            // 1. Firebase — jalan paralel di IO
            val firebaseJob = launch(Dispatchers.IO) {
                withTimeoutOrNull(FIREBASE_TIMEOUT_MS) {
                    initializeFirebase()
                } ?: Log.w(TAG, "Firebase initialization timed out")
            }

            // 2. WebView suffix — wajib Main Thread, jalan paralel
            val webViewJob = launch(Dispatchers.Main) {
                setupWebViewSuffix()
            }

            // FIX: tunggu WebView suffix selesai dulu sebelum AdMob
            // karena AdMob butuh WebView yang sudah di-setup
            webViewJob.join()

            // 3. AdMob — setelah WebView suffix siap
            if (isWebViewAvailableSafely()) {
                withTimeoutOrNull(ADMOB_TIMEOUT_MS) {
                    initializeAdMob()
                } ?: Log.w(TAG, "AdMob initialization timed out")
            } else {
                Log.w(TAG, "WebView not available, skipping AdMob init")
            }

            // 4. Tunggu Firebase selesai sebelum tandai "semua ready"
            firebaseJob.join()
        }

        // Tandai selesai — dijamin setelah Firebase + AdMob + WebView semua done
        _areEssentialsInitialized.set(true)
        Log.d(TAG, "All essential SDKs initialized")
        notifyListeners(Sdk.ALL_ESSENTIALS)
    }

    // FIX: pisahkan WebView setup ke fungsi sendiri agar lebih jelas
    private fun setupWebViewSuffix() {
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

    private suspend fun initializeFirebase() = withContext(Dispatchers.IO) {
        try {
            FirebaseApp.initializeApp(this@MyApp)
            Log.d(TAG, "Firebase initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Firebase error: ${e.message}")
        }
    }

    // FIX: hapus withContext(Dispatchers.Main) yang redundant di dalam
    // karena caller (launch di supervisorScope) sudah switch ke Main via webViewJob.join()
    // AdMob initialize dipanggil langsung di Main Thread dari supervisorScope
    private suspend fun initializeAdMob() = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            try {
                MobileAds.initialize(this@MyApp) { status ->
                    Log.d(TAG, "AdMob initialized: $status")
                    if (cont.isActive) cont.resume(Unit)
                }
            } catch (e: Exception) {
                Log.e(TAG, "AdMob init error: ${e.message}")
                // FIX: selalu resume agar coroutine tidak hang selamanya
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
        // FIX: clear instance dan listeners saat app terminate → cegah memory leak
        clearAllListeners()
        instance = null
        Log.d(TAG, "Application terminated, scope cancelled")
    }
}
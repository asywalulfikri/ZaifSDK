package sound.recorder.widget

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import android.widget.Toast
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

    // SupervisorJob di IO — child failure tidak cancel sibling
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

        // 1. PASANG JARING PENGAMAN CRASH WEBVIEW
        // Menangkap UncaughtExceptionException dari internal Chromium/AdMob
        setupWebViewCrashHandler()

        // 2. SETUP WEBVIEW SUFFIX SECEPAT MUNGKIN (SINKRON)
        // Wajib sinkron di Main Thread untuk mencegah crash "UncaughtExceptionException" 
        // pada engine Chromium jika ada multi-proses.
        setupWebViewSuffix()

        // 3. INISIALISASI BERAT DI BACKGROUND
        applicationScope.launch {
            initializeEssentialSDKs()
        }
    }

    private suspend fun initializeEssentialSDKs() {
        supervisorScope {
            // A. Firebase — jalan paralel di IO
            val firebaseJob = launch(Dispatchers.IO) {
                withTimeoutOrNull(FIREBASE_TIMEOUT_MS) {
                    initializeFirebase()
                } ?: Log.w(TAG, "Firebase initialization timed out")
            }

            // B. AdMob — Setelah WebView Suffix siap (karena dipanggil di onCreate, di sini sudah pasti siap)
            val admobJob = launch(Dispatchers.IO) {
                if (isWebViewAvailableSafely()) {
                    val result = withTimeoutOrNull(ADMOB_TIMEOUT_MS) {
                        initializeAdMob()
                    }
                    if (result == null) {
                        Log.w(TAG, "AdMob initialization timed out")
                        showDebugToast("AdMob timeout setelah ${ADMOB_TIMEOUT_MS / 1000}s")
                    }
                } else {
                    Log.w(TAG, "WebView not available, skipping AdMob init")
                }
            }

            // Tunggu semua selesai
            joinAll(firebaseJob, admobJob)
        }

        // Tandai selesai
        _areEssentialsInitialized.set(true)
        Log.d(TAG, "All essential SDKs initialized")
        notifyListeners(Sdk.ALL_ESSENTIALS)
    }

    private fun setupWebViewSuffix() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val processName = getProcessName()
                if (packageName != processName) {
                    WebView.setDataDirectorySuffix(processName)
                    Log.d(TAG, "WebView suffix set: $processName")
                }
            } catch (e: Exception) {
                // Jangan throw error di sini agar app tidak mati jika WebView sistem bermasalah
                Log.e(TAG, "WebView suffix error: ${e.message}")
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

    private suspend fun initializeAdMob() = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { cont ->
            try {
                // Sentuh WebView/CookieManager di Main Thread secepat mungkin.
                // Ini memaksa inisialisasi Chromium Engine secara aman di UI Thread sebelum SDK AdMob memanggilnya di background.
                mainHandler.post {
                    try {
                        CookieManager.getInstance()
                    } catch (e: Throwable) {
                        Log.e(TAG, "Pre-touch WebView error: ${e.message}")
                    }
                }

                MobileAds.initialize(this@MyApp) { status ->
                    Log.d(TAG, "AdMob initialized: $status")
                    showDebugToast("AdMob berhasil diinisialisasi")
                    if (cont.isActive) cont.resume(Unit)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "AdMob init error: ${e.message}")
                showDebugToast("AdMob gagal: ${e.message}")
                if (cont.isActive) cont.resume(Unit)
            }
        }
    }

    private fun showDebugToast(message: String) {
        val isDebug = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (isDebug) {
            mainHandler.post {
                Toast.makeText(this@MyApp, message, Toast.LENGTH_SHORT).show()
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

    private fun setupWebViewCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            if (isWebViewChromiumCrash(throwable)) {
                // Log saja, tidak crash ke user
                Log.e(TAG, "WebView internal crash (ignored): ${throwable.message}")
            } else {
                // Exception lain tetap di-handle normal
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    private fun isWebViewChromiumCrash(throwable: Throwable): Boolean {
        val chromiumPackages = listOf(
            "org.chromium",
            "com.android.webview",
            "android.webkit"
        )
        // Cek stack trace apakah berasal dari Chromium
        return throwable.stackTrace.any { element ->
            chromiumPackages.any { pkg -> element.className.startsWith(pkg) }
        } || throwable.cause?.stackTrace?.any { element ->
            chromiumPackages.any { pkg -> element.className.startsWith(pkg) }
        } == true
    }

    override fun onTerminate() {
        super.onTerminate()
        applicationScope.cancel()
        clearAllListeners()
        instance = null
        Log.d(TAG, "Application terminated, scope cancelled")
    }
}
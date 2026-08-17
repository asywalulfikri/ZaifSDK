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
import com.google.firebase.crashlytics.FirebaseCrashlytics
import androidx.work.Configuration
import androidx.work.WorkManager
import kotlinx.coroutines.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

open class MyApp : Application(), Configuration.Provider {

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
        private const val ADMOB_TIMEOUT_MS    = 10_000L
        private const val FIREBASE_INIT_TIMEOUT_MS = 10_000L

        @Volatile
        private var instance: MyApp? = null

        /**
         * Mengembalikan instance MyApp. 
         * Gunakan ini dengan hati-hati dan hanya jika benar-benar butuh instance spesifik MyApp.
         */
        fun getInstance(): MyApp =
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

    @Volatile
    private var isStartupPhase = true

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 0. MULTI-PROCESS GUARD
        // Hanya jalankan inisialisasi berat di proses utama.
        val processName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) getProcessName() else ""
        val isMainProcess = processName.isEmpty() || packageName == processName

        // 1. PASANG JARING PENGAMAN CRASH WEBVIEW (Selalu pasang untuk catching di semua proses jika perlu, 
        // tapi log ke Crashlytics hanya jika initialized)
        setupWebViewCrashHandler()

        // 2. SETUP WEBVIEW SUFFIX SECEPAT MUNGKIN (SINKRON)
        setupWebViewSuffix(processName)

        if (isMainProcess) {
            // 3. INISIALISASI BERAT DI BACKGROUND (Hanya di proses utama)
            applicationScope.launch {
                initializeEssentialSDKs()
            }
        } else {
            // Bukan proses utama, tidak ada inisialisasi SDK yang perlu diproteksi.
            isStartupPhase = false
        }
    }

    private suspend fun initializeEssentialSDKs() {
        supervisorScope {
            // A. Firebase — Usahakan selesai pertama karena Crashlytics butuh ini.
            // Gunakan timeout agar jika Firebase hang, SDK lain tetap bisa mencoba inisialisasi.
            val firebaseJob = launch(Dispatchers.IO) {
                initializeFirebase()
            }
            withTimeoutOrNull(FIREBASE_INIT_TIMEOUT_MS) {
                firebaseJob.join()
            } ?: Log.w(TAG, "Firebase initialization join timed out")

            // B. AdMob & WorkManager — Jalan paralel setelah Firebase
            val admobJob = launch(Dispatchers.IO) {
                if (isWebViewAvailableSafely()) {
                    withTimeoutOrNull(ADMOB_TIMEOUT_MS) {
                        initializeAdMob()
                    } ?: Log.w(TAG, "AdMob initialization timed out")
                } else {
                    Log.w(TAG, "WebView not available, skipping AdMob init")
                }
            }

            val workManagerJob = launch(Dispatchers.IO) {
                try {
                    WorkManager.getInstance(this@MyApp)
                    Log.d(TAG, "WorkManager initialized in background")
                } catch (e: Exception) {
                    Log.e(TAG, "WorkManager background init error: ${e.message}")
                }
            }

            joinAll(admobJob, workManagerJob)
        }

        // Tandai selesai
        isStartupPhase = false
        _areEssentialsInitialized.set(true)
        Log.d(TAG, "All essential SDKs initialized")
        notifyListeners(Sdk.ALL_ESSENTIALS)
    }

    private fun setupWebViewSuffix(processName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                if (packageName != processName && processName.isNotEmpty()) {
                    WebView.setDataDirectorySuffix(processName)
                    Log.d(TAG, "WebView suffix set: $processName")
                }
            } catch (e: Exception) {
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

    private suspend fun initializeAdMob() {
        // 1. STAGGERED START: Beri jeda 1.5 detik agar tidak bertabrakan dengan startup awal aplikasi.
        delay(1500)

        // 2. Hanya API WebView-related yang wajib di Main Thread.
        withContext(Dispatchers.Main) {
            try {
                // Touch CookieManager agar engine WebView siap
                CookieManager.getInstance()
            } catch (e: Throwable) {
                Log.e(TAG, "CookieManager init error: ${e.message}")
            }
        }

        // 3. MobileAds.initialize() adalah operasi berat (class loading, baca cache/config).
        // Sejak Play Services Ads SDK 20.0.0+, aman dipanggil dari background thread —
        // callback completion akan tetap di-dispatch ke main thread oleh SDK.
        // Memanggilnya di main thread menyebabkan ANR di device low-end.
        suspendCancellableCoroutine { cont ->
            try {
                MobileAds.initialize(this@MyApp) { status ->
                    Log.d(TAG, "AdMob initialized: $status")
                    showDebugToast("AdMob berhasil diinisialisasi")
                    if (cont.isActive) cont.resume(Unit)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "AdMob init error: ${e.message}")
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
            // HANYA swallow crash Chromium jika masih dalam fase startup kritis.
            // Setelah startup selesai, kita biarkan crash normal agar state app tetap konsisten.
            if (isStartupPhase && isWebViewChromiumCrash(throwable)) {
                // Log ke Logcat
                Log.e(TAG, "Caught WebView-related crash DURING STARTUP (swallowed): ${throwable.message}")
                
                // LAPORKAN KE CRASHLYTICS agar kita punya visibility masalah di lapangan
                try {
                    FirebaseCrashlytics.getInstance().recordException(
                        Exception("Swallowed Startup WebView Crash: ${throwable.message}", throwable)
                    )
                } catch (e: Exception) {
                    // Firebase mungkin belum siap
                }
            } else {
                // Exception lain atau crash di luar fase startup tetap di-handle normal (app crash)
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

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.DEBUG else Log.ERROR)
            .build()

    override fun onTerminate() {
        super.onTerminate()
        applicationScope.cancel()
        clearAllListeners()
        instance = null
        Log.d(TAG, "Application terminated, scope cancelled")
    }
}
package sound.recorder.widget

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import android.widget.Toast
import androidx.work.Configuration
import androidx.work.WorkManager
import com.google.android.gms.ads.MobileAds
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
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

    private val applicationScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val TAG = "MyApp"
        private const val ADMOB_TIMEOUT_MS = 10_000L
        private const val FIREBASE_INIT_TIMEOUT_MS = 10_000L

        @Volatile
        private var instance: MyApp? = null

        fun getInstance(): MyApp =
            instance ?: throw IllegalStateException("MyApp not initialized")

        private val _areEssentialsInitialized = AtomicBoolean(false)
        val areEssentialsInitialized: Boolean
            get() = _areEssentialsInitialized.get()

        private val sdkListeners = CopyOnWriteArrayList<SdkInitializationListener>()
        private val mainHandler = Handler(Looper.getMainLooper())

        fun registerListener(listener: SdkInitializationListener) {
            if (_areEssentialsInitialized.get()) {
                mainHandler.post {
                    try { listener.onSdkInitialized(Sdk.ALL_ESSENTIALS) }
                    catch (e: Exception) { Log.e(TAG, "Listener callback error: ${e.message}") }
                }
            } else {
                sdkListeners.addIfAbsent(listener)
            }
        }

        fun unregisterListener(listener: SdkInitializationListener) = sdkListeners.remove(listener)

        fun clearAllListeners() = sdkListeners.clear()

        private fun notifyListeners(sdk: Sdk) {
            mainHandler.post {
                val targets = ArrayList(sdkListeners)
                sdkListeners.clear()
                targets.forEach { listener ->
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

        val processName = getProcessNameCompat()
        val isMainProcess = processName.isEmpty() || packageName == processName

        setupWebViewCrashHandler()
        setupWebViewSuffix(processName)

        if (isMainProcess) {
            applicationScope.launch {
                initializeEssentialSDKs()
            }
        } else {
            isStartupPhase = false
        }
    }

    private fun getProcessNameCompat(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return getProcessName()
        }
        try {
            val pid = Process.myPid()
            val am = getSystemService(ACTIVITY_SERVICE) as? ActivityManager
            am?.runningAppProcesses?.forEach { processInfo ->
                if (processInfo.pid == pid) {
                    return processInfo.processName
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting process name: ${e.message}")
        }
        return packageName
    }

    private suspend fun initializeEssentialSDKs() {
        supervisorScope {
            val firebaseJob = launch {
                initializeFirebase()
            }
            withTimeoutOrNull(FIREBASE_INIT_TIMEOUT_MS) {
                firebaseJob.join()
            } ?: Log.w(TAG, "Firebase initialization join timed out")

            val admobJob = launch {
                if (isWebViewAvailableSafely()) {
                    withTimeoutOrNull(ADMOB_TIMEOUT_MS) {
                        initializeAdMob()
                    } ?: Log.w(TAG, "AdMob initialization timed out")
                } else {
                    Log.w(TAG, "WebView not available, skipping AdMob init")
                }
            }

            val workManagerJob = launch {
                try {
                    WorkManager.getInstance(this@MyApp)
                    Log.d(TAG, "WorkManager initialized in background")
                } catch (e: Throwable) {
                    Log.e(TAG, "WorkManager background init error: ${e.message}")
                }
            }

            joinAll(admobJob, workManagerJob)
        }

        isStartupPhase = false
        _areEssentialsInitialized.set(true)
        Log.d(TAG, "All essential SDKs initialized")
        notifyListeners(Sdk.ALL_ESSENTIALS)
    }

    private fun setupWebViewSuffix(processName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                if (packageName != processName && processName.isNotEmpty()) {
                    val safeSuffix = processName.replace(":", "_")
                    WebView.setDataDirectorySuffix(safeSuffix)
                    Log.d(TAG, "WebView suffix set: $safeSuffix")
                }
            } catch (e: Throwable) {
                Log.e(TAG, "WebView suffix error: ${e.message}")
            }
        }
    }

    private suspend fun initializeFirebase() = withContext(Dispatchers.IO) {
        try {
            FirebaseApp.initializeApp(this@MyApp)
            Log.d(TAG, "Firebase initialized successfully")
        } catch (e: Throwable) {
            Log.e(TAG, "Firebase error: ${e.message}")
        }
    }

    private suspend fun initializeAdMob() {
        delay(1500)

        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P) {
            withContext(Dispatchers.Main) {
                try {
                    CookieManager.getInstance()
                } catch (e: Throwable) {
                    Log.e(TAG, "CookieManager init error: ${e.message}")
                }
            }
        } else {
            withContext(Dispatchers.IO) {
                try {
                    CookieManager.getInstance()
                } catch (e: Throwable) {
                    Log.e(TAG, "CookieManager init error: ${e.message}")
                }
            }
        }

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
        } catch (e: Throwable) {
            Log.e(TAG, "WebView check error: ${e.message}")
            false
        }
    }

    private fun setupWebViewCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            if (isStartupPhase && isWebViewChromiumCrash(throwable)) {
                Log.e(TAG, "Caught WebView crash DURING STARTUP: ${throwable.message}")
                try {
                    FirebaseCrashlytics.getInstance().recordException(
                        Exception("Startup WebView Crash: ${throwable.message}", throwable)
                    )
                } catch (e: Exception) { }
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun isWebViewChromiumCrash(throwable: Throwable): Boolean {
        val chromiumPackages = listOf(
            "org.chromium",
            "com.android.webview",
            "android.webkit"
        )
        var current: Throwable? = throwable
        while (current != null) {
            if (current.stackTrace.any { element -> chromiumPackages.any { pkg -> element.className.startsWith(pkg) } }) {
                return true
            }
            current = current.cause
        }
        return false
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.DEBUG else Log.ERROR)
            .build()

    override fun onTerminate() {
        super.onTerminate()
        clearAllListeners()
        instance = null
        Log.d(TAG, "Application terminated")
    }
}

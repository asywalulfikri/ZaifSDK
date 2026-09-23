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
        private const val FIREBASE_INIT_TIMEOUT_MS = 10_000L

        @Volatile
        private var instance: MyApp? = null

        fun getInstance(): MyApp =
            instance ?: throw IllegalStateException("MyApp not initialized")

        private val _areEssentialsInitialized = AtomicBoolean(false)
        val areEssentialsInitialized: Boolean
            get() = _areEssentialsInitialized.get()

        private val sdkListeners = CopyOnWriteArrayList<SdkInitializationListener>()
        private val listenerLock = Any()
        private val mainHandler = Handler(Looper.getMainLooper())

        private val adMobInitializationStarted = AtomicBoolean(false)

        fun registerListener(listener: SdkInitializationListener) {
            val initialized = synchronized(listenerLock) {
                if (!_areEssentialsInitialized.get()) {
                    sdkListeners.addIfAbsent(listener)
                }
                _areEssentialsInitialized.get()
            }

            if (initialized) {
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
                val targets = synchronized(listenerLock) {
                    ArrayList(sdkListeners).also { sdkListeners.clear() }
                }
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
            } ?: run {
                firebaseJob.cancel()
                Log.w(TAG, "Firebase initialization timed out")
            }

            val workManagerJob = launch {
                try {
                    WorkManager.getInstance(this@MyApp)
                    Log.d(TAG, "WorkManager initialized in background")
                } catch (e: Throwable) {
                    Log.e(TAG, "WorkManager background init error: ${e.message}")
                }
            }

            // AdMob must not delay the application's essential startup. Its
            // initialization may touch Google Play services/WebView and can
            // block on vendor devices. Ads are loaded later by the Activity
            // when a valid ad container is available.
            initializeAdMobInBackground()

            workManagerJob.join()
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

    private fun initializeAdMobInBackground() {
        if (!adMobInitializationStarted.compareAndSet(false, true)) return

        applicationScope.launch(Dispatchers.IO) {
            // Give the first Activity a chance to render before Google Play
            // services/AdMob performs its one-time initialization.
            delay(1500)
            try {
                MobileAds.initialize(applicationContext) { status ->
                    Log.d(TAG, "AdMob initialized: $status")
                    showDebugToast("AdMob berhasil diinisialisasi")
                }
            } catch (e: Throwable) {
                // Advertising must never prevent the host app from starting.
                Log.e(TAG, "AdMob init error: ${e.message}", e)
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

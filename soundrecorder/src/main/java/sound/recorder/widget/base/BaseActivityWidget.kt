package sound.recorder.widget.base

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.animation.LinearInterpolator
import android.webkit.WebView
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback
import com.google.android.gms.tasks.Task
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.google.firebase.messaging.FirebaseMessaging
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import sound.recorder.widget.BuildConfig
import sound.recorder.widget.R
import sound.recorder.widget.RecordingSDK
import sound.recorder.widget.ads.AdConfigProvider
import sound.recorder.widget.builder.AdmobSDKBuilder
import sound.recorder.widget.listener.MyAdsListener
import sound.recorder.widget.notes.Note
import sound.recorder.widget.util.DataSession
import sound.recorder.widget.util.Toastic
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException


open class BaseActivityWidget : AppCompatActivity() {

    private var mInterstitialAd: InterstitialAd? = null
    private var isLoad = false
    private var isLoadInterstitialReward = false
    private var rewardedInterstitialAd: RewardedInterstitialAd? = null

    private var adView: AdView? = null
    private var adView2: AdView? = null

    private lateinit var consentInformation: ConsentInformation
    private var TAG = "GDPR_App"

    private var isPrivacyOptionsRequired: Boolean = false


    var sharedPreferences: SharedPreferences? = null

    private var appOpenAd: AppOpenAd? = null
    var mPanAnim: Animation? = null


    private var bannerRetryCount = 0
    private val maxBannerRetry = 2

    private var isBannerLoaded = false

    private var isLoading = false


    @Volatile
    private var isBannerLoading = false

    private val adLoadTimeout = 10_000L // 10 detik timeout
    private var loadTimeoutRunnable: Runnable? = null

    private var rewardedAd: RewardedAd? = null


    //banner home
    private var loadBannerRunnable: Runnable? = null

    private var bannerContainerRef: WeakReference<FrameLayout>? = null


    //interstitial admob handle
    private var reloadJob: Job? = null
    private var retryJob: Job? = null

    private var retryCount = 0

    private val MAX_RETRY = 2
    private val RETRY_DELAY = 60_000L     // 60 detik
    private val NEXT_LOAD_DELAY = 90_000L // 90 detik

    private var lastShowTime = 0L

    private val SHOW_INTERVAL = 90_000L
    private val LOAD_INTERVAL = 90_000L




    companion object {
        // Tempat untuk "menitipkan" implementasi kontrak dari aplikasi
        var adConfigProvider: AdConfigProvider? = null
    }

    val admobSDKBuilder: AdmobSDKBuilder?
        get() = adConfigProvider?.getAdmobBuilder()

    protected lateinit var updateHelper: InAppUpdateHelper


    private var hasCheckedUpdateThisSession = false

    open fun getUpdateType(): Int = AppUpdateType.FLEXIBLE
    open fun enableInAppUpdate(): Boolean = true

    private var bannerHandler: Handler? = null
    private var bannerRetryRunnable: Runnable? = null
    private var bannerTimeoutRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        updateHelper = InAppUpdateHelper(
            this,
            getUpdateType()
        )
    }

    open fun getAdBannerContainer(): FrameLayout? {
        return null // Nilai default
    }

    fun isLanguageIdEn(context: Context): Boolean {
        val deviceLanguage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.resources.configuration.locales[0].language
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale.language
        }

        return deviceLanguage == "id" || deviceLanguage == "en" || deviceLanguage == "in"
    }


    /*fun loadBannerHome(view: FrameLayout?) {
        try {
            val bannerId = admobSDKBuilder?.bannerHomeId.toString()
            setLog("ADS_Admob", "home banner id = $bannerId")

            if (adView == null) {
                adView = AdView(this).apply {
                    setAdSize(AdSize.BANNER)
                    adUnitId = bannerId

                    // 🔒 PENTING: cegah AdMob mencuri focus UI
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                }


                if (adView?.parent !== view) {
                    (adView?.parent as? ViewGroup)?.removeView(adView)
                    view?.removeAllViews()
                    view?.addView(adView)
                }

            }

            // Load Ad hanya sekali
            if (!isBannerLoaded) {
                val adRequest = AdRequest.Builder().build()
                adView?.loadAd(adRequest)
                isBannerLoaded = true
            } else {
                setLog("ADS_Admob", "Banner already loaded, skip duplicate load")
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
*/



    protected fun loadBannerHome(container: FrameLayout?) {
        val bannerId = admobSDKBuilder?.bannerHomeId.orEmpty()
        if (container == null || bannerId.isBlank()) return
        if (isBannerLoaded || isLoading) return
        if (!isAlive()) return

        bannerContainerRef = WeakReference(container)

        loadBannerRunnable?.let { container.removeCallbacks(it) }

        val runnable = Runnable {
            val safeContainer = bannerContainerRef?.get()
            if (!isAlive() || safeContainer == null || !safeContainer.isAttachedToWindow) return@Runnable

            try {
                initAdView(safeContainer, bannerId)
                loadAdWithTimeout()
            } catch (e: Exception) {
                isLoading = false
                Log.e("Ads", "Banner error", e)
            }
        }

        loadBannerRunnable = runnable
        container.postDelayed(runnable, 300)
    }

    private fun isAlive() = !isFinishing && !isDestroyed

    private fun loadAdWithTimeout() {
        if (!isAlive()) return
        isLoading = true

        loadTimeoutRunnable?.let { adView?.removeCallbacks(it) }

        val timeout = Runnable {
            if (isLoading) {
                isLoading = false
                Log.e("Ads", "Banner load timeout")
            }
        }

        loadTimeoutRunnable = timeout
        adView?.postDelayed(timeout, adLoadTimeout)

        adView?.loadAd(AdRequest.Builder().build())
    }


    private fun cancelTimeout() {
        loadTimeoutRunnable?.let { adView?.removeCallbacks(it) }
        loadTimeoutRunnable = null
    }

    private fun createAdListener() = object : AdListener() {

        override fun onAdLoaded() {
            cancelTimeout()
            isLoading = false
            isBannerLoaded = true
            Log.d("Ads", "Banner loaded")
        }

        override fun onAdFailedToLoad(error: LoadAdError) {
            cancelTimeout()
            isLoading = false
            Log.e("Ads", "Banner failed: ${error.message}")
        }
    }

    private fun initAdView(container: FrameLayout, bannerId: String) {
        if (adView == null) {
            adView = AdView(this).apply {
                adUnitId = bannerId
                setAdSize(AdSize.BANNER)
                adListener = createAdListener()
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            }
        }

        (adView?.parent as? ViewGroup)?.removeView(adView)
        container.removeAllViews()
        container.addView(adView)
    }


    private fun getAdSize2(): AdSize {
        // 1. Ambil lebar layar dalam pixel dengan cara paling modern (API 30+)
        val widthPixels = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = windowManager.currentWindowMetrics
            val insets = windowMetrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.navigationBars() or WindowInsets.Type.displayCutout()
            )
            val insetsWidth = insets.left + insets.right
            windowMetrics.bounds.width() - insetsWidth
        } else {
            @Suppress("DEPRECATION")
            val displayMetrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(displayMetrics)
            displayMetrics.widthPixels
        }

        // 2. Konversi pixel ke DP (Density-independent Pixels)
        val density = resources.displayMetrics.density
        val adWidthDp = (widthPixels / density).toInt()

        // 3. Gunakan Inline Adaptive Banner (LEBIH BAGUS dari standar)
        // Kita batasi tinggi maksimal 60dp agar tidak menutupi tombol gendang
        val maxHeightDp = 60

        return AdSize.getInlineAdaptiveBannerAdSize(adWidthDp, maxHeightDp)
    }

    @SuppressLint("WrongConstant")
    fun setupHideStatusBar(rootView: View?, hide: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // API 30+ (Android 11 dan lebih baru)
            try {
                // Hide the status and navigation bars
                window.insetsController?.apply {
                    hide(WindowInsets.Type.statusBars()) // Hide status bar
                    if (hide) {
                        hide(WindowInsets.Type.navigationBars()) // Hide navigation bar
                    }
                    systemBarsBehavior =
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }

                // Ensure the root view gets the correct padding and margins
                if (rootView != null) {
                    ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, windowInsets ->
                        val insets = windowInsets.getInsets(WindowInsets.Type.systemBars())
                        val layoutParams = view.layoutParams as ViewGroup.MarginLayoutParams
                        layoutParams.rightMargin = insets.right
                        view.layoutParams = layoutParams
                        windowInsets
                    }
                }
            } catch (e: Exception) {
                Log.e("FullscreenSetup", "Error handling insets: ${e.message}")
            }
        } else {
            // API < 30 (Android 10 dan sebelumnya)
            try {
                window.decorView.systemUiVisibility =
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                rootView?.setOnApplyWindowInsetsListener { view, windowInsets ->
                    val rightInset = windowInsets.systemWindowInsetRight
                    val layoutParams = view.layoutParams as ViewGroup.MarginLayoutParams
                    layoutParams.rightMargin = rightInset
                    view.layoutParams = layoutParams
                    windowInsets
                }
            } catch (e: Exception) {
                Log.e("FullscreenSetup", "Error handling insets for API < 30: ${e.message}")
            }
        }
    }

    protected fun setupFragment(id: Int, fragment: Fragment?) {
        try {
            if (fragment != null) {
                val fragmentManager = supportFragmentManager
                val fragmentTransaction = fragmentManager.beginTransaction()
                fragmentTransaction.replace(id, fragment)
                fragmentTransaction.commit()
            }
        } catch (e: Exception) {
            setLog(e.message.toString())
        }
    }



    override fun onStart() {
        super.onStart()
        if (enableInAppUpdate() && !hasCheckedUpdateThisSession) {
            updateHelper.checkUpdate()
            hasCheckedUpdateThisSession = true
        }
    }
    override fun onDestroy() {

        if (enableInAppUpdate()) {
            updateHelper.onDestroy()
        }
        cleanBannerHome()

        ///////////////////

        cleanupBannerAd()
        bannerHandler?.removeCallbacksAndMessages(null)
        bannerHandler = null

        isBannerLoaded = false
        isLoading = false
        mInterstitialAd = null
        rewardedAd = null

        reloadJob?.cancel()
        retryJob?.cancel()

        super.onDestroy()
    }


    override fun onPause() {
        super.onPause()
        if (adView != null) {
            adView?.pause()
        }
        if(adView2!=null){
            adView2?.pause()
        }
    }


    override fun onResume() {
        super.onResume()
        if (enableInAppUpdate()) {
            updateHelper.onResume()
        }
        if (adView != null) {
            adView?.resume()
        }
        if(adView2!=null){
            adView2?.resume()
        }
    }

    fun setupGDPR() {
        if (isAdMobAvailable()) {
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val params = ConsentRequestParameters
                        .Builder()
                        .setTagForUnderAgeOfConsent(false)
                        .build()

                    consentInformation =
                        UserMessagingPlatform.getConsentInformation(this@BaseActivityWidget)
                    isPrivacyOptionsRequired =
                        consentInformation.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

                    consentInformation.requestConsentInfoUpdate(
                        this@BaseActivityWidget,
                        params, {
                            UserMessagingPlatform.loadAndShowConsentFormIfRequired(this@BaseActivityWidget) { loadAndShowError ->
                                loadAndShowError?.let {
                                    Log.w(TAG, String.format("%s: %s", it.errorCode, it.message))
                                }

                                if (isPrivacyOptionsRequired) {
                                    // Regenerate the options menu to include a privacy setting.
                                    UserMessagingPlatform.showPrivacyOptionsForm(this@BaseActivityWidget) { formError ->
                                        formError?.let {
                                            if (BuildConfig.DEBUG) {
                                                setToastTic(Toastic.ERROR, it.message.toString())
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        { requestConsentError ->
                            // Consent gathering failed.
                            Log.w(
                                TAG,
                                String.format(
                                    "%s: %s",
                                    requestConsentError.errorCode,
                                    requestConsentError.message
                                )
                            )
                        })

                    if (consentInformation.canRequestAds()) {
                        //MobileAds.initialize(this@BaseActivityWidget) {}
                    }
                } catch (e: Exception) {
                    Log.d("message", e.message.toString())
                }
            }
        }
    }


    @SuppressLint("SetTextI18n")
    fun showDialogEmail(appName: String, info: String) {

        // custom dialog
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_input_email)
        dialog.setCancelable(true)

        // set the custom dialog components - text, image and button
        val etMessage = dialog.findViewById<View>(R.id.etMessage) as EditText
        val btnSend = dialog.findViewById<View>(R.id.btnSend) as TextView
        val btnCancel = dialog.findViewById<View>(R.id.btnCancel) as TextView


        // if button is clicked, close the custom dialog
        btnSend.setOnClickListener {
            val message = etMessage.text.toString().trim()
            if (message.isEmpty()) {
                setToastTic(Toastic.WARNING, getString(R.string.message_cannot_empty))
                return@setOnClickListener
            } else {
                sendEmail("Feed Back $appName", "$message\n\n\n\nfrom $info")
                dialog.dismiss()
            }
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun sendEmail(subject: String, body: String) {
        try {
            RecordingSDK.openEmail(this, subject, body)
        } catch (e: Exception) {
            setToastTic(Toastic.ERROR, e.message.toString())
        }

    }


    fun showLoadingLayout(context: Context, long: Long) {
        try {
            showLoadingProgress(context, long)
        } catch (e: Exception) {
            setLog(e.message.toString())
        }
    }

    @SuppressLint("SetTextI18n")
    fun showLoadingProgress(context: Context, long: Long) {

        try {
            var dialogLoading: Dialog? = Dialog(context)
            dialogLoading?.requestWindowFeature(Window.FEATURE_NO_TITLE)
            dialogLoading?.setContentView(R.layout.loading_layout)
            dialogLoading?.setCancelable(false)

            dialogLoading?.show()

            val handler = Handler()
            handler.postDelayed({
                val dialog = dialogLoading
                if (dialog != null && dialog.isShowing) {
                    dialog.dismiss()
                    dialogLoading = null // Release the dialog instance
                }
            }, long)
        } catch (e: Exception) {
            Log.d("message", e.message.toString())
        }
    }


    fun getNoteValue(note: Note): String {
        val valueNote = try {
            JSONObject(note.note.toString())
            val value = Gson().fromJson(note.note, Note::class.java)
            // The JSON string is valid
            value.note.toString()

        } catch (e: Exception) {
            // The JSON string is not valid
            note.note
        }

        return valueNote
    }

    fun getTitleValue(note: Note): String {
        var valueNote = ""
        valueNote = try {
            JSONObject(note.note.toString())
            val value = Gson().fromJson(note.title, Note::class.java)
            // The JSON string is valid
            value.note.toString()

        } catch (e: Exception) {
            // The JSON string is not valid
            "No title"
        }

        return valueNote
    }


    private fun isAdMobAvailable(): Boolean {
        return try {
            Class.forName("com.google.android.gms.ads.MobileAds")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }

   /* protected fun loadBannerGame(frameLayout: FrameLayout?, modePortrait : Boolean? = false) {
        try {
            val baseDelay =
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1) 3000L else 1200L

            delay(baseDelay)

            if (isAdMobAvailable()) {
                if(modePortrait==true){
                    executeBannerPortrait(frameLayout)
                }else{
                    executeBanner(frameLayout)
                }
            }
        }catch (e : Exception){
            setLog(e.message.toString()+"")
        }
    }*/

    protected fun loadBannerGame(
        frameLayout: FrameLayout?,
        modePortrait: Boolean = false
    ) {
        if (frameLayout == null) return

        lifecycleScope.launch {
            try {
                val baseDelay = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1) {
                    3000L
                } else {
                    1200L
                }

                delay(baseDelay)

                // Tambahkan check tambahan
                if (isFinishing || isDestroyed || !isActive) return@launch
                if (!frameLayout.isAttachedToWindow) return@launch // ⭐ Tambahan
                if (!isAdMobAvailable()) return@launch

                if (modePortrait) {
                    executeBannerPortrait(frameLayout)
                } else {
                    executeBanner(frameLayout)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e // ⭐ Penting
                setLog("BannerLoad Error: ${e.message}")
            }
        }
    }

    fun setToastADS(message : String){
        if (!isFinishing && !isDestroyed) {
            if(admobSDKBuilder?.showToast==true){
                Toast.makeText(this,message, Toast.LENGTH_LONG).show()
            }
        }

    }





    private fun executeBanner(adViewContainer: FrameLayout?) {
        // ✅ Safety checks
        if (isFinishing || isDestroyed || adViewContainer == null) return
        if (isBannerLoading || bannerRetryCount >= maxBannerRetry) return

        // ✅ Lazy init handler
        if (bannerHandler == null) {
            bannerHandler = Handler(Looper.getMainLooper())
        }

        // ✅ Lazy init AdView
        if (adView2 == null) {
            try {
                adView2 = AdView(applicationContext).apply {
                    adUnitId = admobSDKBuilder?.bannerId.orEmpty()
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    setAdSize(getAdSize2())
                }
            } catch (e: Exception) {
                Log.e("BannerAd", "AdView creation error: ${e.message}")
                return
            }
        }

        loadBannerAd(adViewContainer)
    }

    private fun executeBannerPortrait(adViewContainer: FrameLayout?) {
        // ✅ Safety checks
        if (isFinishing || isDestroyed || adViewContainer == null) return
        if (isBannerLoading || bannerRetryCount >= maxBannerRetry) return

        // ✅ Lazy init handler
        if (bannerHandler == null) {
            bannerHandler = Handler(Looper.getMainLooper())
        }

        // ✅ Lazy init AdView
        if (adView2 == null) {
            try {
                adView2 = AdView(applicationContext).apply {
                    adUnitId = admobSDKBuilder?.bannerId.orEmpty()
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    setAdSize(getAdSize2())
                }
            } catch (e: Exception) {
                Log.e("BannerAd", "AdView creation error: ${e.message}")
                return
            }
        }

        loadBannerAd(adViewContainer)
    }

    private fun loadBannerAd(adViewContainer: FrameLayout) {
        // ✅ Double check before loading
        if (isFinishing || isDestroyed) return

        isBannerLoading = true

        // ✅ Clear all pending callbacks
        bannerRetryRunnable?.let { bannerHandler?.removeCallbacks(it) }
        bannerTimeoutRunnable?.let { bannerHandler?.removeCallbacks(it) }

        // ✅ Setup timeout dengan safe unwrap
        bannerTimeoutRunnable = Runnable {
            if (isBannerLoading && !isFinishing && !isDestroyed) {
                isBannerLoading = false
                handleBannerTimeout(adViewContainer)
            }
        }
        bannerTimeoutRunnable?.let {
            bannerHandler?.postDelayed(it, 10_000L)
        }

        // ✅ Setup AdListener
        adView2?.adListener = object : AdListener() {
            override fun onAdLoaded() {
                // Clear timeout
                bannerTimeoutRunnable?.let { bannerHandler?.removeCallbacks(it) }

                isBannerLoading = false
                bannerRetryCount = 0

                if (isFinishing || isDestroyed) {
                    cleanupBannerAd()
                    return
                }

                try {
                    adView2?.let { ad ->
                        // Remove from old parent
                        (ad.parent as? ViewGroup)?.removeView(ad)

                        // Add to new container
                        adViewContainer.removeAllViews()
                        adViewContainer.addView(ad)

                        Log.d("BannerAd", "✓ Banner loaded successfully")
                    }
                } catch (e: Exception) {
                    Log.e("BannerAd", "Add view error: ${e.message}")
                }
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                // Clear timeout
                bannerTimeoutRunnable?.let { bannerHandler?.removeCallbacks(it) }

                isBannerLoading = false
                bannerRetryCount++

                Log.e("BannerAd", "Failed: ${error.message}, retry: $bannerRetryCount/$maxBannerRetry")

                // ✅ Check retry limit
                if (bannerRetryCount >= maxBannerRetry) {
                    Log.e("BannerAd", "Max retry reached, giving up")
                    return
                }

                if (isFinishing || isDestroyed) return

                // ✅ Exponential backoff: 3s, 6s, 12s (max 15s)
                val delay = (3000L * (1 shl (bannerRetryCount - 1))).coerceAtMost(15_000L)

                bannerRetryRunnable = Runnable {
                    if (!isBannerLoading && !isFinishing && !isDestroyed) {
                        executeBanner(adViewContainer)
                    }
                }
                bannerRetryRunnable?.let {
                    bannerHandler?.postDelayed(it, delay)
                }
            }
        }

        // ✅ Load ad with try-catch
        try {
            adView2?.loadAd(AdRequest.Builder().build())
        } catch (e: Exception) {
            Log.e("BannerAd", "Load ad error: ${e.message}")
            isBannerLoading = false
        }
    }

    private fun handleBannerTimeout(adViewContainer: FrameLayout) {
        Log.e("BannerAd", "⏱️ Load timeout after 10s")

        bannerRetryCount++

        if (bannerRetryCount >= maxBannerRetry) {
            Log.e("BannerAd", "Max retry reached after timeout")
            return
        }

        if (isFinishing || isDestroyed) return

        // ✅ Retry after timeout
        bannerRetryRunnable = Runnable {
            if (!isBannerLoading && !isFinishing && !isDestroyed) {
                executeBanner(adViewContainer)
            }
        }
        bannerRetryRunnable?.let {
            bannerHandler?.postDelayed(it, 5_000L)
        }
    }

    // ✅ Cleanup helper
    private fun cleanupBannerAd() {
        try {
            bannerRetryRunnable?.let { bannerHandler?.removeCallbacks(it) }
            bannerTimeoutRunnable?.let { bannerHandler?.removeCallbacks(it) }
            adView2?.animate()?.cancel()
            adView2?.destroy()
            adView2 = null

            bannerRetryRunnable = null
            bannerTimeoutRunnable = null

            isBannerLoading = false
        } catch (e: Exception) {
            Log.e("BannerAd", "Cleanup error: ${e.message}")
        }
    }


    private fun cleanBannerHome(){
        // 1. Hentikan timeout runnable dari adView
        loadTimeoutRunnable?.let {
            adView?.removeCallbacks(it)
        }

        // 2. Hentikan load runnable dari container
        // Ambil container dari WeakRef secara lokal agar konsisten
        val container = bannerContainerRef?.get()
        loadBannerRunnable?.let {
            container?.removeCallbacks(it)
        }

        // 3. Bersihkan listener (Mencegah callback masuk ke activity yang sedang destroy)
        adView?.adListener = object : AdListener() {} // Dummy listener

        // 4. Hentikan animasi dan hancurkan AdView
        adView?.animate()?.cancel()
        adView?.destroy()

        // 5. Nullified semua property
        adView = null
        bannerContainerRef = null
        loadBannerRunnable = null
        loadTimeoutRunnable = null
    }


    fun permissionNotification(){
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    // Pass any permission you want while launching
                    requestPermissionNotification.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }catch (e : Exception){
            setLog(e.message.toString())
        }

    }

    private val requestPermissionNotification = registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }



    fun setupAppOpenAd() {

        /* if(isWebViewSupported()&&isWebViewAvailable()&&isAdMobAvailable()){
             val adRequest = AdRequest.Builder().build()
             AppOpenAd.load(this, admobSDKBuilder?.appOpenId.toString(), adRequest,getDataSession().getOrientationAds(), object : AppOpenAd.AppOpenAdLoadCallback() {
                 override fun onAdLoaded(ad: AppOpenAd) {
                     appOpenAd = ad
                 }

                 override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                     if(BuildConfig.DEBUG){
                         setToast("openAds " +loadAdError.message)
                     }
                 }
             })
         }*/
    }

    fun hideSystemNavigationBar() {
        val window = window

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val controller = window.insetsController
            if (controller != null) {
                controller.hide(WindowInsets.Type.navigationBars() or WindowInsets.Type.statusBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    )
        }
    }

    private fun ensureInsetsForAd() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.show(WindowInsets.Type.navigationBars())
        } else {
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
    }

    private fun resetInsetsAfterAd() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.hide(WindowInsets.Type.systemBars())
        } else {
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
    }




    fun showOpenAd(){
        if(appOpenAd!=null){
            try {
                appOpenAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        appOpenAd = null
                        setupAppOpenAd()
                        if(BuildConfig.DEBUG){
                            setToast("close by user")
                        }
                        MyAdsListener.setBannerHome(true)
                    }

                    override fun onAdFailedToShowFullScreenContent(p0: AdError) {
                        Log.d("AppOpenAd", "Failed to show ad: ${p0.message}")
                    }

                    override fun onAdShowedFullScreenContent() {
                        MyAdsListener.setBannerHome(false)
                        Log.d("AppOpenAd", "Ad showed")
                    }
                }
                appOpenAd?.show(this)
            }catch (e : Exception){
                if(BuildConfig.DEBUG){
                    setToast("open ads f" + e.message.toString())
                }
            }
        }else{
            if(BuildConfig.DEBUG){
                setToast("open ad null")
            }
        }
    }



    /*fun loadInterstitialIfNeeded() {
        if (mInterstitialAd != null) return

        InterstitialAd.load(
            this,
            admobSDKBuilder?.interstitialId.toString(),
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {

                override fun onAdLoaded(ad: InterstitialAd) {
                    retryJob?.cancel()
                    retryCount = 0
                    mInterstitialAd = ad
                    setToastADS("load interstitial success")
                    Log.d("ADS", "Interstitial loaded")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e("ADS", "Load failed: ${error.message}")
                    setToastADS("load interstitial failed")
                    scheduleRetry()
                }
            }
        )
    }*/


    fun loadInterstitialIfNeeded(isPremium: Boolean) {
        if(!isPremium){
            val now = System.currentTimeMillis()

            // 1️⃣ Jangan load kalau interstitial masih ada
            if (mInterstitialAd != null) return

            // 2️⃣ Kalau SUDAH pernah show → cek interval
            if (lastShowTime > 0 && now - lastShowTime < LOAD_INTERVAL) {
                Log.d("ADS", "Skip load: waiting interval")
                setToastADS("already init wait bro")
                return
            }

            // 3️⃣ Jangan load kalau activity tidak aktif
            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                return
            }

            InterstitialAd.load(
                this,
                admobSDKBuilder?.interstitialId ?: return,
                AdRequest.Builder().build(),
                object : InterstitialAdLoadCallback() {

                    override fun onAdLoaded(ad: InterstitialAd) {
                        retryJob?.cancel()
                        retryCount = 0
                        mInterstitialAd = ad
                        Log.d("ADS", "Interstitial loaded")
                        setToastADS("load interstitial success")
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Log.e("ADS", "Load failed: ${error.message}")
                        setToastADS("load interstitial failed")
                        scheduleRetry(isPremium)
                    }
                }
            )
        }
    }




    private var retryCountReward = 0

    fun loadRewardedAd(isPremium: Boolean) {
        if (isPremium) return

        val adId = admobSDKBuilder?.rewardId.orEmpty()
        if (adId.isEmpty()) return

        val adRequest = AdRequest.Builder().build()

        // Gunakan applicationContext untuk loading iklan
        RewardedAd.load(applicationContext, adId, adRequest, object : RewardedAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                rewardedAd = null
                if (retryCountReward < 3) {
                    retryCountReward++
                    Handler(Looper.getMainLooper()).postDelayed({
                        loadRewardedAd(isPremium)
                    }, 5000)
                }
            }

            override fun onAdLoaded(ad: RewardedAd) {
                rewardedAd = ad
                retryCountReward = 0
            }
        })
    }


    private fun scheduleRetry(isPremium: Boolean) {
        if(!isPremium){
            if (retryCount >= MAX_RETRY) return

            retryJob?.cancel()
            retryCount++

            retryJob = lifecycleScope.launch {
                delay(60_000L)

                if (mInterstitialAd == null &&
                    lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                ) {
                    loadInterstitialIfNeeded(isPremium)
                }
            }
        }
    }

    fun showInterstitialIfAllowed(isPremium : Boolean,onFinished: () -> Unit) {

        if(!isPremium){
            val ad = mInterstitialAd
            val now = System.currentTimeMillis()

            // 1️⃣ Guard awal
            if (
                ad == null ||
                now - lastShowTime < SHOW_INTERVAL ||
                lifecycle.currentState != Lifecycle.State.RESUMED
            ) {
                onFinished()
                return
            }

            // 2️⃣ Anti double show
            mInterstitialAd = null
            lastShowTime = now

            // 3️⃣ Gunakan WeakReference (ANTI LEAK)
            val activityRef = WeakReference(this)

            ad.fullScreenContentCallback = object : FullScreenContentCallback() {

                override fun onAdDismissedFullScreenContent() {
                    releaseAd(ad)
                    activityRef.get()?.safePost {
                        onFinished()
                    }
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    releaseAd(ad)
                    activityRef.get()?.safePost {
                        onFinished()
                    }
                }
            }

            ad.show(this)
        }else{
            onFinished()
            return
        }
    }

    private fun Activity.safePost(block: () -> Unit) {
        if (!isFinishing && !isDestroyed) {
            window?.decorView?.post { block() }
        }
    }



    private fun releaseAd(ad: InterstitialAd) {
        ad.fullScreenContentCallback = null
        scheduleNextLoad()
    }


    private fun cleanupAfterShow() {
        try {
            mInterstitialAd?.fullScreenContentCallback = null
        } catch (_: Exception) {}

        mInterstitialAd = null
    }

    private fun scheduleNextLoad() {
        reloadJob?.cancel()

        reloadJob = lifecycleScope.launch {
            delay(NEXT_LOAD_DELAY)

            // Jangan load kalau activity tidak aktif
            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return@launch

            setToastADS("reload interstitial after 90 second")
            loadInterstitialIfNeeded(false)
        }
    }



    override fun onStop() {
        super.onStop()

        // Layar mati / app background → BUANG INTERSTITIAL
        mInterstitialAd?.fullScreenContentCallback = null
        mInterstitialAd = null
    }


   /* fun showRewardedAd(isPremium : Boolean,onComplete: () -> Unit) {
        if(!isPremium){
            if (rewardedAd != null) {
                rewardedAd?.show(this) { _ ->
                    onComplete.invoke()
                    loadRewardedAd(isPremium)
                }
            } else {
                // Kasih Toast atau Dialog loading
                setToast(getString(R.string.ads_prepared_please_wait))
                loadRewardedAd(isPremium)
            }
        }else{
            onComplete()
            return
        }
    }*/


    fun showRewardedAd(isPremium: Boolean, onComplete: () -> Unit) {
        if (isPremium) {
            onComplete()
            return
        }

        val ad = rewardedAd
        if (ad != null) {
            // 1. Pasang Callback untuk deteksi iklan ditutup
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    // PENTING: Null-kan callback agar tidak menahan referensi Fragment
                    ad.fullScreenContentCallback = null
                    rewardedAd = null

                    // Jalankan aksi (Unlock music/list)
                    onComplete()

                    // Load ulang untuk selanjutnya
                    loadRewardedAd(isPremium)
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    ad.fullScreenContentCallback = null
                    rewardedAd = null
                    onComplete() // Tetap jalankan atau kasih fail handler
                }
            }

            // 2. Tampilkan Iklan
            ad.show(this) { rewardItem ->
                // User sudah menonton sampai habis (reward diberikan via onAdDismissed)
                Log.d("ADS", "User earned reward: ${rewardItem.amount}")
            }

        } else {
            setToast(getString(R.string.ads_prepared_please_wait))
            loadRewardedAd(isPremium)
        }
    }

    private fun isWebViewAvailable(): Boolean {
        val packageManager = packageManager
        return try {
            packageManager.getPackageInfo("com.google.android.webview", 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun onUserFinishPlaying0(duration: Long) {
        // Kalau user main lama, pastikan iklan fresh
        if (duration > 30_000) {
            setupRewardInterstitial()
        }
    }

    fun setupRewardInterstitial(){
        try {
            RewardedInterstitialAd.load(this, DataSession(this).getRewardInterstitialId(),
                AdRequest.Builder().build(), object : RewardedInterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedInterstitialAd) {
                        //Log.d(TAG, "Ad was loaded.")
                        rewardedInterstitialAd = ad
                        isLoadInterstitialReward = true
                        rewardedInterstitialAd?.fullScreenContentCallback = object: FullScreenContentCallback() {
                            override fun onAdClicked() {
                                // Called when a click is recorded for an ad.
                                Log.d("yametere", "Ad was clicked.")
                            }

                            override fun onAdDismissedFullScreenContent() {
                                // Called when ad is dismissed.
                                // Set the ad reference to null so you don't show the ad a second time.
                                Log.d("yametere", "Ad dismissed fullscreen content.")
                                rewardedInterstitialAd = null
                            }

                            override fun onAdFailedToShowFullScreenContent(p0: AdError) {
                                // Called when ad fails to show.
                                Log.d("yametere", "Ad failed to show fullscreen content.")
                                rewardedInterstitialAd = null
                            }

                            override fun onAdImpression() {
                                // Called when an impression is recorded for an ad.
                                Log.d("yametere", "Ad recorded an impression.")
                            }

                            override fun onAdShowedFullScreenContent() {
                                // Called when ad is shown.
                                Log.d("yametere","Ad showed fullscreen content.")
                            }
                        }
                    }

                    override fun onAdFailedToLoad(adError: LoadAdError) {
                        // Log.d(TAG, adError?.toString())
                        Log.d("yameterex",adError.message.toString())
                        rewardedInterstitialAd = null
                    }
                })

        }catch (e : Exception){
            setLog(e.message.toString())
        }
    }




    protected open fun getFirebaseToken(): String? {
        val tokens = AtomicReference("")
        FirebaseMessaging.getInstance().token
            .addOnCompleteListener { task: Task<String> ->
                if (!task.isSuccessful) {
                    Log.w("response", "Fetching FCM registration token failed", task.exception)
                    //getFirebaseToken()
                    return@addOnCompleteListener
                }

                // Get new FCM registration token
                val tokenFirebase = task.result
                tokens.set(tokenFirebase)
                Log.d("tokenFirebase",tokenFirebase.toString())

            }
        return tokens.get()
    }



    /* fun showInterstitial(){
         try {
             Log.d("showInters","execute")
             if(isLoad){
                 Log.d("showIntersAdmob","true")
                 mInterstitialAd?.show(this)
             }else{
                 if(showFANInterstitial){
                     Log.d("showIntersFA","true")
                     interstitialFANAd?.show()
                 }
             }
         }catch (e : Exception){
             Log.d("showInters","false")
             setLog(e.message.toString())
         }
     }
 */


    protected fun setToastTic(code : Int,message : String){
        try {
            Toastic.toastic(
                context = this,
                message = "$message.",
                duration = Toastic.LENGTH_SHORT,
                type = code,
                isIconAnimated = true
            ).show()
        }catch (e : Exception){
            setLog(e.message.toString())
        }

    }


    protected fun setToast(message : String){
        Toast.makeText(this, "$message.",Toast.LENGTH_SHORT).show()
    }


    fun setLog(message: String){
        if(BuildConfig.DEBUG){
            Log.d("response", "$message - ")
        }
    }

    fun setLog(name : String, message: String){
        if(BuildConfig.DEBUG){
            Log.d(name, "$message - ")
        }
    }

    fun openSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = Uri.fromParts("package", packageName.toString(), null)
        startActivity(intent)
    }


    open fun getActivity(): BaseActivityWidget? {
        return this
    }



    fun isDarkTheme(): Boolean {
        return resources?.configuration?.uiMode!! and
                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }



    fun initAnim(ivStop : ImageView? =null) {
        try {
            mPanAnim = AnimationUtils.loadAnimation(this, R.anim.rotate)
            val mPanLin = LinearInterpolator()
            mPanAnim?.interpolator = mPanLin
            mPanAnim?.startTime = 0
            mPanAnim?.let { anim ->
                anim.interpolator = mPanLin
                anim.setAnimationListener(object : Animation.AnimationListener {
                    override fun onAnimationStart(animation: Animation) {}

                    override fun onAnimationEnd(animation: Animation) {
                        ivStop?.visibility = View.GONE
                    }

                    override fun onAnimationRepeat(animation: Animation) {}
                })
            } ?: run {
                println("Error: mPanAnim is null")
            }
        } catch (e: Exception) {
            setLog(e.message.toString())
        }
    }


    fun isInternetConnected(): Boolean {
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val networkCapabilities = connectivityManager.activeNetwork ?: return false
            val actNw = connectivityManager.getNetworkCapabilities(networkCapabilities) ?: return false

            return when {
                actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                // for other device how are able to connect with Ethernet
                actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                // for check internet over Bluetooth
                actNw.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> true
                else -> false
            }
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo ?: return false
            @Suppress("DEPRECATION")
            return networkInfo.isConnected
        }
    }



    @Suppress("DEPRECATION")
    fun isInternetTrulyAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false

            // Internet benar-benar aktif & tervalidasi
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

        } else {
            // Untuk SDK < 23 (Lollipop, KitKat, dll)
            val networkInfo = cm.activeNetworkInfo
            networkInfo != null && networkInfo.isConnected
        }
    }


}
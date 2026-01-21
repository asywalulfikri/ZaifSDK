package sound.recorder.widget.base

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.IntentSender
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
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback
import com.google.android.gms.tasks.Task
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.ktx.isFlexibleUpdateAllowed
import com.google.android.play.core.ktx.isImmediateUpdateAllowed
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.google.firebase.messaging.FirebaseMessaging
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import sound.recorder.widget.BuildConfig
import sound.recorder.widget.R
import sound.recorder.widget.RecordingSDK
import sound.recorder.widget.ads.AdConfigProvider
import sound.recorder.widget.ads.GoogleMobileAdsConsentManager
import sound.recorder.widget.builder.AdmobSDKBuilder
import sound.recorder.widget.listener.MyAdsListener
import sound.recorder.widget.notes.Note
import sound.recorder.widget.util.DataSession
import sound.recorder.widget.util.Toastic
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.seconds


open class BaseActivityWidget : AppCompatActivity() {

    private var mInterstitialAd: InterstitialAd? = null
    private var isLoad = false
    private var rewardedAd: RewardedAd? = null
    private var isLoadReward = false
    private var isLoadInterstitialReward = false
    private var rewardedInterstitialAd: RewardedInterstitialAd? = null

    private var adView: AdView? = null
    private var adView2: AdView? = null

    private lateinit var googleMobileAdsConsentManager: GoogleMobileAdsConsentManager
    private lateinit var consentInformation: ConsentInformation
    private var TAG = "GDPR_App"

    private var isPrivacyOptionsRequired: Boolean = false


    private lateinit var appUpdateManager: AppUpdateManager       // in app update
    private val updateType = AppUpdateType.FLEXIBLE

    var sharedPreferences: SharedPreferences? = null

    private var appOpenAd: AppOpenAd? = null
    var mPanAnim: Animation? = null

  //  private var unityBannerView: BannerView? = null

    private var bannerRetryCount = 0
    private val maxBannerRetry = 3

    private val retryHandler = Handler(Looper.getMainLooper())

    private var interstitialRetryCount = 0
    private val MAX_RETRY_COUNT = 1

    private var isBannerLoaded = false

    private var isLoading = false


    @Volatile
    private var isBannerLoading = false
    private val adLoadTimeout = 10_000L // 10 detik timeout
    private var loadTimeoutRunnable: Runnable? = null


    //banner home
    private var loadBannerRunnable: Runnable? = null

    private var bannerContainerRef: WeakReference<FrameLayout>? = null



    companion object {
        // Tempat untuk "menitipkan" implementasi kontrak dari aplikasi
        var adConfigProvider: AdConfigProvider? = null
    }

    val admobSDKBuilder: AdmobSDKBuilder?
        get() = adConfigProvider?.getAdmobBuilder()

   /* val unitySDKBuilder: UnitySDKBuilder?
        get() = adConfigProvider?.getUnityBuilder()*/

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    open fun getAdBannerContainer(): FrameLayout? {
        return null // Nilai default
    }

    /*  fun executeBuilder(){
          admobSDKBuilder = AdmobSDKBuilder.builder(this).loadFromSharedPreferences()
          fanSDKBuilder   = FanSDKBuilder.builder(this).loadFromSharedPreferences()
          unitySDKBuilder = UnitySDKBuilder.builder(this).loadFromSharedPreferences()
      }*/





    /*fun setupUnityAds(unityId : String){
        var testMode = true

        testMode = BuildConfig.DEBUG
        // UnityAds.initialize(this, unitySDKBuilder?.unityId, unitySDKBuilder?.testMode == true, this)
        UnityAds.initialize(this, unityId, testMode, object : IUnityAdsInitializationListener {
            override fun onInitializationComplete() {
                Log.d("UnityAds", "Initialization Complete")
            }

            override fun onInitializationFailed(
                error: UnityAds.UnityAdsInitializationError?,
                message: String?
            ) {
                Log.e("UnityAds", "Initialization Failed: $message")
            }
        })

    }*/



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


    fun checkUpdate() {
        appUpdateManager = AppUpdateManagerFactory.create(this)

        lifecycleScope.launch {
            try {
                val info = withContext(Dispatchers.IO) {
                    appUpdateManager.appUpdateInfo.await()
                }

                val isUpdateAvailable =
                    info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE

                val isUpdateAllowed = when (updateType) {
                    AppUpdateType.FLEXIBLE -> info.isFlexibleUpdateAllowed
                    AppUpdateType.IMMEDIATE -> info.isImmediateUpdateAllowed
                    else -> false
                }

                if (isUpdateAvailable && isUpdateAllowed) {
                    try {
                        appUpdateManager.startUpdateFlowForResult(
                            info,
                            updateType,
                            this@BaseActivityWidget,
                            123
                        )
                    } catch (e: IntentSender.SendIntentException) {
                        Log.e("YourActivity", "Error starting update flow: ${e.message}")
                        // Handle the exception, log, or display an error message
                    }
                }
            } catch (e: Exception) {
                Log.e("YourActivity", "Error checking update: ${e.message}")
                // Handle the exception, log, or display an error message
            }
        }
    }


    fun onDestroyUpdate() {
        try {
            if (updateType == AppUpdateType.FLEXIBLE) {
                appUpdateManager.unregisterListener(installStateUpdatedListener)
            }
        } catch (e: Exception) {
            Log.d("not", "support")
        }
    }



    override fun onDestroy() {

        cleanBannerHome()

        ///////////////////

        cleanupBannerAd()
        bannerHandler?.removeCallbacksAndMessages(null)
        bannerHandler = null

        isBannerLoaded = false
        isLoading = false
        mInterstitialAd = null
        super.onDestroy()
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
        if (adView != null) {
            adView?.resume()
        }
        if(adView2!=null){
            adView2?.resume()
        }
    }





    private val installStateUpdatedListener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) {
            try {
                setToastTic(Toastic.INFO, getString(R.string.download_success))
                lifecycleScope.launch {
                    delay(5.seconds)
                    appUpdateManager.completeUpdate()
                }
            } catch (e: Exception) {
                Log.d("not", "support")
            }
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

    protected fun loadBannerGame(frameLayout: FrameLayout?, modePortrait : Boolean? = false) {
        try {
            if (isAdMobAvailable()) {
                if(modePortrait==true){
                    executeBannerPortrait(frameLayout)
                }else{
                    executeBanner(frameLayout)
                }
            }
        }catch (e : Exception){
            //
        }
    }

    fun setToastADS(message : String){
        if (!isFinishing && !isDestroyed) {
            if(admobSDKBuilder?.showToast==true){
                Toast.makeText(this,message, Toast.LENGTH_LONG).show()
            }
        }

    }


    /*private fun executeBanner(adViewContainer: FrameLayout?) {
        if (bannerRetryCount >= maxBannerRetry) {
            runOnUiThread {
                setToastADS("Limit reached: $bannerRetryCount")
            }
            return
        }

        // Hanya buat AdView baru jika belum ada
        if (adView2 == null) {
            adView2 = AdView(this).apply {
                adUnitId = admobSDKBuilder?.bannerId.orEmpty()
                setAdSize(getAdSize2())
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            }
        }

        adView2?.adListener = object : AdListener() {
            override fun onAdLoaded() {

                if (isFinishing || isDestroyed) return

                runOnUiThread {
                    try {
                        bannerRetryCount = 0
                        // Hapus parent lama jika ada
                        (adView2?.parent as? ViewGroup)?.removeView(adView2)
                        adViewContainer?.removeAllViews()
                        adViewContainer?.addView(adView2)
                        setToastADS("Banner loaded: ${adView2?.adUnitId}")
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                runOnUiThread {
                    setToastADS("Banner failed: ${error.message} ${adView2?.adUnitId}")
                }
                bannerRetryCount++
                // Retry dengan delay, misal 3 detik
                adViewContainer?.postDelayed({ executeBanner(adViewContainer) }, 3000)
            }
        }

        // Load Ad
        val adRequest = AdRequest.Builder().build()
        adView2?.loadAd(adRequest)
    }*/


    private var bannerHandler: Handler? = null
    private var bannerRetryRunnable: Runnable? = null
    private var bannerTimeoutRunnable: Runnable? = null

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

    fun setupInterstitialppp() {

        if(isWebViewAvailable()){

            CoroutineScope(Dispatchers.Main).launch {

                if (Build.VERSION.SDK_INT == Build.VERSION_CODES.O||Build.VERSION.SDK_INT == Build.VERSION_CODES.O_MR1) {

                }else{

                    if(isAdMobAvailable()){
                        try {
                            val adRequest = AdRequest.Builder().build()
                            InterstitialAd.load(this@BaseActivityWidget, admobSDKBuilder?.interstitialId.toString(), adRequest,
                                object : InterstitialAdLoadCallback() {
                                    override fun onAdLoaded(interstitialAd: InterstitialAd) {
                                        mInterstitialAd = interstitialAd
                                        isLoad = true
                                        Log.d("AdMob", "Interstitial loaded successfully "+interstitialAd.adUnitId)
                                        // Set the FullScreenContentCallback
                                        mInterstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                                            override fun onAdDismissedFullScreenContent() {
                                                // Handle the ad dismissed event
                                                setLog("AdMob Inters Ad Dismissed")
                                                if (BuildConfig.DEBUG) {
                                                    setToast("ads closed")
                                                }
                                                // Load a new interstitial ad
                                                mInterstitialAd = null
                                                // resetInsetsAfterAd()
                                                setupInterstitial()
                                            }

                                            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                                                // Handle the ad failed to show event
                                                setLog("AdMob Inters Ad Failed to Show: ${adError.message}")
                                                if (BuildConfig.DEBUG) {
                                                    setToast(adError.message)
                                                }

                                            }

                                            override fun onAdShowedFullScreenContent() {
                                                // Handle the ad showed event
                                                setLog("AdMob Inters Ad Showed")
                                                //ensureInsetsForAd()
                                                mInterstitialAd = null // Reset the interstitial ad
                                            }
                                        }
                                    }

                                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                                        mInterstitialAd = null
                                        isLoad = false
                                        Log.d("Admob","Interstitial Loaded Failed id = ${admobSDKBuilder?.interstitialId.toString()} ---> ${loadAdError.message}")

                                    }
                                })
                        } catch (e: Exception) {
                            setLog("asywalul inters :${e.message}")
                        }
                    }
                }

            }
        }

    }




    // Tambahkan variabel counter di level class (luar fungsi)

   /* fun setupInterstitial() {
        // 1. Validasi awal
        if (!isWebViewAvailable() || !isAdMobAvailable()) return

        // Gunakan lifecycleScope jika di Activity/Fragment agar tidak memicu memory leak
        CoroutineScope(Dispatchers.Main).launch {

            // Skip untuk OS tertentu jika memang diperlukan seperti logika awalmu
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.O || Build.VERSION.SDK_INT == Build.VERSION_CODES.O_MR1) {
                return@launch
            }

            try {
                val adRequest = AdRequest.Builder().build()
                val adUnitId = admobSDKBuilder?.interstitialId.toString()

                InterstitialAd.load(this@BaseActivityWidget, adUnitId, adRequest,
                    object : InterstitialAdLoadCallback() {
                        override fun onAdLoaded(interstitialAd: InterstitialAd) {
                            // Reset counter saat berhasil load
                            interstitialRetryCount = 0

                            mInterstitialAd = interstitialAd
                            isLoad = true
                            Log.d("AdMob", "Interstitial loaded successfully: ${interstitialAd.adUnitId}")

                            mInterstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                                override fun onAdDismissedFullScreenContent() {
                                    mInterstitialAd = null
                                    isLoad = false // Reset status load
                                    setupInterstitial() // Load lagi untuk persiapan berikutnya
                                }

                                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                                    mInterstitialAd = null
                                    isLoad = false
                                }

                                override fun onAdShowedFullScreenContent() {
                                    mInterstitialAd = null
                                    isLoad = false
                                }
                            }
                        }

                        override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                            mInterstitialAd = null
                            isLoad = false
                            Log.e("AdMob", "Failed to load: ${loadAdError.message}")

                            // 2. LOGIKA RETRY
                            if (interstitialRetryCount < MAX_RETRY_COUNT) {
                                interstitialRetryCount++

                                // Gunakan delay eksponensial (makin lama makin bertambah jedanya)
                                val retryDelay = interstitialRetryCount * 5000L // 5 detik, 10 detik

                                Log.d("AdMob", "Retrying load ($interstitialRetryCount/$MAX_RETRY_COUNT) in ${retryDelay/1000}s...")

                                Handler(Looper.getMainLooper()).postDelayed({
                                    setupInterstitial()
                                }, retryDelay)
                            } else {
                                Log.d("AdMob", "Max retry reached. Stop loading.")
                            }
                        }
                    })
            } catch (e: Exception) {
                Log.e("AdMob", "Error in setupInterstitial: ${e.message}")
            }
        }
    }*/


    fun setupInterstitial() {
        // 1. Validasi Dasar
        if (!isWebViewAvailable() || !isAdMobAvailable()) return

        // Gunakan lifecycleScope agar coroutine otomatis batal saat Activity hancur
        lifecycleScope.launch {
            try {
                val adRequest = AdRequest.Builder().build()
                val adUnitId = admobSDKBuilder?.interstitialId.toString()

                // Membungkus load di Main Thread secara aman
                InterstitialAd.load(this@BaseActivityWidget, adUnitId, adRequest,
                    object : InterstitialAdLoadCallback() {
                        override fun onAdLoaded(interstitialAd: InterstitialAd) {
                            interstitialRetryCount = 0
                            mInterstitialAd = interstitialAd
                            isLoad = true

                            mInterstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                                override fun onAdDismissedFullScreenContent() {
                                    mInterstitialAd = null
                                    isLoad = false
                                    // Pre-load iklan berikutnya setelah ditutup
                                    setupInterstitial()
                                }

                                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                                    // Menangkap error saat gagal tampil (sering terjadi di OS lama)
                                    mInterstitialAd = null
                                    isLoad = false
                                    Log.e("AdMob", "Gagal tampil: ${adError.message}")
                                }

                                override fun onAdShowedFullScreenContent() {
                                    mInterstitialAd = null
                                    isLoad = false
                                }
                            }
                        }

                        override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                            mInterstitialAd = null
                            isLoad = false

                            // Logika Retry dengan Exponential Backoff
                            if (interstitialRetryCount < MAX_RETRY_COUNT) {
                                interstitialRetryCount++
                                val retryDelay = interstitialRetryCount * 5000L

                                // Delay non-blocking yang terikat pada lifecycle
                                lifecycleScope.launch {
                                    delay(retryDelay)
                                    if (!isFinishing && !isDestroyed) {
                                        setupInterstitial()
                                    }
                                }
                            }
                        }
                    })
            } catch (e: Exception) {
                // Ini adalah "jaring pengaman" terakhir untuk mencegah crash di Android Oreo/Lollipop
                Log.e("AdMob", "Fatal Error di setupInterstitial: ${e.message}")
            }
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

    fun showInterstitial() {
        try {
            Log.d("showInters", "execute")
            if (isLoad) {
                Log.d("showIntersAdmob", "true")
                mInterstitialAd?.let { ad ->
                    window.decorView.rootView.post {
                        try {
                            ad.show(this@BaseActivityWidget)
                        } catch (e: Exception) {
                            setLog("Error showing AdMob Interstitial: ${e.message}")
                        }
                    }
                }
            } else {

            }
        } catch (e: Exception) {
            Log.d("showInters", "false")
            setLog(e.message.toString())
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

    fun showRewardInterstitial(){
        try {
            if(isLoadInterstitialReward){
                Log.d("yametere", "show")
                rewardedInterstitialAd?.let { ad ->
                    ad.show(this) { rewardItem ->
                        // Handle the reward.
                        val rewardAmount = rewardItem.amount
                        val rewardType = rewardItem.type
                        Log.d("yametere", "User earned the reward.$rewardAmount--$rewardType")
                    }
                } ?: run {
                    Log.d("yametere", "The rewarded ad wasn't ready yet.")
                    showInterstitial()
                }
            }
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


    /*fun setupBannerUnity(adContainer: FrameLayout) {
        if(unitySDKBuilder?.enable==true){
            try {
                unityBannerView = BannerView(this, "Banner_Android", UnityBannerSize(320, 50))
                unityBannerView?.load()
                unityBannerView?.gravity = Gravity.CENTER
                adContainer.addView(unityBannerView)

                loadInterstitialUnityAds()
            }catch (e : Exception){

            }
        }
    }*/




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
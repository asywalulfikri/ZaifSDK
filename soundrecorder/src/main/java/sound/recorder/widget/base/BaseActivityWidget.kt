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
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.facebook.ads.Ad
import com.facebook.ads.InterstitialAdListener
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
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.IUnityAdsLoadListener
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.ads.UnityAds
import com.unity3d.ads.UnityAds.UnityAdsLoadError
import com.unity3d.ads.UnityAds.UnityAdsShowCompletionState
import com.unity3d.ads.UnityAds.UnityAdsShowError
import com.unity3d.services.banners.BannerView
import com.unity3d.services.banners.UnityBannerSize
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
import sound.recorder.widget.builder.FanSDKBuilder
import sound.recorder.widget.builder.UnitySDKBuilder
import sound.recorder.widget.listener.MyAdsListener
import sound.recorder.widget.notes.Note
import sound.recorder.widget.util.DataSession
import sound.recorder.widget.util.Toastic
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.seconds


open class BaseActivityWidget : AppCompatActivity() {

    private var mInterstitialAd: InterstitialAd? = null
    private var isLoad = false
    private var rewardedAd: RewardedAd? = null
    private var isLoadReward = false
    private var interstitialFANAd: com.facebook.ads.InterstitialAd? = null
    private var isLoadInterstitialReward = false
    private var rewardedInterstitialAd: RewardedInterstitialAd? = null

    private var adView: AdView? = null
    private var adView2: AdView? = null

    private lateinit var googleMobileAdsConsentManager: GoogleMobileAdsConsentManager
    private lateinit var consentInformation: ConsentInformation
    private var TAG = "GDPR_App"

    private var isPrivacyOptionsRequired: Boolean = false
    private var showFANInterstitial = false

    private lateinit var appUpdateManager: AppUpdateManager       // in app update
    private val updateType = AppUpdateType.FLEXIBLE

    var sharedPreferences: SharedPreferences? = null

    private var appOpenAd: AppOpenAd? = null
  /*  var admobSDKBuilder: AdmobSDKBuilder? = null
    var fanSDKBuilder: FanSDKBuilder? = null
    var unitySDKBuilder : UnitySDKBuilder? =null*/
    var mPanAnim: Animation? = null

    private var unityBannerView: BannerView? = null
    private var fanAdView: com.facebook.ads.AdView? = null

    private var bannerRetryCount = 0
    private val maxBannerRetry = 3

    private val retryHandler = Handler(Looper.getMainLooper())
    private val retryRunnable = Runnable { loadBannerAds() }


    private val displayMetrics by lazy { resources.displayMetrics }

    companion object {
        // Tempat untuk "menitipkan" implementasi kontrak dari aplikasi
        var adConfigProvider: AdConfigProvider? = null
    }

    val admobSDKBuilder: AdmobSDKBuilder?
        get() = adConfigProvider?.getAdmobBuilder()

    val fanSDKBuilder: FanSDKBuilder?
        get() = adConfigProvider?.getFanBuilder()

    val unitySDKBuilder: UnitySDKBuilder?
        get() = adConfigProvider?.getUnityBuilder()

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





    fun setupUnityAds(unityId : String){
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

    fun setupBannerAdmob(view : FrameLayout){
        try {
            val bannerId = admobSDKBuilder?.bannerHomeId.toString()
            setLog("ADS_Admob", "home banner id = $bannerId")
            adView = AdView(this)
            adView?.setAdSize(AdSize.BANNER)
            adView?.adUnitId = bannerId
            view.addView(adView)

            val adRequest = AdRequest.Builder().build()
            adView?.loadAd(adRequest)
        }catch (e : Exception){
            print(e.message)
        }
    }


    @SuppressLint("WrongConstant")
    fun setupHideStatusBar(rootView: View, hide: Boolean) {
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
                ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, windowInsets ->
                    val insets = windowInsets.getInsets(WindowInsets.Type.systemBars())
                    val layoutParams = view.layoutParams as ViewGroup.MarginLayoutParams
                    layoutParams.rightMargin = insets.right
                    view.layoutParams = layoutParams
                    windowInsets
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
                rootView.setOnApplyWindowInsetsListener { view, windowInsets ->
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


  /*  fun setStatusBarColor(color: Int) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
                window.statusBarColor = ContextCompat.getColor(this, color)
            }
        } catch (e: Exception) {
            setLog("not support")
        }
    }

    fun setBottomStatusColor(color: Int) {
        try {
            window?.navigationBarColor = ContextCompat.getColor(this, color)
        } catch (e: Exception) {
            setLog("not support")
        }

    }*/

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
        super.onDestroy()
        mInterstitialAd = null
        retryHandler.removeCallbacks(retryRunnable) // <-- Kuncinya di sini!
        // Menunda proses destroy untuk AdMob Banners
        adView?.postDelayed({
            try {
                adView?.destroy()
            } catch (e: Exception) {
                // Abaikan error jika view sudah tidak valid
            }
        }, 100)

        adView2?.postDelayed({
            try {
                adView?.destroy()
            } catch (e: Exception) {
                // Abaikan error jika view sudah tidak valid
            }
        }, 100)


        // Menunda proses destroy untuk Unity Banner
        unityBannerView?.let { banner ->
            (banner.parent as? ViewGroup)?.removeView(banner)
            banner.postDelayed({
                try {
                    banner.destroy()
                } catch (e: Exception) {
                    // Abaikan error jika view sudah tidak valid
                }
            }, 100) // Tunda eksekusi selama 100 milidetik
            unityBannerView = null
        }


        // Menunda proses destroy untuk Facebook FAN Banner
        fanAdView?.postDelayed({
            try {
                fanAdView?.destroy()
            } catch (e: Exception) {
                // Abaikan error jika view sudah tidak valid
            }
        }, 100)
    }

    override fun onPause() {
        super.onPause()
        if (adView != null) {
            adView?.pause()
            adView2?.pause()
        }
    }

    override fun onResume() {
        super.onResume()
        if (adView != null) {
            adView?.resume()
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
        val btnSend = dialog.findViewById<View>(R.id.btnSend) as Button
        val btnCancel = dialog.findViewById<View>(R.id.btnCancel) as Button


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

   /* fun setupBanner(adViewContainer: FrameLayout) {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.O || Build.VERSION.SDK_INT == Build.VERSION_CODES.O_MR1) {
            if (isWebViewAvailable()) {
                setupBannerFacebook(adViewContainer)
            }
        } else {
            if (isWebViewAvailable()) {
                if (isAdMobAvailable()) {
                    executeBanner(adViewContainer)
                }
            }
        }
    }*/


    protected fun loadBannerAds() {
        try {
            val container = getAdBannerContainer()
            if (container == null) {
                // Halaman ini tidak ingin menampilkan banner
                return
            }

            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.O || Build.VERSION.SDK_INT == Build.VERSION_CODES.O_MR1) {
                if (isWebViewAvailable()) {
                    setupBannerFacebook(container)
                }
            } else {
                if (isAdMobAvailable()) {
                    executeBanner(container)
                }
            }
        }catch (e : Exception){

        }
    }

    private fun executeBanner(adViewContainer: FrameLayout) {

        if (bannerRetryCount >= maxBannerRetry) {
            Log.e("ADS_Waterfall", "❌ Max retry reached. Stop trying.")
            return
        }

        try {
            fanAdView?.destroy()
            fanAdView= null

            // Buat instance AdMob AdView
            adView2= AdView(this).apply {
                adUnitId = admobSDKBuilder?.bannerId.orEmpty()
                setAdSize(getSize()) // Pastikan fungsi ini mengembalikan AdSize yang valid
            }

            // Siapkan listener untuk menangani hasil pemuatan
            adView2?.adListener = object : AdListener() {
                override fun onAdLoaded() {
                    bannerRetryCount = 0
                    Log.d("ADS_Waterfall", "✅ AdMob banner SUKSES dimuat.")
                   // setToast("AdMob Sukses "+adView2?.adUnitId)

                    // Bersihkan container dan tampilkan banner AdMob
                    //adViewContainer.removeAllViews()
                    adViewContainer.addView(adView2)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e("ADS_Waterfall", "❌ AdMob banner GAGAL: ${error.message}. Mencoba fallback ke Facebook...")
                   // setToast("AdMob Gagal"+ adView2?.adUnitId +"coba FAN")
                    bannerRetryCount++
                    // 3. Jika AdMob gagal, panggil fungsi untuk memuat banner Facebook
                    loadFanBanner(adViewContainer)
                }
            }

            // Buat request dan mulai pemuatan
            Log.d("ADS_Waterfall", "Mencoba memuat AdMob banner...")
            val adRequest = AdRequest.Builder().build()
            adView2?.loadAd(adRequest)
        }catch ( e : Exception){

        }
        // Hancurkan banner FAN sebelumnya jika ada, untuk menghindari memory leak
    }


    private fun loadFanBanner(adViewContainer: FrameLayout) {

        try {
            // Cek jika FAN diaktifkan
            if (fanSDKBuilder?.enable != true) {
                Log.d("ADS_Waterfall", "Facebook FAN tidak diaktifkan. Berhenti.")
                return
            }

            // Hancurkan banner AdMob sebelumnya untuk membebaskan memori
            adView2?.destroy()
            adView2 = null


            val fanBannerId = fanSDKBuilder?.bannerId.orEmpty()
            fanAdView = com.facebook.ads.AdView(
                this,
                fanBannerId,
                com.facebook.ads.AdSize.BANNER_HEIGHT_50
            )

            val adListener = object : com.facebook.ads.AdListener {
                override fun onAdLoaded(ad: Ad) {
                    Log.d("ADS_Waterfall", "✅ Facebook banner SUKSES dimuat sebagai fallback.")
                   // setToast("FAN Sukses"+fanAdView?.id.toString())
                    bannerRetryCount = 0
                    // Bersihkan container dan tampilkan banner Facebook
                    //adViewContainer.removeAllViews()
                    adViewContainer.addView(fanAdView)
                }

                override fun onError(ad: Ad, adError: com.facebook.ads.AdError) {
                    Log.e("ADS_Waterfall", "❌ Facebook banner juga GAGAL: ${adError.errorMessage}")
                    setToast("FAN Gagal"+adError.errorMessage)
                    bannerRetryCount++
                    // Jadwalkan untuk mencoba lagi dari awal (memanggil loadBannerAds)
                    retryHandler.postDelayed(retryRunnable, 10000)
                    // Retry AdMob after delay
                }

                override fun onAdClicked(ad: Ad) {}
                override fun onLoggingImpression(ad: Ad) {}
            }

            Log.d("ADS_Waterfall", "Mencoba memuat Facebook banner...")
            fanAdView?.loadAd(
                fanAdView?.buildLoadAdConfig()
                    ?.withAdListener(adListener)
                    ?.build()
            )
        }catch (e : Exception){
          //  setToast("veve" +e.message)

        }
    }
    private fun executeBannerAAAA(adViewContainer: FrameLayout) {

        try {
            adView2 = AdView(this).apply {
                adUnitId = admobSDKBuilder?.bannerId.orEmpty()
                setAdSize(getSize())
            }

            val adRequest = AdRequest.Builder().build()
            Log.d("ADS_AdMob", "BannerId: ${adView2?.adUnitId}")

            adView2?.adListener = object : AdListener() {
                override fun onAdLoaded() {
                    setToast("load sukses"+adView2?.adUnitId)
                    Log.d("ADS_AdMob", "Banner loaded successfully: ${adView2?.adUnitId}")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    setToast("load gagal"+error.message+adView2?.adUnitId)
                    // Log.e("ADS_AdMob", "Refresh Gagal. KODE: ${error.code}, PESAN: ${error.message}")
                    Log.d("ADS_AdMob", "Banner failed to load: ${error.message}"+adView2?.adUnitId.toString())
                    // Fallback langsung ke main thread, karena kita sudah di main thread
                    if (fanSDKBuilder?.enable == true) {
                        setupBannerFacebook(adViewContainer)
                    }
                }

                override fun onAdOpened() {
                    Log.d("ADS_AdMob", "Banner ad opened")
                }

                override fun onAdClicked() {
                    Log.d("ADS_AdMob", "Banner ad clicked")
                }

                override fun onAdClosed() {
                    Log.d("ADS_AdMob", "Banner ad closed")
                }
            }

            adView2?.loadAd(adRequest)
            adViewContainer.addView(adView2)



            if(fanSDKBuilder?.enable==true){
                try {
                    val adListener = object : com.facebook.ads.AdListener {
                        override fun onError(ad: Ad, adError: com.facebook.ads.AdError) {
                            setLog(
                                "ADS_FAN",
                                "Banner error loaded id = ${ad.placementId} ---> ${adError.errorMessage}"
                            )
                        }

                        override fun onAdLoaded(ad: Ad) {
                            setLog("ADS_FAN", "Banner Successfully Loaded id = ${ad.placementId}")
                        }

                        override fun onAdClicked(ad: Ad) {}
                        override fun onLoggingImpression(ad: Ad) {}
                    }
                    val fanBannerId = fanSDKBuilder?.bannerId.toString()

                    fanAdView = com.facebook.ads.AdView(
                        this,
                        fanBannerId,
                        com.facebook.ads.AdSize.BANNER_HEIGHT_50
                    )

                    fanAdView?.loadAd(
                        fanAdView?.buildLoadAdConfig()
                            ?.withAdListener(adListener)
                            ?.build()
                    )

                    // Clear old views if any
                    adViewContainer.addView(fanAdView)

                } catch (e: Exception) {
                    setLog("ADS_FAN_EXCEPTION", e.message.orEmpty())
                }
            }


        } catch (e: Exception) {
            Log.e("ADS_AdMob", "Banner load exception: ${e.message}")
            setLog(e.message.toString())
        }
    }


    fun setupBannerFacebook(adContainer: FrameLayout) {

        if(fanSDKBuilder?.enable==true){
            try {
                val adListener = object : com.facebook.ads.AdListener {
                    override fun onError(ad: Ad, adError: com.facebook.ads.AdError) {
                        setLog(
                            "ADS_FAN",
                            "Banner error loaded id = ${ad.placementId} ---> ${adError.errorMessage}"
                        )
                    }

                    override fun onAdLoaded(ad: Ad) {
                        setLog("ADS_FAN", "Banner Successfully Loaded id = ${ad.placementId}")
                    }

                    override fun onAdClicked(ad: Ad) {}
                    override fun onLoggingImpression(ad: Ad) {}
                }
                val fanBannerId = fanSDKBuilder?.bannerId.toString()

                fanAdView = com.facebook.ads.AdView(
                    this,
                    fanBannerId,
                    com.facebook.ads.AdSize.BANNER_HEIGHT_50
                )

                fanAdView?.loadAd(
                    fanAdView?.buildLoadAdConfig()
                        ?.withAdListener(adListener)
                        ?.build()
                )

                // Clear old views if any
                adContainer.addView(fanAdView)

            } catch (e: Exception) {
                setLog("ADS_FAN_EXCEPTION", e.message.orEmpty())
            }
        }
    }



    /*   private fun isWebViewSupported(): Boolean {
           return try {
               WebView(this)
               if (BuildConfig.DEBUG) {
                   // setToast("WebView didukung pada perangkat ini.")
               }
               true
           } catch (e: Exception) {
               if (BuildConfig.DEBUG) {
                   // setToast("WebView tidak didukung pada perangkat ini.")
               }
               false
           }
       }*/




    private fun getSize(): AdSize {
        val widthPixels = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = (getSystemService(WINDOW_SERVICE) as WindowManager).currentWindowMetrics
            val insets = windowMetrics.windowInsets
                .getInsetsIgnoringVisibility(
                    WindowInsets.Type.navigationBars() or WindowInsets.Type.displayCutout()
                )
            val insetsWidth = insets.left + insets.right
            windowMetrics.bounds.width() - insetsWidth
        } else {
            @Suppress("DEPRECATION")
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(metrics)
            metrics.widthPixels
        }

        val density = resources.displayMetrics.density.takeIf { it > 0 } ?: 1f
        val adWidth = (widthPixels / density).toInt()

        // Get adaptive size dari AdMob
        val adaptiveSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(this, adWidth)

        // Force height max 60dp
        val customHeight = adaptiveSize.height.coerceAtMost(60)

        // Buat AdSize custom
        return AdSize(adWidth, customHeight)
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

    fun setupInterstitial() {

        if(isWebViewAvailable()){

            CoroutineScope(Dispatchers.Main).launch {

                if (Build.VERSION.SDK_INT == Build.VERSION_CODES.O||Build.VERSION.SDK_INT == Build.VERSION_CODES.O_MR1) {
                    try {
                        if (fanSDKBuilder?.enable==true) {
                            setupInterstitialFacebook()
                        }
                    } catch (e: Exception) {
                        setLog("asywalul fbb :${e.message}")
                    }
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

                                        try {
                                            if (fanSDKBuilder?.enable==true) {
                                                setupInterstitialFacebook()
                                            }
                                        } catch (e: Exception) {
                                            setLog("asywalul fbb :${e.message}")
                                        }
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


    fun setupInterstitialFacebook() {

        if (isWebViewAvailable()) {
            try {
                interstitialFANAd =
                    com.facebook.ads.InterstitialAd(this, fanSDKBuilder?.interstitialId.toString())
                val interstitialAdListener = object : InterstitialAdListener {
                    override fun onInterstitialDisplayed(ad: Ad) {
                        setLog("ADS_FAN", "show Interstitial success " + ad.placementId)
                    }

                    override fun onInterstitialDismissed(ad: Ad) {
                        interstitialFANAd = null
                        Log.d("ADS_FAN", "Interstitial dismiss")
                        setupInterstitialFacebook()
                    }

                    override fun onError(p0: Ad?, adError: com.facebook.ads.AdError?) {
                        Log.e("ADS_FAN", "Interstitial failed to load: ${adError?.errorMessage}")
                    }

                    override fun onAdLoaded(ad: Ad) {
                        Log.d("ADS_FAN", "Interstitial is loaded and ready to be displayed!")
                        showFANInterstitial = true
                    }

                    override fun onAdClicked(ad: Ad) {
                    }

                    override fun onLoggingImpression(ad: Ad) {

                    }
                }

                interstitialFANAd?.loadAd(
                    interstitialFANAd?.buildLoadAdConfig()
                        ?.withAdListener(interstitialAdListener)
                        ?.build()
                )
            } catch (e: Exception) {
                setLog("asywalul fb :" + e.message)
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
                if(fanSDKBuilder?.enable==true){
                    if (showFANInterstitial) {
                        Log.d("showIntersFA", "true")
                        interstitialFANAd?.let { fanAd ->
                            window.decorView.rootView.post {
                                try {
                                    fanAd.show()
                                } catch (e: Exception) {
                                    setLog("Error showing FAN Interstitial: ${e.message}")
                                    if(unitySDKBuilder?.enable==true){
                                        //showInterstitialUnity()
                                    }
                                }
                            }
                        }
                    }
                }else{
                    if(unitySDKBuilder?.enable==true){
                       // showInterstitialUnity()
                    }
                }
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
                    getFirebaseToken()
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
    fun showInterstitialFAN(){
        try {
            Log.d("showInters","execute")
            if(showFANInterstitial){
                Log.d("showIntersFA","true")
                interstitialFANAd?.show()
            }
        }catch (e : Exception){
            Log.d("showInters","false")
            setLog(e.message.toString())
        }
    }


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


    fun setupBannerUnity(adContainer: FrameLayout) {
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
    }


    private val interstitialAdId = "Interstitial_Android"

    // Load dulu
    fun loadInterstitialUnityAds() {
        try {
            UnityAds.load(interstitialAdId, object : IUnityAdsLoadListener {
                override fun onUnityAdsAdLoaded(placementId: String?) {
                    Log.d("UnityAds", "Ad Loaded: $placementId")
                }

                override fun onUnityAdsFailedToLoad(
                    placementId: String?,
                    error: UnityAdsLoadError?,
                    message: String?
                ) {
                    Log.e("UnityAds", "Failed to load: $placementId - $message")
                }
            })
        }catch (e : Exception){
            //
        }
    }

    // Tampilkan saat dibutuhkan
    fun showInterstitialUnity() {
        try {
            UnityAds.show(this,"Interstitial_Android", object : IUnityAdsShowListener {
                override fun onUnityAdsShowComplete(
                    placementId: String?,
                    state: UnityAdsShowCompletionState?
                ) {
                    if (placementId == interstitialAdId && state == UnityAdsShowCompletionState.COMPLETED) {
                        Log.d("UnityAds", "Ad Completed")
                        // Reward atau lanjutkan
                    }
                }

                override fun onUnityAdsShowStart(placementId: String?) {
                    Log.d("UnityAds", "Ad Started")
                }

                override fun onUnityAdsShowClick(placementId: String?) {
                    Log.d("UnityAds", "Ad Clicked")
                }

                override fun onUnityAdsShowFailure(
                    placementId: String?,
                    error: UnityAdsShowError?,
                    message: String?
                ) {
                    Log.e("UnityAds", "Ad Show Failed: $message")
                }
            })
        }catch (e : Exception){

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

}
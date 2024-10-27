package sound.recorder.widget.base

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.ActivityNotFoundException
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
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.view.Window
import android.view.animation.AccelerateInterpolator
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.animation.LinearInterpolator
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
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
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
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
import sound.recorder.widget.ads.GoogleMobileAdsConsentManager
import sound.recorder.widget.animation.ParticleSystem
import sound.recorder.widget.animation.modifiers.ScaleModifier
import sound.recorder.widget.builder.AdmobSDKBuilder
import sound.recorder.widget.builder.FanSDKBuilder
import sound.recorder.widget.listener.MyAdsListener
import sound.recorder.widget.notes.Note
import sound.recorder.widget.util.DataSession
import sound.recorder.widget.util.Toastic
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.seconds


open class BaseActivityWidget : AppCompatActivity() {

    private var mInterstitialAd: InterstitialAd? = null
    private var isLoad = false
    private var rewardedAd: RewardedAd? = null
    private var isLoadReward = false
    private var interstitialFANAd : com.facebook.ads.InterstitialAd? =null
    private var isLoadInterstitialReward = false
    private var rewardedInterstitialAd : RewardedInterstitialAd? =null

    private val isMobileAdsInitializeCalled = AtomicBoolean(false)
    private val initialLayoutComplete = AtomicBoolean(false)
    private var adView: AdView? =null
    private var adViewFacebook : com.facebook.ads.AdView? = null
    private lateinit var googleMobileAdsConsentManager: GoogleMobileAdsConsentManager
    private lateinit var consentInformation: ConsentInformation
    private var TAG = "GDPR_App"

    private var isPrivacyOptionsRequired: Boolean = false
    private var showFANInterstitial = false

    private lateinit var appUpdateManager: AppUpdateManager       // in app update
    private val updateType = AppUpdateType.FLEXIBLE

    var sharedPreferences : SharedPreferences? =null

    private var appOpenAd: AppOpenAd? = null
    var admobSDKBuilder : AdmobSDKBuilder? =null
    var fanSDKBuilder : FanSDKBuilder? =null
    var mPanAnim: Animation? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        admobSDKBuilder = AdmobSDKBuilder.builder(this).loadFromSharedPreferences()
        fanSDKBuilder = FanSDKBuilder.builder(this).loadFromSharedPreferences()

    }

    protected fun setupFragment(id : Int, fragment : Fragment?){
        try {
            if(fragment!=null){
                val fragmentManager = supportFragmentManager
                val fragmentTransaction = fragmentManager.beginTransaction()
                fragmentTransaction.replace(id, fragment)
                fragmentTransaction.commit()
            }
        }catch (e : Exception){
            setLog(e.message.toString())
        }
    }


    fun checkUpdate() {
        lifecycleScope.launch {
            try {
                val info = withContext(Dispatchers.IO) {
                    appUpdateManager.appUpdateInfo.await()
                }

                val isUpdateAvailable = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE

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

    fun setupBannerFacebook(adContainer : FrameLayout){
        try {
            val adListener = object : com.facebook.ads.AdListener {
                override fun onError(ad: Ad, adError: com.facebook.ads.AdError) {
                    setLog("ADS_FAN","Banner error loaded id = "+ ad.placementId +"---> "+ adError.errorMessage)
                }

                override fun onAdLoaded(ad: Ad) {
                    setLog("ADS_FAN","Banner Successfully Loaded id = "+ ad.placementId)
                }

                override fun onAdClicked(ad: Ad) {
                }
                override fun onLoggingImpression(ad: Ad) {
                }
            }

            val adView = com.facebook.ads.AdView(this, fanSDKBuilder?.bannerId.toString(), com.facebook.ads.AdSize.BANNER_HEIGHT_50)
            adView.loadAd(adView.buildLoadAdConfig().withAdListener(adListener).build())
            this.adViewFacebook = adView
            adContainer.addView(adView)

        }catch (e : Exception){
            setLog(e.message.toString())
        }

    }


    fun setupInterstitialFacebook(){

        if(isWebViewSupported()&&isWebViewAvailable()){
            try {
                interstitialFANAd = com.facebook.ads.InterstitialAd(this, fanSDKBuilder?.interstitialId.toString())
                val interstitialAdListener = object : InterstitialAdListener {
                    override fun onInterstitialDisplayed(ad: Ad) {
                        setLog("ADS_FAN","show Interstitial success "+ad.placementId)
                    }

                    override fun onInterstitialDismissed(ad: Ad) {
                        interstitialFANAd =null
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
            }catch (e : Exception){
                setLog("asywalul fb :"+e.message)
            }
        }

    }


    fun onDestroyUpdate() {
        try {
            if (updateType == AppUpdateType.FLEXIBLE) {
                appUpdateManager.unregisterListener(installStateUpdatedListener)
            }
        }catch (e : Exception){
            Log.d("not","support")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mInterstitialAd = null
        if(adView!=null){
            adView?.destroy()
        }

    }

    override fun onPause() {
        super.onPause()
        if(adView!=null){
            adView?.pause()
        }
    }

    override fun onResume() {
        super.onResume()
        if(adView!=null){
            adView?.resume()
        }
    }

    fun destroyAds(){
        if(adView!=null){
            adView?.destroy()
        }
    }

    fun pauseAds(){
        if(adView!=null){
            adView?.pause()
        }
    }

    fun resumeAds(){
        if(adView!=null){
            adView?.resume()
        }
    }




    private val installStateUpdatedListener = InstallStateUpdatedListener{ state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) {
            try {
                setToastInfo(getString(R.string.download_success))
                lifecycleScope.launch {
                    delay(5.seconds)
                    appUpdateManager.completeUpdate()
                }
            }catch (e : Exception){
                Log.d("not","support")
            }
        }
    }


    fun setupGDPR() {
        if(isAdMobAvailable()){
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val params = ConsentRequestParameters
                        .Builder()
                        .setTagForUnderAgeOfConsent(false)
                        .build()

                    consentInformation = UserMessagingPlatform.getConsentInformation(this@BaseActivityWidget)
                    isPrivacyOptionsRequired = consentInformation.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

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
                                            if(BuildConfig.DEBUG){
                                                setToastError(it.message.toString())
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        { requestConsentError ->
                            // Consent gathering failed.
                            Log.w(TAG, String.format("%s: %s", requestConsentError.errorCode, requestConsentError.message))
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

    fun getDataSession() : DataSession{
        return DataSession(this)
    }


    private fun getSize(): AdSize {
        val widthPixels = resources.displayMetrics.widthPixels.toFloat()
        val density = resources.displayMetrics.density
        val adWidth = (widthPixels / density).toInt()

        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(this, adWidth)
    }



    fun openPlayStoreForMoreApps(devName : String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://developer?id=$devName"))
            intent.setPackage("com.android.vending") // Specify the Play Store app package name

            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/developer?id=$devName"))

            startActivity(intent)
        }
    }


    @SuppressLint("SetTextI18n")
    fun showDialogEmail(appName : String ,info : String) {

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
            if(message.isEmpty()){
                setToastWarning(getString(R.string.message_cannot_empty))
                return@setOnClickListener
            }else{
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
            RecordingSDK.openEmail(this,subject,body)
        }catch (e : Exception){
            setToastError(e.message.toString())
        }

    }




    fun showLoadingLayout(context: Context,long : Long){
        try {
            showLoadingProgress(context,long)
        } catch (e: Exception) {
            setLog(e.message.toString())
        }
    }

    @SuppressLint("SetTextI18n")
    fun showLoadingProgress(context: Context,long : Long) {

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
        }catch (e : Exception){
            Log.d("message",e.message.toString())
        }
    }

    fun isInternetConnected(context: Context): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

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


    fun getNoteValue(note: Note) : String{
        val valueNote = try {
            val jsonObject = JSONObject(note.note.toString())
            val value = Gson().fromJson(note.note, Note::class.java)
            // The JSON string is valid
            value.note.toString()

        } catch (e: Exception) {
            // The JSON string is not valid
            note.note
        }

        return  valueNote
    }

    fun getTitleValue(note: Note) : String{
        var valueNote = ""
        valueNote = try {
            val jsonObject = JSONObject(note.note.toString())
            val value = Gson().fromJson(note.title, Note::class.java)
            // The JSON string is valid
            value.note.toString()

        } catch (e: Exception) {
            // The JSON string is not valid
            "No title"
        }

        return  valueNote
    }


    private fun isWebViewSupported(): Boolean {
        return try {
            WebView(this)
            if(BuildConfig.DEBUG){
                setToast("WebView didukung pada perangkat ini.")
            }
            true
        } catch (e: Exception) {
            if(BuildConfig.DEBUG){
                setToast("WebView tidak didukung pada perangkat ini.")
            }
            false
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


    private fun isAdMobAvailable(): Boolean {
        return try {
            Class.forName("com.google.android.gms.ads.MobileAds")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }

    fun setupBanner(adViewContainer:FrameLayout){
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.O||Build.VERSION.SDK_INT == Build.VERSION_CODES.O_MR1) {
            if(isWebViewSupported()&&isWebViewAvailable()){
                setupBannerFacebook(adViewContainer)
            }
        }else{
            if(isWebViewSupported()&&isWebViewAvailable()){
                if(isAdMobAvailable()){
                    executeBanner(adViewContainer)
                }
            }
        }
    }

    private fun executeBanner(adViewContainer:FrameLayout){
        try {
            val adView = AdView(this)
            adView.adUnitId = admobSDKBuilder?.bannerId.toString()
            adView.setAdSize(getSize())
            val adRequest = AdRequest.Builder().build()
            adView.adListener = object : AdListener() {
                override fun onAdLoaded() {
                    Log.d("ADS_AdMob", "banner loaded successfully "+adView.adUnitId)
                }
                override fun onAdFailedToLoad(p0: LoadAdError) {
                    Log.d("ADS_AdMob", "banner loaded failed "+p0.message)
                    if(getDataSession().getFanEnable()){
                        setupBannerFacebook(adViewContainer)
                    }
                }
                override fun onAdOpened() {

                }
                override fun onAdClicked() {

                }
                override fun onAdClosed() {

                }
            }
            adView.loadAd(adRequest)
            adViewContainer.removeAllViews()
            adViewContainer.addView(adView)
            this.adView = adView

        }catch (e : Exception){
            setLog(e.message.toString())
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

        if(isWebViewSupported()&&isWebViewAvailable()&&isAdMobAvailable()){
            val adRequest = AdRequest.Builder().build()
            AppOpenAd.load(this, getDataSession().getAppOpenId(), adRequest,getDataSession().getOrientationAds(), object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAd = ad
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    if(BuildConfig.DEBUG){
                        setToast("openAds " +loadAdError.message)
                    }
                }
            })
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
                        MyAdsListener.setAds(true)
                    }

                    override fun onAdFailedToShowFullScreenContent(p0: AdError) {
                        Log.d("AppOpenAd", "Failed to show ad: ${p0.message}")
                    }

                    override fun onAdShowedFullScreenContent() {
                        MyAdsListener.setAds(false)
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

        if(isWebViewSupported()&&isWebViewAvailable()){

            CoroutineScope(Dispatchers.Main).launch {

                if (Build.VERSION.SDK_INT == Build.VERSION_CODES.O||Build.VERSION.SDK_INT == Build.VERSION_CODES.O_MR1) {
                    try {
                        if (getDataSession().getFanEnable()) {
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
                                                mInterstitialAd = null // Reset the interstitial ad
                                            }
                                        }
                                    }

                                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                                        mInterstitialAd = null
                                        isLoad = false
                                        Log.d("Admob","Interstitial Loaded Failed id = ${admobSDKBuilder?.interstitialId.toString()} ---> ${loadAdError.message}")

                                        try {
                                            if (getDataSession().getFanEnable()) {
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

   /* fun setupInterstitial1() {
        try {
            if (getDataSession().getFanEnable()) {
                setupInterstitialFacebook()
            }
        }catch (e : Exception){
            setLog("asywalul fbb :"+e.message)
        }
        try {
            val adRequest = AdRequest.Builder().build()
            adRequest.let {
                InterstitialAd.load(this, getDataSession().getInterstitialId(), it,
                    object : InterstitialAdLoadCallback() {
                        override fun onAdLoaded(interstitialAd: InterstitialAd) {
                            mInterstitialAd = interstitialAd
                            isLoad = true
                            setLog("AdMob Inters Loaded Success")

                            // Set the FullScreenContentCallback
                            mInterstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                                override fun onAdDismissedFullScreenContent() {
                                    // Handle the ad dismissed event
                                    setLog("AdMob Inters Ad Dismissed")
                                    if(BuildConfig.DEBUG){
                                        setToast("ads closed")
                                    }
                                    // Load a new interstitial ad
                                    mInterstitialAd = null
                                    setupInterstitial()
                                }

                                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                                    // Handle the ad failed to show event
                                    setLog("AdMob Inters Ad Failed to Show: ${adError.message}")
                                    if(BuildConfig.DEBUG){
                                        setToast(adError.message)
                                    }
                                }

                                override fun onAdShowedFullScreenContent() {
                                    // Handle the ad showed event
                                    setLog("AdMob Inters Ad Showed")
                                    mInterstitialAd = null // Reset the interstitial ad
                                }
                            }
                        }

                        override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                            mInterstitialAd = null
                            isLoad = false
                            setLog("AdMob Inters Loaded Failed id = " + getDataSession().getInterstitialId() + " ---> " + loadAdError.message)
                        }
                    })
            }
        } catch (e: Exception) {
            setLog("asywalul inters :"+e.message.toString())
        }
    }*/

    fun releaseInterstitialAdmob(){
        mInterstitialAd = null
    }

    fun releaseInterstitialFAN(){
        interstitialFANAd = null
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


    fun setupReward(){
        try {
            val adRequest = AdRequest.Builder().build()
            RewardedAd.load(this,DataSession(this).getRewardId(), adRequest, object : RewardedAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    rewardedAd = null
                }
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    isLoadReward = true
                    rewardedAd?.fullScreenContentCallback = object: FullScreenContentCallback() {
                        override fun onAdClicked() {
                            // Called when a click is recorded for an ad.
                            Log.d("yametere", "Ad was clicked.")
                        }

                        override fun onAdDismissedFullScreenContent() {
                            // Called when ad is dismissed.
                            // Set the ad reference to null so you don't show the ad a second time.
                            Log.d("yametere", "Ad dismissed fullscreen content.")
                            rewardedAd = null
                        }

                        override fun onAdFailedToShowFullScreenContent(p0: AdError) {
                            // Called when ad fails to show.
                            Log.d("yametere", "Ad failed to show fullscreen content.")
                            rewardedAd = null
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
            })
        }catch (e : Exception){
            setLog(e.message.toString())
        }
    }

    fun showRewardAds(){
        try {
            if(isLoadReward){
                Log.d("yametere", "show")
                rewardedAd?.let { ad ->
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
            }else{
                Log.d("yametere", "nall")
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


    fun showInterstitial(){
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

    protected fun showReward(){
        try {
            if(isLoadReward){
                rewardedAd?.let { ad ->
                    ad.show(this) { rewardItem ->
                        // Handle the reward.
                        val rewardAmount = rewardItem.amount
                        val rewardType = rewardItem.type
                    }
                } ?: run {
                    showInterstitial()
                }
            }
        }catch (e : Exception){
            setLog(e.message.toString())
        }
    }


    protected fun setToastError(message : String){
        try {
            Toastic.toastic(
                context = this,
                message = "$message.",
                duration = Toastic.LENGTH_SHORT,
                type = Toastic.ERROR,
                isIconAnimated = true
            ).show()
        }catch (e : Exception){
            setLog(e.message.toString())
        }

    }


    protected fun setToast(message : String){
        Toast.makeText(this, "$message.",Toast.LENGTH_SHORT).show()
    }
    fun setToastWarning(message : String){
        try {
            Toastic.toastic(
                context = this,
                message = "$message.",
                duration = Toastic.LENGTH_SHORT,
                type = Toastic.WARNING,
                isIconAnimated = true
            ).show()
        }catch (e : Exception){
            setLog(e.message.toString())
        }
    }

    protected fun setToastSuccess(message : String){
        try {
            Toastic.toastic(
                context = this,
                message = "$message.",
                duration = Toastic.LENGTH_SHORT,
                type = Toastic.SUCCESS,
                isIconAnimated = true
            ).show()
        }catch (e : Exception){
            setLog(e.message.toString())
        }
    }

    fun setToastInfo(message : String){
        try {
            Toastic.toastic(
                context = this,
                message = "$message.",
                duration = Toastic.LENGTH_SHORT,
                type = Toastic.INFO,
                isIconAnimated = true
            ).show()
        }catch (e : Exception){
            setLog(e.message.toString())
        }

    }


    fun setLog(message: String){
        Log.d("response", "$message - ")
    }

    fun setLog(name : String, message: String){
        Log.d(name, "$message - ")
    }

    fun openSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = Uri.fromParts("package", packageName.toString(), null)
        startActivity(intent)
    }

    protected fun simpleAnimation(view: View , drawable:Int? = null) {
        try {
            var icon  = R.drawable.star_pink
            if(drawable!=null){
                icon = drawable
            }
            ParticleSystem(this, 100, icon, 800)
                .setSpeedRange(0.1f, 0.25f)
                .oneShot(view, 100)
        }catch (e : Exception){
            setLog(e.message.toString())
        }

    }

    protected fun advanceAnimation(view: View , drawable:Int? = null) {
        // Launch 2 particle systems one for each image
        try {
            var icon  = R.drawable.star_white_border
            if(drawable!=null){
                icon = drawable
            }
            val ps = ParticleSystem(this, 100, icon, 800)
            ps.setScaleRange(0.7f, 1.3f)
            ps.setSpeedRange(0.1f, 0.25f)
            ps.setAcceleration(0.0001f, 90)
            ps.setRotationSpeedRange(90f, 180f)
            ps.setFadeOut(200, AccelerateInterpolator())
            ps.oneShot(view, 100)
        }catch (e : Exception){
            setLog(e.message.toString())
        }
    }

    open fun starAnimation(view: View , drawable:Int? = null) {

        try{
            var icon  = R.drawable.star_white_border
            if(drawable!=null){
                icon = drawable
            }
            ParticleSystem(this, 10, icon, 3000)
                .setSpeedByComponentsRange(-0.1f, 0.1f, -0.1f, 0.02f)
                .setAcceleration(0.000003f, 90)
                .setInitialRotationRange(0, 360)
                .setRotationSpeed(120f)
                .setFadeOut(2000)
                .addModifier(ScaleModifier(0f, 1.5f, 0, 1500))
                .oneShot(view, 10)
        }catch (e : Exception){
            setLog(e.message.toString())
        }

    }

    open fun getActivity(): BaseActivityWidget? {
        return this
    }

    open fun rating(){
        val appPackageName = packageName

        try {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=$appPackageName")
                )
            )
        } catch (e: ActivityNotFoundException) {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$appPackageName")
                )
            )
        }
    }

    fun isDarkTheme(): Boolean {
        return resources?.configuration?.uiMode!! and
                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }



    fun showKeyboard(view: View) {
        try {
            if (view.requestFocus()) {
                val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
                imm?.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
            }
        }catch (e : Exception){
            setLog(e.message.toString())
        }

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

    fun hideKeyboard(view: View) {
        try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
            imm?.hideSoftInputFromWindow(view.windowToken, 0)
        }catch (e : Exception){
            setLog(e.message.toString())
        }

    }

}
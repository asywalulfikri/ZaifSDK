package sound.recorder.widget.base

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.ActivityNotFoundException
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
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.view.Window
import android.view.animation.AccelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.RadioButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.facebook.ads.Ad
import com.facebook.ads.AudienceNetworkAds
import com.facebook.ads.InterstitialAdListener
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.admanager.AdManagerAdView
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
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
//import com.google.firebase.FirebaseApp
//import com.google.firebase.messaging.FirebaseMessaging
import com.google.gson.Gson
import org.json.JSONObject
import sound.recorder.widget.R
import sound.recorder.widget.RecordingSDK
import sound.recorder.widget.ads.GoogleMobileAdsConsentManager
import sound.recorder.widget.notes.Note
import sound.recorder.widget.util.DataSession
import sound.recorder.widget.util.Toastic
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sound.recorder.widget.BuildConfig
import sound.recorder.widget.animation.ParticleSystem
import sound.recorder.widget.animation.modifiers.ScaleModifier
import kotlin.time.Duration.Companion.seconds

open class BaseFragmentWidget : Fragment() {

    private var mInterstitialAd: InterstitialAd? = null
    var id: String? = null
    private var isLoad = false
    private var rewardedAd: RewardedAd? = null
    private var isLoadReward = false
    private var interstitialFANAd : com.facebook.ads.InterstitialAd? =null
    private var isLoadInterstitialReward = false
    private var rewardedInterstitialAd : RewardedInterstitialAd? =null

    private val isMobileAdsInitializeCalled = AtomicBoolean(false)
    private val initialLayoutComplete = AtomicBoolean(false)
    private var adView: AdManagerAdView? =null
    private lateinit var googleMobileAdsConsentManager: GoogleMobileAdsConsentManager
    private lateinit var consentInformation: ConsentInformation
    private var TAG = "GDPR_App"

    private var isPrivacyOptionsRequired: Boolean = false
    private var showFANInterstitial = false

    private lateinit var appUpdateManager: AppUpdateManager       // in app update
    private val updateType = AppUpdateType.FLEXIBLE
    var sharedPreferences : SharedPreferences? =null
    var fileName =  ""
    var dirPath = ""
    val LOG_TAG = "AudioRecordTest"


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            val languageCode = Locale.getDefault().language
            getDataSession().saveDefaultLanguage(languageCode)
            setLocale(getDataSession().getDefaultLanguage())
        }catch (e : Exception){
            setToastError(activity,e.message.toString())
        }

    }


    fun initFANSDK(){
        if(getDataSession().getFanEnable()){
            AudienceNetworkAds.initialize(requireContext());
        }

    }


    fun setupFragment(id : Int, fragment : Fragment?){
        try {
            if(fragment!=null){
                val fragmentManager = activity?.supportFragmentManager
                val fragmentTransaction = fragmentManager?.beginTransaction()
                fragmentTransaction?.replace(id, fragment)
                fragmentTransaction?.commit()
            }
        }catch (e : Exception){
            setLog(e.message.toString())
        }
    }


    fun setupBannerFacebook(adContainer : FrameLayout){
        val id = getDataSession().getBannerFANId()
        val adListener = object : com.facebook.ads.AdListener {
            override fun onError(ad: Ad, adError: com.facebook.ads.AdError) {
                setLog("FAN error loaded id = "+ ad.placementId +"---> "+ adError.errorMessage)
            }

            override fun onAdLoaded(ad: Ad) {
                setLog("FAN Banner Success Loaded id = " + ad.placementId)
            }

            override fun onAdClicked(ad: Ad) {
                // Ad clicked callback
            }

            override fun onLoggingImpression(ad: Ad) {
                // Ad impression logged callback
            }
        }

        val adView = com.facebook.ads.AdView(requireContext(), id, com.facebook.ads.AdSize.BANNER_HEIGHT_50);
        adView.loadAd(adView.buildLoadAdConfig().withAdListener(adListener).build())
        adContainer.addView(adView);

    }


    fun setupInterstitialFacebook(){
        val id = getDataSession().getInterstitialFANId()
        try {
            interstitialFANAd = com.facebook.ads.InterstitialAd(requireContext(), id)
            val interstitialAdListener = object : InterstitialAdListener {
                override fun onInterstitialDisplayed(ad: Ad) {
                    // Interstitial ad displayed callback
                    //Log.e(, "Interstitial ad displayed.")
                    setLog("FAN show Interstitial success "+ad.placementId)
                }

                override fun onInterstitialDismissed(ad: Ad) {
                    // Interstitial dismissed callback
                    if(BuildConfig.DEBUG){
                        setToast(activity,"close FAN ads")
                    }
                    interstitialFANAd =null
                    setupInterstitialFacebook()
                }

                override fun onError(p0: Ad?, adError: com.facebook.ads.AdError?) {
                    Log.e(TAG, "Interstitial ad failed to load: ${adError?.errorMessage}")
                }

                override fun onAdLoaded(ad: Ad) {
                    // Interstitial ad is loaded and ready to be displayed
                    Log.d(TAG, "Interstitial ad is loaded and ready to be displayed!")
                    showFANInterstitial = true

                }

                override fun onAdClicked(ad: Ad) {
                    // Ad clicked callback
                    Log.d(TAG, "Interstitial ad clicked!")
                }

                override fun onLoggingImpression(ad: Ad) {
                    // Ad impression logged callback
                    Log.d(TAG, "Interstitial ad impression logged!")
                }
            }

// For auto-play video ads, it's recommended to load the ad
// at least 30 seconds before it is shown
            interstitialFANAd?.loadAd(
                interstitialFANAd?.buildLoadAdConfig()
                    ?.withAdListener(interstitialAdListener)
                    ?.build()
            )
        }catch (e : Exception){
            setLog("asywalul fb fr :"+e.message)
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


    override fun onDestroyView() {
        super.onDestroyView()
        mInterstitialAd = null
        if(adView!=null){
            adView?.destroy()
        }
    }


    fun setupGDPR(){
        try {
            // Set tag for under age of consent. false means users are not under age
            // of consent.

            /*  val debugSettings = ConsentDebugSettings.Builder(this)
                  .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                  .addTestDeviceHashedId("0c302266-17a0-4f2a-a11a-10ca1ad1abe1")
                  .build()*/

            val params = ConsentRequestParameters
                .Builder()
                // .setConsentDebugSettings(debugSettings)
                .setTagForUnderAgeOfConsent(false)
                .build()

            consentInformation = UserMessagingPlatform.getConsentInformation(requireContext())
            isPrivacyOptionsRequired  = consentInformation.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

            consentInformation.requestConsentInfoUpdate(
                requireActivity(),
                params, {
                    UserMessagingPlatform.loadAndShowConsentFormIfRequired(requireActivity()) {

                            loadAndShowError -> run {
                        Log.w(
                            TAG, String.format(
                                "%s: %s",
                                loadAndShowError?.errorCode,
                                loadAndShowError?.message
                            )
                        )

                    }
                        if (isPrivacyOptionsRequired) {
                            // Regenerate the options menu to include a privacy setting.
                            UserMessagingPlatform.showPrivacyOptionsForm(requireActivity()) { formError ->
                                formError?.let {
                                    setToastError(activity,it.message.toString())
                                }
                            }
                        }
                    }
                },
                {
                        requestConsentError ->
                    // Consent gathering failed.
                    Log.w(TAG, String.format("%s: %s",
                        requestConsentError.errorCode,
                        requestConsentError.message))
                })

            if (consentInformation.canRequestAds()) {
                MobileAds.initialize(requireContext()) {}
            }
        }catch (e :Exception){
            Log.d("message",e.message.toString())
        }
    }


    fun getDataSession() : DataSession{
        return DataSession(requireContext())
    }

    private fun loadBanner(adViewContainer: FrameLayout,bannerId : String? =null) {
        try {
            var id = ""
            id = if(bannerId.isNullOrEmpty() || bannerId.isBlank()){
                getDataSession().getBannerId()
            }else{
                bannerId
            }
            adView?.adUnitId = id
            adView?.setAdSizes(getSize(adViewContainer))
            val adRequest = AdManagerAdRequest.Builder().build()
            adView?.adListener = object : AdListener() {
                override fun onAdLoaded() {
                    Log.d("AdMob", "Ad loaded successfully unit = $id")
                }

                override fun onAdFailedToLoad(p0: LoadAdError) {
                    if(getDataSession().getFanEnable()){
                        setupBannerFacebook(adViewContainer)
                    }
                    Log.d("AdMob", "Ad failed to load:"+ p0.message + "id = "+id)
                }

                override fun onAdOpened() {
                    Log.d("AdMob", "Ad opened")
                }

                override fun onAdClicked() {
                    Log.d("AdMob", "Ad clicked")
                }

                override fun onAdClosed() {
                    Log.d("AdMob", "Ad closed")
                }
            }
            adView?.loadAd(adRequest)
        }catch (e : Exception){
            setLog(e.message.toString())
        }
    }

    fun openFragment(view : Int, fragment : Fragment?){
        if(activity!=null){
            try {
                // some code
                fragment?.let {
                    activity?.supportFragmentManager?.beginTransaction()
                        ?.add(view, it)
                        ?.commit()
                }
            } catch (e: Exception) {
                setToastError(activity,e.message.toString())
            }
        }
    }

    private fun getSize(adViewContainer: FrameLayout): AdSize{
        val display = activity?.windowManager?.defaultDisplay
        val outMetrics = DisplayMetrics()
        display?.getMetrics(outMetrics)

        val density = outMetrics.density

        var adWidthPixels = adViewContainer.width.toFloat()
        if (adWidthPixels == 0f) {
            adWidthPixels = outMetrics.widthPixels.toFloat()
        }

        val adWidth = (adWidthPixels / density).toInt()
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(requireContext(), adWidth)
    }

    private fun initializeMobileAdsSdk(adViewContainer: FrameLayout,bannerId: String? =null) {
        if (isMobileAdsInitializeCalled.getAndSet(true)) {
            return
        }

        // Initialize the Mobile Ads SDK.
        MobileAds.initialize(requireContext()) {}

        // Load an ad.
        if (initialLayoutComplete.get()) {
            loadBanner(adViewContainer,bannerId)
        }
    }

    fun setupBannerNew(adViewContainer: FrameLayout,bannerId : String? =null){
        adView = AdManagerAdView(requireContext())
        adViewContainer.addView(adView)

        googleMobileAdsConsentManager = GoogleMobileAdsConsentManager.getInstance(requireContext())
        googleMobileAdsConsentManager.gatherConsent(requireActivity()) { error ->
            if (error != null) {
                // Consent not obtained in current session.
                Log.d("AdMob New Error", "${error.errorCode}: ${error.message}")
            }

            // This sample attempts to load ads using consent obtained in the previous session.
            if (googleMobileAdsConsentManager.canRequestAds) {
                Log.d("AdMob New Request", "success")
                initializeMobileAdsSdk(adViewContainer,bannerId)
            }

            if (googleMobileAdsConsentManager.isPrivacyOptionsRequired) {
                // Regenerate the options menu to include a privacy setting.
                activity?.invalidateOptionsMenu()
            }
        }

        // This sample attempts to load ads using consent obtained in the previous session.
        if (googleMobileAdsConsentManager.canRequestAds) {
            Log.d("AdMob New Request", "success1")
            initializeMobileAdsSdk(adViewContainer,bannerId)
        }


        // Since we're loading the banner based on the adContainerView size, we need to wait until this
        // view is laid out before we can get the width.
        adViewContainer.viewTreeObserver.addOnGlobalLayoutListener {
            if (!initialLayoutComplete.getAndSet(true) && googleMobileAdsConsentManager.canRequestAds) {
                loadBanner(adViewContainer,bannerId)
            }
        }

        // Set your test devices. Check your logcat output for the hashed device ID to
        // get test ads on a physical device. e.g.
        // "Use RequestConfiguration.Builder().setTestDeviceIds(Arrays.asList("ABCDEF012345"))
        // to get test ads on this device."
        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder().setTestDeviceIds(listOf("D48A46E523E6A96C8215178502423686")).build()
        )
    }

    fun showArrayLanguage(){
        val languageArray = resources.getStringArray(R.array.language_array)
        val languageArrayCode = resources.getStringArray(R.array.language_code)
        val selectedLanguages = BooleanArray(languageArray.size) // Untuk melacak status CheckBox

        var selectedLanguage = "" // Untuk melacak bahasa yang dipilih

        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle(R.string.choose_language)


        builder.setSingleChoiceItems(languageArray, -1) { _, which ->
            selectedLanguage = languageArrayCode[which]
        }


        builder.setPositiveButton(getString(R.string.colorpicker_dialog_ok)) { _, _ ->
            if (selectedLanguage.isNotEmpty()) {
                getDataSession().saveDefaultLanguage(selectedLanguage)
                changeLanguage(selectedLanguage)
                // Lakukan sesuatu dengan bahasa yang dipilih
                // Toast.makeText(this, "Anda memilih bahasa: $selectedLanguage", Toast.LENGTH_SHORT).show()
            }
        }

        builder.setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
            dialog.dismiss()
        }

        val dialog = builder.create()
        dialog.show()
    }


    @SuppressLint("SetTextI18n")
    fun showDialogLanguage2() {
        val language = getDataSession().getLanguage()
        // custom dialog
        var type = ""
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_choose_language)
        dialog.setCancelable(true)

        // set the custom dialog components - text, image and button
        val rbDefault = dialog.findViewById<View>(R.id.rbDefaultLanguage) as RadioButton
        val rbEnglish = dialog.findViewById<View>(R.id.rbEnglish) as RadioButton
        val btnSave = dialog.findViewById<View>(R.id.btn_submit) as AppCompatTextView


        if(getDataSession().getLanguage()=="en"){
            rbEnglish.isChecked = true
        }else{
            rbDefault.isChecked = true
        }


        // if button is clicked, close the custom dialog
        btnSave.setOnClickListener {

            if(rbDefault.isChecked){
                type = getDataSession().getDefaultLanguage()
            }

            if(rbEnglish.isChecked){
                type = "en"
            }


            if(type.isNotEmpty()&&type!=getDataSession().getLanguage()){
                getDataSession().setLanguage(type)
                changeLanguage(type)
            }
            dialog.dismiss()
        }

        dialog.show()
    }

    @SuppressLint("NewApi")
    fun showSettingsDialog(context: Context?) {
        val builder = AlertDialog.Builder(context)
        builder.setCancelable(true)
        builder.setTitle("Permission")
        builder.setMessage(HtmlCompat.fromHtml("You need allow Permission Record Audio", HtmlCompat.FROM_HTML_MODE_LEGACY))
        builder.setPositiveButton(getString(R.string.setting)) { dialog, _ ->
            dialog.cancel()
            openSettings(context)
        }
        builder.show()
    }

    fun isInternetConnected(): Boolean {
        val connectivityManager = requireActivity().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

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

    private fun openSettings(activity: Context?) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = Uri.fromParts("package", activity?.packageName.toString(), null)
        activity?.startActivity(intent)
    }


    fun openPlayStoreForMoreApps(devName : String? =null) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://developer?id=$devName"))
            intent.setPackage("com.android.vending") // Specify the Play Store app package name

            startActivity(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/developer?id=$devName"))

            startActivity(intent)
        }
    }


    @SuppressLint("SetTextI18n")
    fun showDialogEmail(appName : String ,info : String) {

        // custom dialog
        val dialog = Dialog(requireContext())
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
                setToastWarning(activity,getString(R.string.message_cannot_empty))
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
            RecordingSDK.openEmail(requireContext(),subject,body)
        }catch (e : Exception){
            setToastError(activity,e.message.toString())
        }

    }


    private fun changeLanguage(type : String) {
        val locale = Locale(type) // Ganti "en" dengan kode bahasa yang diinginkan
        Locale.setDefault(locale)

        val configuration = Configuration()
        configuration.locale = locale

        resources.updateConfiguration(configuration, resources.displayMetrics)
        activity?.recreate()
    }


    private fun getCurrentLanguage(): String {
        /* val locale: Locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
             this.resources.configuration.locales[0]
         } else {
             this.resources.configuration.locale
         }
         return locale.displayLanguage*/
        val currentLocale: Locale = Locale.getDefault()
        return currentLocale.language
    }

    private fun setLocale(language : String) {
        try {
            val locale = Locale(language) // Ganti "en" dengan kode bahasa yang diinginkan
            Locale.setDefault(locale)

            val config = Configuration()
            config.locale = locale
            resources.updateConfiguration(config, resources.displayMetrics)
        }catch (e : Exception){
            setLog(e.message.toString())
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
        val connectivityManager = activity?.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

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


    fun getNoteValue(note: Note?) : String{
        val valueNote = try {
            val jsonObject = JSONObject(note?.note.toString())
            val value = Gson().fromJson(note?.note, Note::class.java)
            // The JSON string is valid
            value.note.toString()

        } catch (e: Exception) {
            // The JSON string is not valid
            note?.note
        }

        return  valueNote.toString()
    }

    fun getTitleValue(note: Note?) : String{
        var valueNote = ""
        valueNote = try {
            val jsonObject = JSONObject(note?.note.toString())
            val value = Gson().fromJson(note?.title, Note::class.java)
            // The JSON string is valid
            value.note.toString()

        } catch (e: Exception) {
            // The JSON string is not valid
            "No title"
        }

        return  valueNote
    }

    fun setupBanner(mAdView: AdView){
        try {
            val adRequest = AdRequest.Builder().build()
            mAdView.adListener = object : AdListener() {
                override fun onAdLoaded() {
                    Log.d("AdMob", "Ad loaded successfully")
                }

                override fun onAdFailedToLoad(p0: LoadAdError) {
                    Log.d("AdMob", "Ad failed to load:"+ p0.message + "id = "+getDataSession().getBannerId())
                }

                override fun onAdOpened() {
                    Log.d("AdMob", "Ad opened")
                }

                override fun onAdClicked() {
                    Log.d("AdMob", "Ad clicked")
                }

                override fun onAdClosed() {
                    Log.d("AdMob", "Ad closed")
                }
            }

            mAdView.loadAd(adRequest)
        }catch (e : Exception){
            setLog(e.message.toString())

        }
    }

    private fun permissionNotification(){
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    // Pass any permission you want while launching
                    requestPermissionNotification.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }catch (e : Exception){
            setLog(e.message.toString())
        }

    }

    private val requestPermissionNotification = registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }


    fun setupInterstitial() {
        if (getDataSession().getFanEnable()) {
            setupInterstitialFacebook()
        }
        try {
            val adRequest = AdRequest.Builder().build()
            adRequest.let {
                InterstitialAd.load(requireContext(), getDataSession().getInterstitialId(), it,
                    object : InterstitialAdLoadCallback() {
                        override fun onAdLoaded(interstitialAd: InterstitialAd) {
                            mInterstitialAd = interstitialAd
                            isLoad = true
                            setLog("AdMob Inters Loaded Success")

                            // Set the FullScreenContentCallback
                            mInterstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                                override fun onAdDismissedFullScreenContent() {
                                    if(BuildConfig.DEBUG){
                                        setToast(activity,"ads closed")
                                    }
                                    mInterstitialAd = null
                                    setupInterstitial()
                                }

                                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                                    // Handle the ad failed to show event
                                    setLog("AdMob Inters Ad Failed to Show: ${adError.message}")
                                }

                                override fun onAdShowedFullScreenContent() {
                                    if(BuildConfig.DEBUG){
                                        setToast(activity,"ads showed")
                                    }
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
            setLog("asywalul interstitial : "+e.message.toString())
        }
    }


    fun releaseInterstitialAdmob(){
        try {
            mInterstitialAd?.fullScreenContentCallback = null
            mInterstitialAd = null
        }catch (e : Exception){
            setLog(e.message)
        }
    }

    fun releaseInterstitialFAN(){
        try {
            interstitialFANAd = null
        }catch (e : Exception){
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

    /*fun setupInterstitial() {
        if(getDataSession().getFanEnable()){
            setupInterstitialFacebook()
        }
        try {
            val adRequest = AdRequest.Builder().build()
            adRequest.let {
                InterstitialAd.load(requireContext(), getDataSession().getInterstitialId(), it,
                    object : InterstitialAdLoadCallback() {
                        override fun onAdLoaded(interstitialAd: InterstitialAd) {
                            mInterstitialAd = interstitialAd
                            isLoad = true
                            setLog("AdMob Inters Loaded Success")
                        }

                        override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                            mInterstitialAd = null
                            setLog("AdMob Inters Loaded Failed id = "+ getDataSession().getInterstitialId() + "--->"+ loadAdError.message)
                        }


                    })
            }
        }catch (e : Exception){
            setLog(e.message.toString())
        }

    }
*/
    fun releaseInterstitial(){
        mInterstitialAd = null
        interstitialFANAd =null
    }


    fun setupRewardInterstitial(){
        try {
            RewardedInterstitialAd.load(requireContext(), DataSession(requireContext()).getRewardInterstitialId(),
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
                    ad.show(requireActivity()) { rewardItem ->
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
            RewardedAd.load(requireActivity(),DataSession(requireContext()).getRewardId(), adRequest, object : RewardedAdLoadCallback() {
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
                    ad.show(requireActivity()) { rewardItem ->
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
   /* protected open fun getFirebaseToken(): String? {
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
    }*/


    /*fun showInterstitial(){
        try {
            if(isLoad){
                mInterstitialAd?.show(requireActivity())
            }else{
                if(showFANInterstitial){
                    interstitialFANAd?.show()
                }else{
                    if(getDataSession().getStarAppEnable()){
                        if(getDataSession().getStarAppShowInterstitial()){
                            StartAppAd.showAd(activity);
                        }
                    }
                }
            }
        }catch (e : Exception){
            setLog(e.message.toString())
        }
    }*/

    fun showInterstitial(){
        try {
            Log.d("showInters","execute")
            if(isLoad){
                Log.d("showIntersAdmob","true")
                mInterstitialAd?.show(requireActivity())
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

    protected fun showReward(){
        try {
            if(isLoadReward){
                rewardedAd?.let { ad ->
                    ad.show(requireActivity()) { rewardItem ->
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


    fun setToastError(activity: Activity?, message : String){
        try {
            if(activity!=null){
                Toastic.toastic(activity,
                    message = "Error : $message",
                    duration = Toastic.LENGTH_SHORT,
                    type = Toastic.ERROR,
                    isIconAnimated = true
                ).show()
            }
        }catch (e : Exception){
            setLog(e.message.toString())
        }
    }

    fun setToast(activity: Activity?, message : String){
        try {
            if(activity!=null){
                Toast.makeText(activity, "$message.",Toast.LENGTH_SHORT).show()
            }
        }catch (e : Exception){
            setLog(e.message.toString())
        }

    }

    fun setToastWarning(activity: Activity?, message : String){
        try {
            if(activity!=null){
                Toastic.toastic(activity,
                    message = "$message.",
                    duration = Toastic.LENGTH_SHORT,
                    type = Toastic.WARNING,
                    isIconAnimated = true
                ).show()
            }
        }catch (e : Exception){
            setLog(e.message.toString())
        }
    }

    fun setToastSuccess(activity: Activity?, message : String){
        try {
            if(activity!=null){
                Toastic.toastic(
                    activity,
                    message = "$message.",
                    duration = Toastic.LENGTH_SHORT,
                    type = Toastic.SUCCESS,
                    isIconAnimated = true
                ).show()
            }
        }catch (e : Exception){
            setLog(e.message.toString())
        }
    }

    fun setToastInfo(activity: Activity?, message : String){
        try {
            if(activity!=null){
                Toastic.toastic(activity,
                    message = "$message.",
                    duration = Toastic.LENGTH_SHORT,
                    type = Toastic.INFO,
                    isIconAnimated = true
                ).show()
            }
        }catch (e : Exception){
            setLog(e.message.toString())
        }
    }


    fun setToastInfo(message : String){
        try {
            Toastic.toastic(
                context = requireContext(),
                message = "$message.",
                duration = Toastic.LENGTH_SHORT,
                type = Toastic.INFO,
                isIconAnimated = true
            ).show()
        }catch (e : Exception){
            setLog(e.message.toString())
        }
    }

    fun showAllowPermission(){
        try {
            setToastInfo(activity,requireActivity().getString(R.string.allow_permission))
            openSettings(activity)
        }catch (e : Exception){
            setLog(e.message)
        }

    }


    fun setLog(message : String? =null){
        Log.d("response ", "$message.")
    }

    protected fun simpleAnimation(view: View , drawable:Int? = null) {
        try {
            var icon  = R.drawable.star_pink
            if(drawable!=null){
                icon = drawable
            }
            ParticleSystem(activity, 100, icon, 800)
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
            val ps = ParticleSystem(activity, 100, icon, 800)
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
            ParticleSystem(activity, 10, icon, 3000)
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


    open fun rating(){
        val appPackageName = requireContext().packageName

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


    fun hideKeyboard(view: View) {
        try {
            val imm = activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
            imm?.hideSoftInputFromWindow(view.windowToken, 0)
        }catch (e : Exception){
            setLog(e.message.toString())
        }

    }

}
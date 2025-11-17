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
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
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
import android.view.WindowManager
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
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.core.view.ViewCompat
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
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.gson.Gson
import com.skydoves.balloon.ArrowPositionRules
import com.skydoves.balloon.Balloon
import com.skydoves.balloon.BalloonAnimation
import com.skydoves.balloon.overlay.BalloonOverlayRoundRect
import org.json.JSONObject
import sound.recorder.widget.R
import sound.recorder.widget.RecordingSDK
import sound.recorder.widget.notes.Note
import sound.recorder.widget.util.DataSession
import sound.recorder.widget.util.Toastic
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sound.recorder.widget.BuildConfig
import sound.recorder.widget.ads.AdConfigProvider
import sound.recorder.widget.animation.ParticleSystem
import sound.recorder.widget.animation.modifiers.ScaleModifier
import sound.recorder.widget.builder.AdmobSDKBuilder
import sound.recorder.widget.builder.FanSDKBuilder
import sound.recorder.widget.builder.UnitySDKBuilder
import sound.recorder.widget.builder.ZaifSDKBuilder
import sound.recorder.widget.databinding.WidgetRecordHorizontalZaifBinding
import sound.recorder.widget.databinding.WidgetRecordVerticalZaifBinding
import sound.recorder.widget.tools.showcase.GuideView
import java.util.concurrent.TimeUnit
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

   // private val isMobileAdsInitializeCalled = AtomicBoolean(false)
  //  private val initialLayoutComplete = AtomicBoolean(false)
    private var adView: AdView? =null
  //  private lateinit var googleMobileAdsConsentManager: GoogleMobileAdsConsentManager
  //  private lateinit var consentInformation: ConsentInformation
    private var TAG = "GDPR_App"

    private var isPrivacyOptionsRequired: Boolean = false
    private var showFANInterstitial = false

    private lateinit var appUpdateManager: AppUpdateManager       // in app update
    private val updateType = AppUpdateType.FLEXIBLE
    var sharedPreferences : SharedPreferences? =null
    var fileName =  ""
    var dirPath = ""
    val LOG_TAG = "AudioRecordTest"

    var mGuideView: GuideView? = null
    var builder: GuideView.Builder? = null
    var mPanAnim: Animation? = null
    //var admobSDKBuilder : AdmobSDKBuilder? =null
    private var adViewFacebook : com.facebook.ads.AdView? = null
   // var fanSDKBuilder : FanSDKBuilder? =null
    var zaifSDKBuilder : ZaifSDKBuilder? =null
    lateinit var dataSession : DataSession


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


    var recorder: MediaRecorder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        zaifSDKBuilder = ZaifSDKBuilder.builder(requireContext()).loadFromSharedPreferences()

        // Run in background thread
       /* CoroutineScope(Dispatchers.IO).launch {
            val admob = AdmobSDKBuilder.builder(requireContext()).loadFromSharedPreferences()
            val fan   = FanSDKBuilder.builder(requireContext()).loadFromSharedPreferences()

            withContext(Dispatchers.Main) {
                admobSDKBuilder = admob
                fanSDKBuilder = fan
            }
        }*/
    }


   /* @SuppressLint("UseKtx")
    fun setupWidget(builder : ZaifSDKBuilder?, binding : WidgetRecordHorizontalZaifBinding){
        try {
            builder?.backgroundWidgetColor?.let { colorString ->
                if (colorString.isNotEmpty()) {
                    try {
                        val tintList = ColorStateList.valueOf(Color.parseColor(colorString))
                        ViewCompat.setBackgroundTintList(binding.llBackground, tintList)
                    } catch (e: IllegalArgumentException) {
                        setToast("Invalid color value: $colorString")
                    }
                }
            }
        }catch (e : Exception){
            //
        }

        binding.ivChangeColor.setOnClickListener {
            activity?.let {
                try {
                    RecordingSDK.showDialogColorPicker(it)
                   // RecordingSDK.changeColor(it)
                } catch (e: Exception) {
                    setToast(e.message.toString())
                }
            } ?: setToast("Activity is not available")
        }

        try {
            binding.ivNote.visibility = if (builder?.showNote==true) View.VISIBLE else View.GONE
            binding.ivChangeColor.visibility = if (builder?.showChangeColor==true) View.VISIBLE else View.GONE
            binding.ivSong.visibility = if (builder?.showListSong==true) View.VISIBLE else View.GONE
            binding.ivVolume.visibility = if (builder?.showVolume==true) View.VISIBLE else View.GONE
        }catch (e : Exception){
            //
        }
    }*/


    @SuppressLint("UseKtx")
    fun setupWidget(builder : ZaifSDKBuilder?, binding : WidgetRecordHorizontalZaifBinding?){
        try {
            builder?.backgroundWidgetColor?.let { colorString ->
                if (colorString.isNotEmpty()) {
                    try {
                        val tintList = ColorStateList.valueOf(Color.parseColor(colorString))
                        binding?.llBackground?.post {
                            ViewCompat.setBackgroundTintList(binding.llBackground, tintList)
                        }
                    } catch (e: IllegalArgumentException) {
                        setToast("Invalid color value: $colorString")
                    }
                }
            }
        }catch (e : Exception){
            //
        }

        binding?.ivChangeColor?.setOnClickListener {
            activity?.let {
                try {
                    RecordingSDK.showDialogColorPicker(it)
                } catch (e: Exception) {
                    setToast(e.message.toString())
                }
            } ?: setToast("Activity is not available")
        }

        try {
            binding?.ivNote?.visibility = if (builder?.showNote==true) View.VISIBLE else View.GONE
            binding?.ivChangeColor?.visibility = if (builder?.showChangeColor==true) View.VISIBLE else View.GONE
            binding?.ivSong?.visibility = if (builder?.showListSong==true) View.VISIBLE else View.GONE
            binding?.ivVolume?.visibility = if (builder?.showVolume==true) View.VISIBLE else View.GONE
        }catch (e : Exception){
            //
        }
    }


    @SuppressLint("UseKtx")
    fun setupWidgetVetical(builder : ZaifSDKBuilder?, binding : WidgetRecordVerticalZaifBinding){
        try {
            builder?.backgroundWidgetColor?.let { colorString ->
                if (colorString.isNotEmpty()) {
                    try {
                        val tintList = ColorStateList.valueOf(Color.parseColor(colorString))
                        ViewCompat.setBackgroundTintList(binding.llBackground, tintList)
                    } catch (e: IllegalArgumentException) {
                        setToast("Invalid color value: $colorString")
                    }
                }
            }
        }catch (e : Exception){
            //
        }

        binding.ivChangeColor.setOnClickListener {
            activity?.let {
                try {
                    RecordingSDK.showDialogColorPicker(it)
                    // RecordingSDK.changeColor(it)
                } catch (e: Exception) {
                    setToast(e.message.toString())
                }
            } ?: setToast("Activity is not available")
        }

        try {
            binding.ivNote.visibility = if (builder?.showNote==true) View.VISIBLE else View.GONE
            binding.ivChangeColor.visibility = if (builder?.showChangeColor==true) View.VISIBLE else View.GONE
            binding.ivSong.visibility = if (builder?.showListSong==true) View.VISIBLE else View.GONE
            binding.ivVolume.visibility = if (builder?.showVolume==true) View.VISIBLE else View.GONE
        }catch (e : Exception){
            //
        }
    }

    @SuppressLint("UseKtx")
    fun setupWidgetVeticalNull(builder : ZaifSDKBuilder?, binding : WidgetRecordVerticalZaifBinding?){
        try {
            builder?.backgroundWidgetColor?.let { colorString ->
                if (colorString.isNotEmpty()) {
                    try {
                        val tintList = ColorStateList.valueOf(Color.parseColor(colorString))
                        binding?.llBackground?.post {
                            ViewCompat.setBackgroundTintList(binding.llBackground, tintList)
                        }
                    } catch (e: IllegalArgumentException) {
                        setToast("Invalid color value: $colorString")
                    }
                }
            }
        }catch (e : Exception){
            //
        }

        binding?.ivChangeColor?.setOnClickListener {
            activity?.let {
                try {
                    RecordingSDK.showDialogColorPicker(it)
                } catch (e: Exception) {
                    setToast(e.message.toString())
                }
            } ?: setToast("Activity is not available")
        }

        try {
            binding?.ivNote?.visibility = if (builder?.showNote==true) View.VISIBLE else View.GONE
            binding?.ivChangeColor?.visibility = if (builder?.showChangeColor==true) View.VISIBLE else View.GONE
            binding?.ivSong?.visibility = if (builder?.showListSong==true) View.VISIBLE else View.GONE
            binding?.ivVolume?.visibility = if (builder?.showVolume==true) View.VISIBLE else View.GONE
        }catch (e : Exception){
            //
        }
    }


    fun permissionSong(){
        try {
            if(Build.VERSION.SDK_INT >=Build.VERSION_CODES.TIRAMISU){
                if (ContextCompat.checkSelfPermission(requireActivity(), Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED) {

                } else {
                    requestPermissionSong.launch(Manifest.permission.READ_MEDIA_AUDIO)
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (ContextCompat.checkSelfPermission(requireActivity(), Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissionSong.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }

        }catch (e : Exception){
            print(e.message)
        }

    }

    private val requestPermissionSong =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->

        }

    fun createBalloonWithText(
        title: String,
        message: String,
        isLast: Boolean = false
    ): Balloon {
        val balloon = Balloon.Builder(requireContext())
            .setLayout(R.layout.tooltip_layout)
            .setArrowSize(10)
            .setCornerRadius(8f)
            .setArrowPositionRules(ArrowPositionRules.ALIGN_ANCHOR)
            .setPadding(8)
            .setOverlayShape(BalloonOverlayRoundRect(com.intuit.sdp.R.dimen._5sdp, com.intuit.sdp.R.dimen._5sdp))


            // ❌ Ini mengatur background tooltip-nya saja
            .setBackgroundColorResource(R.color.yellow_50) // Ganti dengan putih atau warna solid untuk tooltip

            // ✅ Tambahkan overlay luar
            .setIsVisibleOverlay(true)
            .setOverlayColorResource(R.color.tooltip_overlay_dark) // <== ini yang bikin luar gelap

            // ✅ Supaya tidak bisa klik luar
            .setDismissWhenTouchOutside(false)
            .setDismissWhenOverlayClicked(false)
            .setDismissWhenTouchMargin(false)
            .setFocusable(false)

            .setBalloonAnimation(BalloonAnimation.FADE)
            .build()

        val layout = balloon.getContentView()
        layout.findViewById<TextView>(R.id.tvTitle).text = title
        layout.findViewById<TextView>(R.id.tvMessage).text = message
        val btn = layout.findViewById<Button>(R.id.btnNext)
        val btnSkip = layout.findViewById<TextView>(R.id.btnSkip)
        btn.text = if (isLast){
            requireContext().getString(R.string.text_done)
        } else{
            requireContext().getString(R.string.next)
        }

        btnSkip.visibility =  if (isLast){
            View.GONE
        } else{
            View.VISIBLE
        }

        return balloon
    }



    fun setStatusBarColor(color : Int){
        try {
            requireActivity().window.apply {
                addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
                statusBarColor = ContextCompat.getColor(requireContext(), color)
            }
        } catch (e: Exception) {
            setLog("not support")
        }
    }

    fun setBottomStatusColor(color : Int){
        try {
            requireActivity().window?.navigationBarColor = ContextCompat.getColor(requireContext(), color)
        } catch (e: Exception) {
            setLog("not support")
        }

    }

    /*fun initFANSDK(){
        if(getDataSession().getFanEnable()){
            AudienceNetworkAds.initialize(requireContext());
        }

    }
*/

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

/*
    @SuppressLint("CutPasteId")
    fun showTooltipSequence(binding : WidgetRecordHorizontalZaifBinding) {

        try {
            zaifSDKBuilder?.let { builder ->

                val balloonNote       = createBalloonWithText(requireContext().getString(R.string.note_song), getString(R.string.tooltip_note))
                val balloonSong       = createBalloonWithText(requireContext().getString(R.string.choose_song), getString(R.string.tooltip_music))

                // Gunakan 'var' karena nilainya mungkin akan diubah berdasarkan kondisi
                var balloonRecording  = createBalloonWithText(
                    requireContext().getString(R.string.recordings),
                    getString(R.string.tooltip_record)
                )
                var balloonListRecord = createBalloonWithText(
                    requireContext().getString(R.string.recorded_saved),
                    getString(R.string.tooltip_saved_record)
                )
                var balloonVolume     = createBalloonWithText(
                    requireContext().getString(R.string.volume),
                    getString(R.string.tooltip_volume)
                )
                var balloonColor      = createBalloonWithText(
                    requireContext().getString(R.string.choose_color),
                    getString(R.string.tooltip_color),
                    true
                )

// Tampilkan balloon tertentu langsung berdasarkan kondisi
                if (!builder.showVolume && !builder.showChangeColor) {
                    balloonListRecord = createBalloonWithText(
                        requireContext().getString(R.string.recorded_saved),
                        getString(R.string.tooltip_saved_record),
                        true
                    )
                } else if (builder.showVolume && !builder.showChangeColor) {
                    balloonVolume = createBalloonWithText(
                        requireContext().getString(R.string.volume),
                        getString(R.string.tooltip_volume),
                        true
                    )
                }


                balloonNote.getContentView().findViewById<TextView>(R.id.btnNext).setOnClickListener {
                    balloonNote.dismiss()
                    binding.ivNote.post {
                        balloonSong.showAlignBottom(binding.ivSong)
                    }
                }

                balloonNote.getContentView().findViewById<TextView>(R.id.btnSkip).setOnClickListener {
                    balloonNote.dismiss()
                    dataSession.saveTooltip(true)
                }

                balloonSong.getContentView().findViewById<TextView>(R.id.btnNext).setOnClickListener {
                    balloonSong.dismiss()
                    binding.ivSong.post {
                        balloonRecording.showAlignBottom(binding.ivRecord)
                    }
                }

                balloonSong.getContentView().findViewById<TextView>(R.id.btnSkip).setOnClickListener {
                    balloonSong.dismiss()
                    dataSession.saveTooltip(true)
                }

                balloonRecording.getContentView().findViewById<TextView>(R.id.btnNext).setOnClickListener {
                    balloonRecording.dismiss()
                    binding.ivRecord.post {
                        balloonListRecord.showAlignBottom(binding.ivListRecord)
                    }
                }

                balloonRecording.getContentView().findViewById<TextView>(R.id.btnSkip).setOnClickListener {
                    balloonRecording.dismiss()
                    dataSession.saveTooltip(true)
                }

                balloonListRecord.getContentView().findViewById<TextView>(R.id.btnNext).setOnClickListener {
                    balloonListRecord.dismiss()

                    if(builder.showVolume){
                        binding.ivListRecord.post {
                            balloonVolume.showAlignBottom(binding.ivVolume)
                        }
                    }else{
                        if(builder.showChangeColor){
                            balloonColor.showAlignBottom(binding.ivChangeColor)
                        }else{
                            dataSession.saveTooltip(true)
                        }
                    }
                }

                balloonListRecord.getContentView().findViewById<TextView>(R.id.btnSkip).setOnClickListener {
                    balloonListRecord.dismiss()
                    dataSession.saveTooltip(true)
                }

                balloonVolume.getContentView().findViewById<TextView>(R.id.btnNext).setOnClickListener {
                    balloonVolume.dismiss()
                    if(builder.showChangeColor){
                        binding.ivVolume.post {
                            balloonColor.showAlignBottom(binding.ivChangeColor)
                        }
                    }else{
                        dataSession.saveTooltip(true)
                    }
                }

                balloonVolume.getContentView().findViewById<TextView>(R.id.btnSkip).setOnClickListener {
                    balloonVolume.dismiss()
                    dataSession.saveTooltip(true)
                }

                balloonColor.getContentView().findViewById<TextView>(R.id.btnNext).setOnClickListener {
                    balloonColor.dismiss()
                    dataSession.saveTooltip(true)
                    //save session
                }


                if (builder.showNote){
                    binding.ivNote.post {
                        balloonNote.showAlignBottom(binding.ivNote)
                    }
                }else{
                    binding.ivSong.post {
                        balloonSong.showAlignBottom(binding.ivSong)
                    }
                }
            } ?: run {
                // Optional: Log or handle the case where zaifSDKBuilder is null
                setLog("zaifSDKBuilder is null, menu items not updated")
            }
        }catch (e : Exception){
        }

    }*/

    @SuppressLint("CutPasteId")
    fun showTooltipSequence(binding : WidgetRecordHorizontalZaifBinding?) {

        try {
            zaifSDKBuilder?.let { builder ->

                val balloonNote       = createBalloonWithText(requireContext().getString(R.string.note_song), getString(R.string.tooltip_note))
                val balloonSong       = createBalloonWithText(requireContext().getString(R.string.choose_song), getString(R.string.tooltip_music))

                // Gunakan 'var' karena nilainya mungkin akan diubah berdasarkan kondisi
                var balloonRecording  = createBalloonWithText(
                    requireContext().getString(R.string.recordings),
                    getString(R.string.tooltip_record)
                )
                var balloonListRecord = createBalloonWithText(
                    requireContext().getString(R.string.recorded_saved),
                    getString(R.string.tooltip_saved_record)
                )
                var balloonVolume     = createBalloonWithText(
                    requireContext().getString(R.string.volume),
                    getString(R.string.tooltip_volume)
                )
                var balloonColor      = createBalloonWithText(
                    requireContext().getString(R.string.choose_color),
                    getString(R.string.tooltip_color),
                    true
                )

                if (!builder.showVolume && !builder.showChangeColor) {
                    balloonListRecord = createBalloonWithText(
                        requireContext().getString(R.string.recorded_saved),
                        getString(R.string.tooltip_saved_record),
                        true
                    )
                } else if (builder.showVolume && !builder.showChangeColor) {
                    balloonVolume = createBalloonWithText(
                        requireContext().getString(R.string.volume),
                        getString(R.string.tooltip_volume),
                        true
                    )
                }


                balloonNote.getContentView().findViewById<TextView>(R.id.btnNext).setOnClickListener {
                    balloonNote.dismiss()
                    binding?.ivNote?.post {
                        balloonSong.showAlignBottom(binding.ivSong)
                    }
                }

                balloonNote.getContentView().findViewById<TextView>(R.id.btnSkip).setOnClickListener {
                    balloonNote.dismiss()
                    dataSession.saveTooltip(true)
                }

                balloonSong.getContentView().findViewById<TextView>(R.id.btnNext).setOnClickListener {
                    balloonSong.dismiss()
                    binding?.ivSong?.post {
                        balloonRecording.showAlignBottom(binding.ivRecord)
                    }
                }

                balloonSong.getContentView().findViewById<TextView>(R.id.btnSkip).setOnClickListener {
                    balloonSong.dismiss()
                    dataSession.saveTooltip(true)
                }

                balloonRecording.getContentView().findViewById<TextView>(R.id.btnNext).setOnClickListener {
                    balloonRecording.dismiss()
                    binding?.ivRecord?.post {
                        balloonListRecord.showAlignBottom(binding.ivListRecord)
                    }
                }

                balloonRecording.getContentView().findViewById<TextView>(R.id.btnSkip).setOnClickListener {
                    balloonRecording.dismiss()
                    dataSession.saveTooltip(true)
                }

                balloonListRecord.getContentView().findViewById<TextView>(R.id.btnNext).setOnClickListener {
                    balloonListRecord.dismiss()

                    if(builder.showVolume){
                        binding?.ivListRecord?.post {
                            balloonVolume.showAlignBottom(binding.ivVolume)
                        }
                    }else{
                        if(builder.showChangeColor){
                            binding?.ivChangeColor?.post {
                                balloonColor.showAlignBottom(binding.ivChangeColor)
                            }
                        }else{
                            dataSession.saveTooltip(true)
                        }
                    }
                }

                balloonListRecord.getContentView().findViewById<TextView>(R.id.btnSkip).setOnClickListener {
                    balloonListRecord.dismiss()
                    dataSession.saveTooltip(true)
                }

                balloonVolume.getContentView().findViewById<TextView>(R.id.btnNext).setOnClickListener {
                    balloonVolume.dismiss()
                    if(builder.showChangeColor){
                        binding?.ivVolume?.post {
                            balloonColor.showAlignBottom(binding.ivChangeColor)
                        }
                    }else{
                        dataSession.saveTooltip(true)
                    }
                }

                balloonVolume.getContentView().findViewById<TextView>(R.id.btnSkip).setOnClickListener {
                    balloonVolume.dismiss()
                    dataSession.saveTooltip(true)
                }

                balloonColor.getContentView().findViewById<TextView>(R.id.btnNext).setOnClickListener {
                    balloonColor.dismiss()
                    dataSession.saveTooltip(true)
                    //save session
                }


                if (builder.showNote){
                    binding?.ivNote?.post {
                        balloonNote.showAlignBottom(binding.ivNote)
                    }
                }else{
                    binding?.ivSong?.post {
                        balloonSong.showAlignBottom(binding.ivSong)
                    }
                }
            } ?: run {
                // Optional: Log or handle the case where zaifSDKBuilder is null
                setLog("zaifSDKBuilder is null, menu items not updated")
            }
        }catch (e : Exception){
        }

    }



    @SuppressLint("CutPasteId")
    fun showTooltipSequenceVetical(binding : WidgetRecordVerticalZaifBinding) {

        try {
            zaifSDKBuilder?.let { builder ->

                val balloonNote       = createBalloonWithText(requireContext().getString(R.string.note_song), getString(R.string.tooltip_note))
                val balloonSong       = createBalloonWithText(requireContext().getString(R.string.choose_song), getString(R.string.tooltip_music))

                // Gunakan 'var' karena nilainya mungkin akan diubah berdasarkan kondisi
                var balloonRecording  = createBalloonWithText(
                    requireContext().getString(R.string.recordings),
                    getString(R.string.tooltip_record)
                )
                var balloonListRecord = createBalloonWithText(
                    requireContext().getString(R.string.recorded_saved),
                    getString(R.string.tooltip_saved_record)
                )
                var balloonVolume     = createBalloonWithText(
                    requireContext().getString(R.string.volume),
                    getString(R.string.tooltip_volume)
                )
                var balloonColor      = createBalloonWithText(
                    requireContext().getString(R.string.choose_color),
                    getString(R.string.tooltip_color),
                    true
                )

// Tampilkan balloon tertentu langsung berdasarkan kondisi
                if (!builder.showVolume && !builder.showChangeColor) {
                    balloonListRecord = createBalloonWithText(
                        requireContext().getString(R.string.recorded_saved),
                        getString(R.string.tooltip_saved_record),
                        true
                    )
                } else if (builder.showVolume && !builder.showChangeColor) {
                    balloonVolume = createBalloonWithText(
                        requireContext().getString(R.string.volume),
                        getString(R.string.tooltip_volume),
                        true
                    )
                }


                balloonNote.getContentView().findViewById<TextView>(R.id.btnNext).setOnClickListener {
                    balloonNote.dismiss()
                    binding.ivNote.post {
                        balloonSong.showAlignBottom(binding.ivSong)
                    }
                }

                balloonNote.getContentView().findViewById<TextView>(R.id.btnSkip).setOnClickListener {
                    balloonNote.dismiss()
                    dataSession.saveTooltip(true)
                }

                balloonSong.getContentView().findViewById<TextView>(R.id.btnNext).setOnClickListener {
                    balloonSong.dismiss()
                    binding.ivSong.post {
                        balloonRecording.showAlignBottom(binding.ivRecord)
                    }
                }

                balloonSong.getContentView().findViewById<TextView>(R.id.btnSkip).setOnClickListener {
                    balloonSong.dismiss()
                    dataSession.saveTooltip(true)
                }

                balloonRecording.getContentView().findViewById<TextView>(R.id.btnNext).setOnClickListener {
                    balloonRecording.dismiss()
                    binding.ivRecord.post {
                        balloonListRecord.showAlignBottom(binding.ivListRecord)
                    }
                }

                balloonRecording.getContentView().findViewById<TextView>(R.id.btnSkip).setOnClickListener {
                    balloonRecording.dismiss()
                    dataSession.saveTooltip(true)
                }

                balloonListRecord.getContentView().findViewById<TextView>(R.id.btnNext).setOnClickListener {
                    balloonListRecord.dismiss()

                    if(builder.showVolume){
                        binding.ivListRecord.post {
                            balloonVolume.showAlignBottom(binding.ivVolume)
                        }
                    }else{
                        if(builder.showChangeColor){
                            balloonColor.showAlignBottom(binding.ivChangeColor)
                        }else{
                            dataSession.saveTooltip(true)
                        }
                    }
                }

                balloonListRecord.getContentView().findViewById<TextView>(R.id.btnSkip).setOnClickListener {
                    balloonListRecord.dismiss()
                    dataSession.saveTooltip(true)
                }

                balloonVolume.getContentView().findViewById<TextView>(R.id.btnNext).setOnClickListener {
                    balloonVolume.dismiss()
                    if(builder.showChangeColor){
                        binding.ivVolume.post {
                            balloonColor.showAlignBottom(binding.ivChangeColor)
                        }
                    }else{
                        dataSession.saveTooltip(true)
                    }
                }

                balloonVolume.getContentView().findViewById<TextView>(R.id.btnSkip).setOnClickListener {
                    balloonVolume.dismiss()
                    dataSession.saveTooltip(true)
                }

                balloonColor.getContentView().findViewById<TextView>(R.id.btnNext).setOnClickListener {
                    balloonColor.dismiss()
                    dataSession.saveTooltip(true)
                    //save session
                }


                if (builder.showNote){
                    binding.ivNote.post {
                        balloonNote.showAlignBottom(binding.ivNote)
                    }
                }else{
                    binding.ivSong.post {
                        balloonSong.showAlignBottom(binding.ivSong)
                    }
                }
            } ?: run {
                // Optional: Log or handle the case where zaifSDKBuilder is null
                setLog("zaifSDKBuilder is null, menu items not updated")
            }
        }catch (e : Exception){
        }

    }

    @SuppressLint("CutPasteId")
    fun showTooltipSequenceVeticalNull(binding : WidgetRecordVerticalZaifBinding?) {

        try {
            zaifSDKBuilder?.let { builder ->

                val balloonNote       = createBalloonWithText(requireContext().getString(R.string.note_song), getString(R.string.tooltip_note))
                val balloonSong       = createBalloonWithText(requireContext().getString(R.string.choose_song), getString(R.string.tooltip_music))

                // Gunakan 'var' karena nilainya mungkin akan diubah berdasarkan kondisi
                var balloonRecording  = createBalloonWithText(
                    requireContext().getString(R.string.recordings),
                    getString(R.string.tooltip_record)
                )
                var balloonListRecord = createBalloonWithText(
                    requireContext().getString(R.string.recorded_saved),
                    getString(R.string.tooltip_saved_record)
                )
                var balloonVolume     = createBalloonWithText(
                    requireContext().getString(R.string.volume),
                    getString(R.string.tooltip_volume)
                )
                var balloonColor      = createBalloonWithText(
                    requireContext().getString(R.string.choose_color),
                    getString(R.string.tooltip_color),
                    true
                )

// Tampilkan balloon tertentu langsung berdasarkan kondisi
                if (!builder.showVolume && !builder.showChangeColor) {
                    balloonListRecord = createBalloonWithText(
                        requireContext().getString(R.string.recorded_saved),
                        getString(R.string.tooltip_saved_record),
                        true
                    )
                } else if (builder.showVolume && !builder.showChangeColor) {
                    balloonVolume = createBalloonWithText(
                        requireContext().getString(R.string.volume),
                        getString(R.string.tooltip_volume),
                        true
                    )
                }


                balloonNote.getContentView().findViewById<TextView>(R.id.btnNext).setOnClickListener {
                    balloonNote.dismiss()
                    binding?.ivNote?.post {
                        balloonSong.showAlignBottom(binding.ivSong)
                    }
                }

                balloonNote.getContentView().findViewById<TextView>(R.id.btnSkip).setOnClickListener {
                    balloonNote.dismiss()
                    dataSession.saveTooltip(true)
                }

                balloonSong.getContentView().findViewById<TextView>(R.id.btnNext).setOnClickListener {
                    balloonSong.dismiss()
                    binding?.ivSong?.post {
                        balloonRecording.showAlignBottom(binding.ivRecord)
                    }
                }

                balloonSong.getContentView().findViewById<TextView>(R.id.btnSkip).setOnClickListener {
                    balloonSong.dismiss()
                    dataSession.saveTooltip(true)
                }

                balloonRecording.getContentView().findViewById<TextView>(R.id.btnNext).setOnClickListener {
                    balloonRecording.dismiss()
                    binding?.ivRecord?.post {
                        balloonListRecord.showAlignBottom(binding.ivListRecord)
                    }
                }

                balloonRecording.getContentView().findViewById<TextView>(R.id.btnSkip).setOnClickListener {
                    balloonRecording.dismiss()
                    dataSession.saveTooltip(true)
                }

                balloonListRecord.getContentView().findViewById<TextView>(R.id.btnNext).setOnClickListener {
                    balloonListRecord.dismiss()

                    if(builder.showVolume){
                        binding?.ivListRecord?.post {
                            balloonVolume.showAlignBottom(binding.ivVolume)
                        }
                    }else{
                        if(builder.showChangeColor){
                            binding?.ivChangeColor?.post {
                                balloonColor.showAlignBottom(binding.ivChangeColor)
                            }
                        }else{
                            dataSession.saveTooltip(true)
                        }
                    }
                }

                balloonListRecord.getContentView().findViewById<TextView>(R.id.btnSkip).setOnClickListener {
                    balloonListRecord.dismiss()
                    dataSession.saveTooltip(true)
                }

                balloonVolume.getContentView().findViewById<TextView>(R.id.btnNext).setOnClickListener {
                    balloonVolume.dismiss()
                    if(builder.showChangeColor){
                        binding?.ivVolume?.post {
                            balloonColor.showAlignBottom(binding.ivChangeColor)
                        }
                    }else{
                        dataSession.saveTooltip(true)
                    }
                }

                balloonVolume.getContentView().findViewById<TextView>(R.id.btnSkip).setOnClickListener {
                    balloonVolume.dismiss()
                    dataSession.saveTooltip(true)
                }

                balloonColor.getContentView().findViewById<TextView>(R.id.btnNext).setOnClickListener {
                    balloonColor.dismiss()
                    dataSession.saveTooltip(true)
                    //save session
                }


                if (builder.showNote){
                    binding?.ivNote?.post {
                        balloonNote.showAlignBottom(binding.ivNote)
                    }
                }else{
                    binding?.ivSong?.post {
                        balloonSong.showAlignBottom(binding.ivSong)
                    }
                }
            } ?: run {
                // Optional: Log or handle the case where zaifSDKBuilder is null
                setLog("zaifSDKBuilder is null, menu items not updated")
            }
        }catch (e : Exception){
        }

    }


    fun setupBannerFacebook(adContainer : FrameLayout?){
        try {
            val adListener = object : com.facebook.ads.AdListener {
                override fun onError(ad: Ad, adError: com.facebook.ads.AdError) {
                    if(BuildConfig.DEBUG){
                        setLog("Banner error loaded id = "+ ad.placementId +"---> "+ adError.errorMessage)
                    }
                }

                override fun onAdLoaded(ad: Ad) {
                    if(BuildConfig.DEBUG){
                        setLog("Banner Successfully Loaded id = "+ ad.placementId)
                    }
                }

                override fun onAdClicked(ad: Ad) {
                }
                override fun onLoggingImpression(ad: Ad) {
                }
            }

            val adView = com.facebook.ads.AdView(requireContext(), fanSDKBuilder?.bannerId.toString(), com.facebook.ads.AdSize.BANNER_HEIGHT_50)
            adView.loadAd(adView.buildLoadAdConfig().withAdListener(adListener).build())
            this.adViewFacebook = adView
            adContainer?.addView(adView)

        }catch (e : Exception){
            setLog(e.message.toString())
        }

    }

    fun sampleRate() : Int?{
        val audioManager = activity?.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val sampleRate = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toInt()// ?: 48000
        return sampleRate;
    }

    fun bufferSize() : Int?{
        val audioManager = activity?.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val bufferSize = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toInt()// ?: 256
        return bufferSize;
    }

    @SuppressLint("DefaultLocale")
    fun getFormattedAudioDuration(filePath: String): String {

        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(filePath)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()

            val durationMillis = durationStr?.toLong() ?: 0L

            // Mengonversi milidetik menjadi menit dan detik
            val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMillis)
            val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMillis) % 60

            return String.format("%02d:%02d", minutes, seconds)

        }catch (e : Exception){

            setLog(e.message.toString())
        }

        return ""
        // Format menjadi "mm:ss"
    }


    /*fun setupInterstitialFacebook(){
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
                        setToast("close FAN ads")
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
*/
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
                setToastTic(Toastic.INFO,getString(R.string.download_success))
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



    /*fun getDataSession() : DataSession{
        return DataSession(requireContext())
    }*/


    fun releaseBannerAdmob(){
        adView?.destroy()
        adView = null
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

    fun convertColorCodeToHex(colorCode: Int): String {
        return String.format("#%06X", colorCode)
    }

    @SuppressLint("UseKtx")
    fun setBottomStatusColor1(color : String){
        try {
            requireActivity().window?.navigationBarColor = Color.parseColor(color)
        } catch (e: Exception) {
            setLog("not support")
        }

    }

    /*private fun initializeMobileAdsSdk(adViewContainer: FrameLayout,bannerId: String? =null) {
        if (isMobileAdsInitializeCalled.getAndSet(true)) {
            return
        }

        // Initialize the Mobile Ads SDK.
        MobileAds.initialize(requireContext()) {}

        // Load an ad.
        if (initialLayoutComplete.get()) {
           // loadBanner(adViewContainer,bannerId)
        }
    }*/

   /* fun setupBannerNew(adViewContainer: FrameLayout,bannerId : String? =null){
        val adView = AdManagerAdView(requireContext())
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
                setupBanner(adViewContainer)
            }
        }

        // Set your test devices. Check your logcat output for the hashed device ID to
        // get test ads on a physical device. e.g.
        // "Use RequestConfiguration.Builder().setTestDeviceIds(Arrays.asList("ABCDEF012345"))
        // to get test ads on this device."
        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder().setTestDeviceIds(listOf("D48A46E523E6A96C8215178502423686")).build()
        )
    }*/

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
                //getDataSession().saveDefaultLanguage(selectedLanguage)
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
                setToastTic(Toastic.WARNING,activity?.getString(R.string.message_cannot_empty).toString())
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

    fun isLanguageIdEn(context: Context): Boolean {
        val deviceLanguage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.resources.configuration.locales[0].language
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale.language
        }

        return deviceLanguage == "id" || deviceLanguage == "en"|| deviceLanguage == "in"
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

    fun setupBanner(adViewContainer:FrameLayout?){
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

    private fun isAdMobAvailable(): Boolean {
        return try {
            Class.forName("com.google.android.gms.ads.MobileAds")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }


    private fun isWebViewAvailable(): Boolean {
        val packageManager = requireContext().packageManager
        return try {
            packageManager.getPackageInfo("com.google.android.webview", 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }


    private fun isWebViewSupported(): Boolean {
        return try {
            WebView(requireContext())
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



   /* private fun getSize(): AdSize {
        val widthPixels = resources.displayMetrics.widthPixels.toFloat()
        val density = resources.displayMetrics.density
        val adWidth = (widthPixels / density).toInt()

        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(requireContext(), adWidth)
    }*/

    private fun getSize(): AdSize {
        val widthPixels = resources.displayMetrics.widthPixels.toFloat()
        val density = resources.displayMetrics.density.takeIf { it > 0 } ?: 1f
        val adWidth = (widthPixels / density).toInt()

        val adaptiveSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(requireContext(), adWidth)
        val adHeight = adaptiveSize.height.coerceAtMost(60)

        return AdSize(adWidth, adHeight)
    }


    private fun executeBanner(adViewContainer:FrameLayout?){
        try {
            val adView = AdView(requireContext())
            adView.adUnitId = admobSDKBuilder?.bannerId.toString()
            adView.setAdSize(getSize())
            val adRequest = AdRequest.Builder().build()
            adView.adListener = object : AdListener() {
                override fun onAdLoaded() {
                    Log.d("ADS_AdMob", "banner loaded successfully "+adView.adUnitId)
                }
                override fun onAdFailedToLoad(p0: LoadAdError) {
                    Log.d("ADS_AdMob", "banner loaded failed "+p0.message)
                    if(fanSDKBuilder?.enable==true){
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
            adViewContainer?.removeAllViews()
            adViewContainer?.addView(adView)
            this.adView = adView

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

        if(isWebViewSupported()&&isWebViewAvailable()){

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
                            InterstitialAd.load(requireContext(), admobSDKBuilder?.interstitialId.toString(), adRequest,
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


    fun setupInterstitialFacebook(){

        if(isWebViewSupported()&&isWebViewAvailable()){
            try {
                interstitialFANAd = com.facebook.ads.InterstitialAd(requireContext(), fanSDKBuilder?.interstitialId.toString())
                val interstitialAdListener = object : InterstitialAdListener {
                    override fun onInterstitialDisplayed(ad: Ad) {
                        setLog("show Interstitial success "+ad.placementId)
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
                Toastic.toastic(activity.applicationContext,
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

    fun setToast(message : String){
        try {
            Toast.makeText(requireContext().applicationContext, "$message.",Toast.LENGTH_SHORT).show()
        }catch (e : Exception){
            setLog(e.message.toString())
        }

    }

    fun setToastTic(code : Int,message : String){
        try {
            if(activity!=null){
                Toastic.toastic(requireContext().applicationContext,
                    message = "$message.",
                    duration = Toastic.LENGTH_SHORT,
                    type = code,
                    isIconAnimated = true
                ).show()
            }
        }catch (e : Exception){
            setLog(e.message.toString())
        }
    }



    fun showAllowPermission(){
        try {
            setToastTic(Toastic.INFO,activity?.getString(R.string.allow_permission).toString())
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

    fun initAnim(ivStop : ImageView? =null) {
        try {
            mPanAnim = AnimationUtils.loadAnimation(activity, R.anim.rotate)
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



}
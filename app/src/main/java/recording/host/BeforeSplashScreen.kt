package recording.host

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import sound.recorder.widget.base.BaseActivityWidget
import sound.recorder.widget.builder.AdmobSDKBuilder
import sound.recorder.widget.builder.FanSDKBuilder
import sound.recorder.widget.builder.UnitySDKBuilder
import sound.recorder.widget.builder.ZaifSDKBuilder
import sound.recorder.widget.databinding.ActivitySplashSdkBinding
import sound.recorder.widget.util.Constant

@SuppressLint("CustomSplashScreen")
class BeforeSplashScreen : BaseActivityWidget(){

    private lateinit var binding: ActivitySplashSdkBinding
    private val handler = Handler(Looper.getMainLooper())
    private val goToNextPageRunnable = Runnable { goToNextPage() }
    private val appId = "balera.music.android"

    val admobBannerId           = "ca-app-pub-4503297165525769/8869314001" //correct
    val admobInterstitialId     = "ca-app-pub-4503297165525769/6993052482" //correct
    val admobId                 = "ca-app-pub-4503297165525769~2938330478" //correct
    val homeBanner               = "ca-app-pub-4503297165525769/8322544586" //correct
    val admobRewardInterstitialId = "ca-app-pub-4503297165525769/2856073839" //correct
    val admobRewardId             = "ca-app-pub-4503297165525769/2839761965" //correct
    val admobNativeId =""

    val unityGameId = "5278177"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashSdkBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Log.d("BeforeSplashScreen", "onCreate called")

    }

    @SuppressLint("SetTextI18n")
    private fun updateData() {
        try {
            Log.d("BeforeSplashScreen", "updateData called")
            // Initialize AdmobAdsBuilder based on the build type

            if (BuildConfig.DEBUG) {
                AdmobSDKBuilder.builder(this@BeforeSplashScreen)
                    .setAdmobId(admobId)
                    .setBannerId(Constant.AdsTesterId.admobBannerId)
                    .setInterstitialId(Constant.AdsTesterId.admobInterstitialId)
                    .setRewardId(Constant.AdsTesterId.admobRewardId)
                    .setRewardInterstitialId(Constant.AdsTesterId.admobRewardInterstitialId)
                    .setNativeId(Constant.AdsTesterId.admobNativeId)
                    .build()
            } else {
                AdmobSDKBuilder.builder(this@BeforeSplashScreen)
                    .setAdmobId(admobId)
                    .setBannerId(admobBannerId)
                    .setInterstitialId(admobInterstitialId)
                    .setRewardId(admobRewardId)
                    .setRewardInterstitialId(admobRewardInterstitialId)
                    .setNativeId("")
                    .build()
            }

            val fanId             = "6371696286185210"
            val fanBannerId       = "6371696286185210_7264663670221796"
            val fanInterstitialId = "6371696286185210_7264664310221732"


            // Initialize FanAdsBuilder
            FanSDKBuilder.builder(this)
                .setApplicationId(fanId)
                .setBannerId(fanBannerId)
                .setInterstitialId(fanInterstitialId)
                .setEnable(false)
                .build()


            if(BuildConfig.DEBUG){
                UnitySDKBuilder.builder(this)
                    .setUnityId(unityGameId)
                    .setEnable(true)
                    .build()
            }


            if(BuildConfig.DEBUG){

                setupUnityAds(unityGameId)
                UnitySDKBuilder.builder(this)
                    .setUnityId(unityGameId)
                    .setEnable(true)
                    .build()
            }



            ZaifSDKBuilder.builder(this)
                .setAppName(getString(R.string.app_name))
                .setVersionCode(BuildConfig.VERSION_CODE)
                .setVersionName(BuildConfig.VERSION_NAME)
                .setApplicationId(BuildConfig.APPLICATION_ID)
                .setBackgroundWidgetColor("#FF8A80")
                .showNote(true)
                .showChangeColor(true)
                .showListSong(true)
                .showVolume(true)
                .build()

            // Update UI elements
            binding.backgroundSplash.setBackgroundColor(Color.parseColor("#000000"))
            binding.tvTitle.text = getString(R.string.app_name) + "\n" + "v " + BuildConfig.VERSION_NAME

           // setupAppOpenAd()


            // Post delay to navigate to next page
            Log.d("BeforeSplashScreen", "Posting delay to go to next page")
            handler.postDelayed(goToNextPageRunnable, 3000)
        } catch (e: Exception) {
            setLog("Before Splash : " + e.message.toString())
            Log.e("BeforeSplashScreen", "Error in updateData: ${e.message}")
        }

    }

    override fun onResume() {
        super.onResume()
        updateData()
    }

    override fun onStop() {
        super.onStop()
        // Remove any pending callbacks to prevent them from running after the activity is stopped
        handler.removeCallbacks(goToNextPageRunnable)
    }

    private fun goToNextPage() {
        try {
            Log.d("BeforeSplashScreen", "goToNextPage called")
            if (getString(R.string.app_name) == "Bellyra" && BuildConfig.APPLICATION_ID == appId) {
                Log.d("BeforeSplashScreen", "Navigating to HomeActivity")
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                finish()
            } else {
                Log.d("BeforeSplashScreen", "App name or application ID does not match, finishing activity")
                finish()
            }
        } catch (e: Exception) {
            setToast("walul "+e.message.toString())
            Log.e("BeforeSplashScreen", "Error in goToNextPage: ${e.message}")
        }
    }
}

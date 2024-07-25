package recording.host


import android.content.Intent
import android.media.SoundPool
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import recording.host.databinding.ActivitySplashBinding
import sound.recorder.widget.RecordingSDK
import sound.recorder.widget.base.BaseActivityWidget
import sound.recorder.widget.builder.AdmobAdsBuilder
import sound.recorder.widget.builder.FanAdsBuilder
import sound.recorder.widget.builder.InMobiBuilder
import sound.recorder.widget.builder.RecordingWidgetBuilder
import sound.recorder.widget.builder.StarAppBuilder
import sound.recorder.widget.model.Song
import sound.recorder.widget.util.Constant

class SplashActivity : BaseActivityWidget() {

    private lateinit var sp : SoundPool


    private lateinit var binding : ActivitySplashBinding

    private val listTitle = arrayOf(
        "Gundul Gundul Pacul",
        "Ampar Ampar Pisang"
    )

    private var song = ArrayList<Song>()

    private val pathRaw = arrayOf(
        "android.resource://"+BuildConfig.APPLICATION_ID+"/raw/gundul_gundul_pacul",
        "android.resource://"+BuildConfig.APPLICATION_ID+"/raw/ampar_ampar_pisang"
    )

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //setupInterstitial()

        setupAppOpenAd()


        for (i in listTitle.indices) {
            val itemSong = Song()
            itemSong.title = listTitle[i]
            itemSong.pathRaw = pathRaw[i]
            song.add(itemSong)
        }
        RecordingSDK.addSong(this,song)
        RecordingSDK.run()

        val fanId             = "6371696286185210"
        val fanBannerId       = "6371696286185210_7264663670221796"
        val fanInterstitialId = "6371696286185210_7264664310221732"

        FanAdsBuilder.builder(this)
            .setBannerId(fanBannerId)
            .setApplicationId(fanId)
            .setInterstitialId(fanInterstitialId)
            .setEnable(true)
            .build()


        AdmobAdsBuilder.builder(this)
            .setAdmobId("")
            .setBannerId(Constant.AdsTesterId.admobBannerId)
            .setInterstitialId(Constant.AdsTesterId.admobInterstitialId)
            .setRewardId("")
            .setRewardInterstitialId("")
            .setNativeId("")
            .setAppOpenId(Constant.AdsTesterId.admobOpenAdId)
            .setOrientationAds(2)
            .build()


        InMobiBuilder.builder(this)
            .setBannerId(1705420822194)
            .setApplicationId("1706737529085")
            .setInterstitialId(1708302528403)
            .setEnable(true)
            .build()

        StarAppBuilder.builder(this)
            .setApplicationId("205917032")
            .showBanner(false)
            .showInterstitial(false)
            .setEnable(true)
            .build()

        RecordingWidgetBuilder.builder(this)
            .setAppName(getString(R.string.app_name))
            .setVersionCode(BuildConfig.VERSION_CODE)
            .setVersionName(BuildConfig.VERSION_NAME)
            .setApplicationId(BuildConfig.APPLICATION_ID)
            .setDeveloperName("Developer+Receh")
            .showNote(true)



        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this,MainActivity::class.java)
            startActivity(intent)
            finish()
        }, 3000)


    }

}
package recording.host

import android.annotation.SuppressLint
import android.os.Bundle
import sound.recorder.widget.base.BaseActivityWidget


@SuppressLint("Registered")
open class BaseActivity : BaseActivityWidget(){


    val admobBannerId = "ca-app-pub-4503297165525769/9848252129"
    val admobInterstitialId = "ca-app-pub-4503297165525769/5909007113"
    var admobId = "ca-app-pub-4503297165525769~1231395506"
    var admobRewardInterstitialId = ""
    var admobRewardId = ""
    var tag = BuildConfig.APPLICATION_ID



    val fanId             = "1158506188466367"
    var fanBannerId       = "1158506188466367_1158506601799659"
    val fanInterstitialId = "1158506188466367_1158506675132985"

    val starAppId = "202764189"


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }
}
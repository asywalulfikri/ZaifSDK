package sound.recorder.widget.util

open class Constant {


    interface KeyShared{
        companion object {
            const val appName = "appName"
            const val versionCode = "versionCode"
            const val versionName = "versionName"
            const val applicationId = "applicationId"
            const val showNote = "showNote"
            const val showSetting = "showSetting"
            const val showSong = "showSong"
            const val backgroundWidgetColor = "llRecordBackground"
            const val developerName = "developerName"



            const val shareKey = "recordingWidget"
            const val backgroundColor = "backgroundColor"
            const val volume = "volume"
            const val animation = "animation"
            const val colorWidget = "colorWidget"
            const val colorRunningText = "colorRunningText"

            const val volumeAudio = "volumeAudio"

            const val admobBannerId = "bannerId"
            const val admobId = "admobId"
            const val admobInterstitialId = "interstitialIdName"
            const val admobRewardInterstitialId = "rewardInterstitialId"
            const val admobRewardId = "rewardId"
            const val admobNativeId = "nativeId"
            const val admobAppOpenId = "appOpenId"
            const val orientationAds = "orientationAds"

            const val fanBannerId = "fanBannerId"
            const val fanId = "fanId"
            const val fanInterstitialId = "fanInterstitialId"
            const val fanEnable = "fanEnable"

            const val starAppId = "starAppId"
            const val starAppEnable = "starAppEnable"
            const val starAppShowBanner = "starAppShowBanner"
            const val starAppShowInterstitial = "starAppShowInterstitial"

            const val inMobiId = "inMobiId"
            const val inMobiBannerId = "inMobiBannerId"
            const val inMobiInterstitialId = "inMobiInterstitialId"
            const val inMobiEnable = "inMobiEnable"



        }
    }


    interface typeFragment{
        companion object{
            const val listRecordFragment       = "listRecordFragment"
            const val listMusicFragment        = "listMusicFragment"
            const val listNoteFragment         = "listNoteFragment"
            const val settingFragment          = "settingFragment"
            const val videoFragment            = "videoFragment"
            const val listNoteFirebaseFragment = "listNoteFirebaseFragment"
        }
    }

    interface AdsTesterId{
        companion object{
            const val admobBannerId             = "ca-app-pub-3940256099942544/6300978111"
            const val admobInterstitialId       = "ca-app-pub-3940256099942544/1033173712"
            const val admobRewardInterstitialId = "ca-app-pub-3940256099942544/5354046379"
            const val admobRewardId             = "ca-app-pub-3940256099942544/5224354917"
            const val admobNativeId             = "ca-app-pub-3940256099942544/2247696110"
            const val admobOpenAdId             = "ca-app-pub-3940256099942544/9257395921"

        }
    }

}
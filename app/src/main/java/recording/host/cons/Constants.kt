package recording.host.cons

import sound.recorder.widget.BuildConfig
import sound.recorder.widget.R


class Constants {

    object SongConstants {

        val listTitle = arrayOf(
            "Gundul Gundul Pacul",
            "Ampar Ampar Pisang"
        )


       val pathRaw = arrayOf(
            "android.resource://"+"sound.recorder.widget"+"/raw/gundul_gundul_pacul",
            "android.resource://"+"sound.recorder.widget"+"/raw/ampar_ampar_pisang"
        )

    }

    interface AdsProductionId{
        companion object{
            const val admobBannerId           = "ca-app-pub-4503297165525769/8869314001" //correct
            const val admobInterstitialId     = "ca-app-pub-4503297165525769/6993052482" //correct
            const val admobId                 = "ca-app-pub-4503297165525769~2938330478" //correct
            const val admobHomeBannerId               = "ca-app-pub-4503297165525769/8322544586" //correct
            const val admobRewardInterstitialId = "ca-app-pub-4503297165525769/2856073839" //correct
            const val admobRewardId             = "ca-app-pub-4503297165525769/2839761965" //correct
            const val admobNativeId =""


            const val fanId             = "6371696286185210"
            const val fanBannerId       = "6371696286185210_7264663670221796"
            const val fanBannerHomeId   = "6371696286185210_24358580093736887"
            const val fanInterstitialId = "6371696286185210_7264664310221732"


            const val starAppId = "205917032"
            const val unityGameId = "5278177"


        }
    }


}
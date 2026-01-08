package recording.host.cons

import recording.host.BuildConfig

class Constants {

    object SongConstants {

        val listTitle = arrayOf(
            "Gundul Gundul Pacul",
            "Ampar Ampar Pisang"
        )


       val pathRaw = arrayOf(
            "android.resource://"+ BuildConfig.APPLICATION_ID+"/raw/gundul_gundul_pacul",
            "android.resource://"+ BuildConfig.APPLICATION_ID+"/raw/ampar_ampar_pisang"
        )


        internal var listNote = arrayOf(
            "Gundul gundul pacul cul\n" +
                    "1   3       1    3    4  5    5\n" +
                    "Gembelengan\n" +
                    "7  1'  7  1'  7  5\n" +
                    "Nyunggi nyunggi wakul kul\n" +
                    "1         3    1    3     4    5    5\n" +
                    "Gembelengan\n" +
                    "7  1'  7  1'  7  5\n" +
                    "Wakul ngglimpang segane dadi sak latar\n" +
                    "  1   3     5     4         4   5     4   3  1 4 3 1\n" +
                    "Wakul ngglimpang segane dadi sak latar\n" +
                    "  1  3      5     4         4   5     4   3   1 4 3 1",


            "5 1   1 7   1 2\n" +
                    "Ampar ampar pisang\n" +

                    "\n" +
                    "5 5   2  2 1   2 3\n" +
                    "Pisangku belum masak\n" +
                    "\n" +

                    "4 2     2 3  1  1 2    2 1  7 1\n" +
                    "Masak sabigi di hurung bari-bari\n" +
                    "\n" +

                    "4 2     2 3  1  1 2    2 1  7 1\n" +
                    "Masak sabigi di hurung bari-bari\n" +
                    "\n" +

                    "5   5  5 1   1   7  1 2\n" +
                    "Mangga lepak mangga lepok\n" +
                    "\n" +

                    "5 2   2 1  2   3\n" +
                    "Patah kayu bengkok\n" +
                    "\n" +

                    "3   4   4 2 2   33\n" +
                    "Bengkok dimakan api\n" +
                    "\n" +

                    "11 2   2    1 7 1\n" +
                    "Apinya cang curupan\n" +
                    "\n" +

                    "3   4   4 2 2   33\n" +
                    "Bengkok dimakan api\n" +
                    "\n" +

                    "11 2   2    1 7 1\n" +
                    "Apinya cang curupan\n" +
                    "\n" +

                    "3    5 5  4 4   5 2\n" +
                    "Nang mana batis kutung\n" +
                    "\n" +

                    "2 4 4 3  2 1\n" +
                    "Dikitipi dawang\n" +
                    "\n" +

                    "3    5 5  4 4   5 2\n" +
                    "Nang mana batis kutung\n" +
                    "\n" +

                    "2 4 4 3  2 1\n" +
                    "Dikitipi dawang...",

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
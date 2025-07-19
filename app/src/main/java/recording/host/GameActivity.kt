package recording.host

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import recording.host.cons.Constants
import recording.host.databinding.ActivityGameBinding
import sound.recorder.widget.MyApp
import sound.recorder.widget.RecordingSDK
import sound.recorder.widget.listener.AdsListener
import sound.recorder.widget.listener.MyAdsListener
import sound.recorder.widget.model.Song
import kotlin.collections.indices
import kotlin.toString

class GameActivity : BaseActivity(), AdsListener, GameApp.AppInitializationListener, MyApp.SdkInitializationListener{
    private lateinit var binding: ActivityGameBinding

    private lateinit var pathAudio: ArrayList<Int>

    private var areBuildersReady = false
    private var areEssentialAdsReady = false
    private var isUnityReady = false
    private var adsSetupCalled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupHideStatusBar(binding.root, true)


        setupListener()



        MyAdsListener.setMyListener(this)
        permissionNotification()


        try {
            checkUpdate()
        }catch (e : Exception){
            setLog(e.message.toString())
        }

        loadSongs()

    }

    override fun getAdBannerContainer(): FrameLayout? {
        return binding.bannerID
    }


    fun setupListener(){
        GameApp.registerListener(this)
        MyApp.registerListener(this)
        areBuildersReady = GameApp.isInitialized
        areEssentialAdsReady = MyApp.areEssentialsInitialized
        isUnityReady = MyApp.isUnityInitialized
    }

    fun setupAds() {
        lifecycleScope.launch {
            delay(1000)
            setupBannerUnity(binding.bannerUnity)
            setupBannerFacebook(binding.bannerFAN)
            loadBannerAds()
            setupInterstitial()

            delay(10000) // jeda 1 detik sebelum setupBannerAdmob
            setupBannerAdmob(binding.bannerAdmob)
        }
    }

    private fun loadSongs() {
        lifecycleScope.launch(Dispatchers.IO) {
            val songs = ArrayList<Song>()
            for (i in Constants.SongConstants.listTitle.indices) {
                val itemSong = Song()
                itemSong.title = Constants.SongConstants.listTitle[i]
                itemSong.pathRaw = Constants.SongConstants.pathRaw[i]
                itemSong.note =""
                songs.add(itemSong)
            }
            withContext(Dispatchers.Main) {
                RecordingSDK.addSong(this@GameActivity, songs)
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 123) {
            if (resultCode != Activity.RESULT_OK) {
                // Handle update failure
                setToast("Failed Update App")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        MyAdsListener.setMyListener(null)
        MyApp.unregisterListener(this)
        GameApp.unregisterListener(this)
        onDestroyUpdate()

    }

    override fun onViewBannerHome(show: Boolean) {
        if(show){
            binding.bannerID.visibility  = View.VISIBLE
            binding.bannerAdmob.visibility    = View.GONE
        }else{
            binding.bannerID.visibility  = View.GONE
            binding.bannerAdmob.visibility    = View.VISIBLE
        }
    }

    override fun onViewBannerUnity(show: Boolean) {
        if(show){
            binding.bannerUnity.visibility = View.VISIBLE
            binding.bannerAdmob.visibility      = View.VISIBLE
        }else{
            binding.bannerUnity.visibility = View.GONE
            binding.bannerAdmob.visibility      = View.GONE
        }
    }

    override fun onHideAllBanner() {
        binding.bannerID.visibility  = View.GONE
        binding.bannerAdmob.visibility    = View.GONE
        binding.bannerFAN.visibility = View.GONE
        binding.bannerUnity.visibility = View.GONE
    }

    override fun onShowInterstitial() {
        showInterstitial()
    }

    override fun onInitializationComplete() {

        areBuildersReady = true
    }

    override fun onSdkInitialized(sdk: MyApp.Sdk) {
        when (sdk) {
            MyApp.Sdk.ALL_ESSENTIALS -> areEssentialAdsReady = true
            MyApp.Sdk.UNITY -> isUnityReady = true
        }

        tryToSetupAds()
    }

    private fun tryToSetupAds() {
        // Hanya panggil setupAds jika SEMUA flag sudah true
        if (areBuildersReady && areEssentialAdsReady && adsSetupCalled==false) {
            adsSetupCalled = true
            setupAds()
        }
    }
}

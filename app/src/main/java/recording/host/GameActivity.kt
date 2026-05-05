package recording.host

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import recording.host.cons.Constants.SongConstants.rawList
import recording.host.databinding.ActivityGameBinding
import sound.recorder.widget.MyApp
import sound.recorder.widget.listener.AdsListener
import sound.recorder.widget.listener.MyAdsListener
import sound.recorder.widget.music.MusicListDialogHelper
import sound.recorder.widget.music.MusicPlayerManager
import java.util.concurrent.atomic.AtomicBoolean

class GameActivity : BaseActivity(),
    AdsListener,
    GameApp.AppInitializationListener,
    MyApp.SdkInitializationListener {

    private var _binding: ActivityGameBinding? = null
    private val binding get() = _binding!!

    /** =====================
     *  STATE FLAGS (THREAD SAFE)
     *  ===================== */
    private val adsSetupCalled    = AtomicBoolean(false)
    private val adsFirstLoadIsOff = AtomicBoolean(false)

    private var areBuildersReady     = false
    private var areEssentialAdsReady = false

    /** =====================
     *  NETWORK CALLBACK
     *  ===================== */
    private lateinit var connectivityManager: ConnectivityManager

    private val soundViewModel: SoundViewModel by viewModels()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            // compareAndSet memastikan hanya sekali trigger setelah koneksi hilang
            if (adsFirstLoadIsOff.compareAndSet(true, false)) {
                runOnUiThreadSafe {
                    setToastADS("Internet restored, loading ads…")
                    tryToSetupAds()
                }
            }
        }
    }

    /** =====================
     *  LIFECYCLE
     *  ===================== */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        _binding = ActivityGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setupHideStatusBar(binding.root, true)

        connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager

        MyAdsListener.setMyListener(this)
        GameApp.registerListener(this)

        // Jika SDK sudah selesai init sebelum Activity ini dibuat,
        // MyApp.registerListener() akan langsung callback via mainHandler.post {}
        // sehingga tidak ada race condition
        MyApp.registerListener(this)

        permissionNotification()

        if (BuildConfig.hasSong) {
            loadSongsOnce()
        }
    }

    override fun onStart() {
        super.onStart()
        registerNetworkCallbackSafe()
    }

    override fun onStop() {
        super.onStop()
        unregisterNetworkCallbackSafe()
    }

    override fun onDestroy() {
        MyAdsListener.setMyListener(null)
        GameApp.unregisterListener(this)
        MyApp.unregisterListener(this)
        _binding = null
        super.onDestroy()
    }

    /** =====================
     *  NETWORK HELPERS
     *  ===================== */
    private fun registerNetworkCallbackSafe() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                connectivityManager.registerDefaultNetworkCallback(networkCallback)
            } else {
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                connectivityManager.registerNetworkCallback(request, networkCallback)
            }
        } catch (e: Exception) {
            Log.e("Network", "register callback error: ${e.message}")
        }
    }

    private fun unregisterNetworkCallbackSafe() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            setLog(e.message.toString())
        }
    }

    /** =====================
     *  SONG LOADING (ONE TIME, ANTI-ANR)
     *  ===================== */
    private fun loadSongsOnce() {
        lifecycleScope.launch(Dispatchers.IO) {
            val tracks = rawList.map { (resId, title) ->
                MusicPlayerManager.MusicTrack(
                    title    = title,
                    duration = getRawDurationSafe(resId),
                    isRaw    = true,
                    rawResId = resId
                )
            }
            withContext(Dispatchers.Main) {
                if (!isDestroyed && !isFinishing) {
                    MusicListDialogHelper.registerRawTracks(tracks)
                }
            }
        }
    }

    /** =====================
     *  ADS SETUP (STRICTLY ONCE)
     *  ===================== */
    private fun tryToSetupAds() {
        // Kedua flag harus true sebelum lanjut
        if (!areBuildersReady || !areEssentialAdsReady) return

        if (!isInternetTrulyAvailable(this)) {
            adsFirstLoadIsOff.set(true)
            return
        }

        // compareAndSet memastikan blok ini hanya dieksekusi satu kali
        if (!adsSetupCalled.compareAndSet(false, true)) return

        lifecycleScope.launch {

            delay(1_200)

            // Pastikan binding masih ada setelah delay
            _binding?.let {
                loadBannerGame(it.bannerGame, true)
            }

            delay(10_000)

            // Pastikan Activity masih aktif setelah delay panjang
            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return@launch
            if (isDestroyed || isFinishing) return@launch

            try {
                loadBannerHome()
            } catch (e: Exception) {
                setLog(e.message.toString())
            }
        }
    }

    /**
     * Load banner AdMob di bannerHome.
     * Dipanggil setelah delay 10 detik di tryToSetupAds().
     */
    private fun loadBannerHome() {
        _binding?.let {
            loadBannerGame(it.bannerHome, false)
        }
    }

    /** =====================
     *  SDK CALLBACKS
     *  ===================== */

    // Callback dari MyApp — dipanggil di Main Thread (via mainHandler.post)
    override fun onSdkInitialized(sdk: MyApp.Sdk) {
        if (sdk == MyApp.Sdk.ALL_ESSENTIALS) {
            areEssentialAdsReady = true
            tryToSetupAds()
        }
    }

    // Callback dari GameApp
    override fun onInitializationComplete() {
        areBuildersReady = true
        tryToSetupAds()
    }

    /** =====================
     *  ADS LISTENER CALLBACKS
     *  ===================== */
    override fun onViewBannerHome(show: Boolean) {
        _binding?.apply {
            if (soundViewModel.isPremium) {
                onHideAllBanner()
            } else {
                val homeTarget = if (show) View.GONE else View.VISIBLE
                val gameTarget = if (show) View.VISIBLE else View.GONE

                bannerHome.animate()
                    .alpha(if (show) 0f else 1f)
                    .withEndAction { bannerHome.visibility = homeTarget }

                bannerGame.animate()
                    .alpha(if (show) 1f else 0f)
                    .withStartAction { bannerGame.visibility = gameTarget }
            }
        }
    }

    override fun onHideAllBanner() {
        _binding?.apply {
            bannerHome.visibility = View.GONE
            bannerGame.visibility = View.GONE
        }
    }

    override fun loadInterstitial() {
        loadInterstitialIfNeeded(soundViewModel.isPremium)
    }

    override fun loadReward() {
        loadRewardedAd(soundViewModel.isPremium)
    }

    /** =====================
     *  SAFE HELPERS
     *  ===================== */
    private fun runOnUiThreadSafe(block: () -> Unit) {
        if (!isFinishing && !isDestroyed) {
            runOnUiThread { block() }
        }
    }
}
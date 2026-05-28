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
import recording.host.cons.Constants
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
    private val binding get() = _binding

    companion object {
        private const val ADS_INITIAL_DELAY_MS    = 1_200L
        private const val ADS_SECONDARY_DELAY_MS  = 10_000L
        private const val TAG                     = "GameActivity"
    }

    /** =====================
     *  STATE FLAGS (THREAD SAFE)
     *  ===================== */
    private val adsSetupCalled    = AtomicBoolean(false)
    private val adsFirstLoadIsOff = AtomicBoolean(false)
    private val songsLoaded       = AtomicBoolean(false) // ← guard loadSongsOnce
    private var areBuildersReady      = false
    private var areEssentialAdsReady  = false

    /** =====================
     *  NETWORK CALLBACK
     *  ===================== */
    private lateinit var connectivityManager: ConnectivityManager

    private val soundViewModel: SoundViewModel by viewModels()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (adsFirstLoadIsOff.compareAndSet(true, false)) {
                runOnUiThreadSafe {
                    setToastADS("Internet restored, loading ads…")
                    tryToSetupAds()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        _binding = ActivityGameBinding.inflate(layoutInflater)
        setContentView(_binding!!.root)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setupHideStatusBar(_binding!!.root, true)

        connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager

        MyAdsListener.setMyListener(this)
        GameApp.registerListener(this)
        MyApp.registerListener(this)

        permissionNotification()
        setupGDPR()

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
            Log.e(TAG, "register callback error: ${e.message}")
        }
    }

    private fun unregisterNetworkCallbackSafe() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            Log.e(TAG, "unregister callback error: ${e.message}")
        }
    }

    /** =====================
     *  SONG LOADING (ONE TIME, ANTI-ANR)
     *  ===================== */
    private fun loadSongsOnce() {
        // ← Guard: pastikan hanya dipanggil sekali
        if (!songsLoaded.compareAndSet(false, true)) return

        lifecycleScope.launch(Dispatchers.IO) {
            val tracks = Constants.SongConstants.rawList.map { (resId, title) ->
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
        if (!areBuildersReady || !areEssentialAdsReady) return

        if (!isInternetTrulyAvailable(this)) {
            adsFirstLoadIsOff.set(true)
            return
        }

        if (!adsSetupCalled.compareAndSet(false, true)) return

        lifecycleScope.launch {
            delay(ADS_INITIAL_DELAY_MS)

            binding?.let {
                loadBannerGame(it.bannerGame, BuildConfig.isPotrait)
            }

            delay(ADS_SECONDARY_DELAY_MS)

            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return@launch
        }
    }

    /** =====================
     *  ADS CALLBACK
     *  ===================== */
    override fun onSdkInitialized(sdk: MyApp.Sdk) {
        if (sdk == MyApp.Sdk.ALL_ESSENTIALS) {
            areEssentialAdsReady = true
            tryToSetupAds()
        }
    }

    override fun onInitializationComplete() {
        areBuildersReady = true
        tryToSetupAds()
    }

    override fun onViewBanner(show: Boolean) {
        binding?.apply {
            if (soundViewModel.isPremium) {
                bannerGame.visibility = View.GONE
                return@apply
            }else{
                val gameTo = if (show) View.VISIBLE else View.GONE
                val gameAlpha = if (show) 1f else 0f

                bannerGame.animate().alpha(gameAlpha).withStartAction {
                    bannerGame.visibility = gameTo
                }
            }
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
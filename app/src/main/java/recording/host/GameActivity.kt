package recording.host

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import recording.host.cons.Constants
import recording.host.databinding.ActivityGameBinding
import sound.recorder.widget.MyApp
import sound.recorder.widget.RecordingSDK
import sound.recorder.widget.listener.AdsListener
import sound.recorder.widget.listener.MyAdsListener
import sound.recorder.widget.model.Song
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
    private val adsSetupCalled = AtomicBoolean(false)
    private val adsFirstLoadIsOff = AtomicBoolean(false)
    private val songsLoaded = AtomicBoolean(false)

    private var areBuildersReady = false
    private var areEssentialAdsReady = false

    /** =====================
     *  NETWORK CALLBACK (NON-DEPRECATED)
     *  ===================== */
    private lateinit var connectivityManager: ConnectivityManager
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (adsFirstLoadIsOff.compareAndSet(true, false)) {
                runOnUiThreadSafe {
                    showToast("Internet restored, loading ads…")
                    tryToSetupAds()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setupHideStatusBar(binding.root, true)

        connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        MyAdsListener.setMyListener(this)
        GameApp.registerListener(this)
        MyApp.registerListener(this)

        permissionNotification()
        safeCall { checkUpdate() }

        loadSongsOnce()
    }



    override fun onStart() {
        super.onStart()
        registerNetworkCallbackSafe()
    }

    override fun onStop() {
        super.onStop()
        unregisterNetworkCallbackSafe()
    }

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
            // ignore jika belum terdaftar
        }
    }


    override fun onDestroy() {
        MyAdsListener.setMyListener(null)
        GameApp.unregisterListener(this)
        MyApp.unregisterListener(this)
        onDestroyUpdate()

        _binding = null
        super.onDestroy()
    }

    /** =====================
     *  SONG LOADING (ONE TIME, ANTI-ANR)
     *  ===================== */
    private fun loadSongsOnce() {
        if (!songsLoaded.compareAndSet(false, true)) return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val songs = Constants.SongConstants.listTitle.indices.map { i ->
                    Song().apply {
                        title = Constants.SongConstants.listTitle[i]
                        pathRaw = Constants.SongConstants.pathRaw[i]
                        note = Constants.SongConstants.listNote[i]
                    }
                }

                withContext(Dispatchers.Main) {
                    if (_binding != null && !isFinishing && !isDestroyed) {
                        RecordingSDK.addSong(this@GameActivity, ArrayList(songs))
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setLog("LoadSongs Error: ${e.message}")
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
            // beri waktu UI settle
            kotlinx.coroutines.delay(1200)

            _binding?.let {
                loadBannerGame(it.bannerGame,true)
                setupInterstitial()
            }

            // banner kedua dibuat jauh lebih lambat
            kotlinx.coroutines.delay(15000)

            setToastADS("load banner ke 2")
           _binding?.let {
                loadBannerHome(it.bannerHome)
            }
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


    override fun onViewBannerHome(show: Boolean) {
        _binding?.apply {
            val homeTarget = if (show) View.GONE else View.VISIBLE
            val gameTarget = if (show) View.VISIBLE else View.GONE

            // Animasi halus agar tidak UI Lag
            bannerHome.animate().alpha(if (show) 0f else 1f).withEndAction {
                bannerHome.visibility = homeTarget
            }
            bannerGame.animate().alpha(if (show) 1f else 0f).withStartAction {
                bannerGame.visibility = gameTarget
            }
        }
    }

    override fun onHideAllBanner() {
        _binding?.apply {
            bannerHome.visibility = View.GONE
            bannerGame.visibility = View.GONE
        }
    }

    override fun onShowInterstitial() {
        showInterstitial()
    }

    /** =====================
     *  SAFE HELPERS
     *  ===================== */
    private fun runOnUiThreadSafe(block: () -> Unit) {
        if (!isFinishing && !isDestroyed) {
            runOnUiThread { block() }
        }
    }

    private fun showToast(message: String) {
        if (!isFinishing && !isDestroyed) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private inline fun safeCall(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            setLog("SafeCall Error: ${e.message}")
        }
    }
}

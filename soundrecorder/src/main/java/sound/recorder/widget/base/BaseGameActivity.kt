package sound.recorder.widget.base

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sound.recorder.widget.MyApp
import sound.recorder.widget.listener.AdsListener
import sound.recorder.widget.listener.MyAdsListener
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Induk GameActivity untuk semua app: seluruh alur iklan ada di sini, app hanya
 * menyediakan container banner dan status premium.
 *
 * Cara pakai di app:
 * 1. Isi [bannerContainer] dan [isPremiumUser].
 * 2. Panggil [startAds] di onCreate setelah setContentView.
 * 3. Panggil [onAdBuildersReady] setelah ID iklan terpasang (AdConfigProvider/GameApp).
 * 4. Kosongkan binding SETELAH super.onDestroy(), karena [bannerContainer] masih dipakai.
 *
 * Aturan yang dijaga:
 * - Banner dan interstitial menunggu consent GDPR dan SDK siap.
 * - Banner diminta sekali per Activity; refresh dan retry diurus BaseActivityWidget.
 * - Rewarded TIDAK dimuat di sini. Panggil MyAdsListener.loadReward() saat layar yang
 *   punya tombol iklan dibuka, supaya iklan yang dimuat tidak terbuang.
 */
abstract class BaseGameActivity : BaseActivityWidget(),
    AdsListener,
    MyApp.SdkInitializationListener {

    /** Container banner game; null bila view sudah dilepas. */
    protected abstract val bannerContainer: FrameLayout?

    /** True bila user bebas iklan (pembelian maupun pass sementara). */
    protected abstract val isPremiumUser: Boolean

    /** Jeda sebelum banner diminta, agar attach AdView tidak bertumpuk dengan interstitial. */
    protected open val adsInitialDelayMs: Long = 3_000L

    /** Portrait dipakai app yang layar game-nya tegak. */
    protected open val isBannerPortrait: Boolean = false

    private var adsStarted = false
    private var buildersReady = false
    private var essentialsReady = false
    private var consentGathered = false

    private var bannerRequested = false
    private var bannerJob: Job? = null
    /** Status banner terakhir dari onViewBanner; null bila belum pernah ditentukan. */
    private var bannerWanted: Boolean? = null
    private var bannerPaused = false

    /** Diakses dari thread network callback. */
    private val waitingForInternet = AtomicBoolean(false)
    private var networkCallbackRegistered = false
    private val connectivityManager by lazy {
        getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            val validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            if (validated && waitingForInternet.compareAndSet(true, false)) {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        setToastADS("Internet restored, loading ads…")
                        setupAdsIfReady()
                    }
                }
            }
        }
    }

    /** Mulai alur iklan. Panggil sekali di onCreate setelah setContentView. */
    protected fun startAds() {
        if (adsStarted) return
        adsStarted = true
        MyAdsListener.setMyListener(this)
        MyApp.registerListener(this)
        setupGDPR {
            consentGathered = true
            setupAdsIfReady()
        }
    }

    /** Panggil saat ID iklan di builder sudah terpasang. */
    protected fun onAdBuildersReady() {
        buildersReady = true
        setupAdsIfReady()
    }

    override fun onSdkInitialized(sdk: MyApp.Sdk) {
        if (sdk == MyApp.Sdk.ALL_ESSENTIALS) {
            essentialsReady = true
            setupAdsIfReady()
        }
    }

    override fun onStart() {
        super.onStart()
        if (!adsStarted) return
        registerNetworkCallback()
    }

    override fun onResume() {
        super.onResume()
        bannerPaused = false
        restoreBannerAfterPause()
        // Di onStart lifecycle belum STARTED, jadi load interstitial ditolak SDK. Iklan yang
        // belum ada (terpakai, atau retry-nya dibatalkan saat onStop) dimuat di sini.
        if (adsStarted) setupAdsIfReady()
    }

    private fun restoreBannerAfterPause() {
        val wanted = bannerWanted ?: return
        val banner = bannerContainer ?: return
        // Premium bisa aktif selama banner disembunyikan.
        if (wanted && !isPremiumUser) {
            banner.alpha = 1f
            banner.visibility = View.VISIBLE
            // SDK melanjutkan AdView lewat global layout, dan INVISIBLE -> VISIBLE
            // tidak selalu memicu layout. Saat ini lifecycle juga belum RESUMED.
            banner.requestLayout()
        } else {
            banner.visibility = View.GONE
        }
    }

    override fun onPause() {
        // FIX ANR setStopped (Oppo/Vivo): WebView iklan digambar lewat functor di RenderThread.
        // Bila functor itu macet saat Activity berhenti, main thread ikut menunggu RenderThread.
        // Keluarkan banner dari frame terakhir sebelum stop; AdView sendiri di-pause oleh SDK.
        bannerPaused = true
        if (adsStarted) bannerContainer?.let { banner ->
            banner.animate().cancel()
            if (bannerWanted == null) bannerWanted = banner.visibility == View.VISIBLE
            if (banner.visibility == View.VISIBLE) banner.visibility = View.INVISIBLE
        }
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        // Banner belum sempat diminta: batalkan, onStart berikutnya menjadwalkan ulang.
        if (bannerJob?.isActive == true) {
            bannerJob?.cancel()
            bannerRequested = false
        }
        bannerJob = null
        unregisterNetworkCallback()
    }

    override fun onDestroy() {
        unregisterNetworkCallback()
        if (adsStarted) {
            MyAdsListener.clearMyListener(this)
            MyApp.unregisterListener(this)
        }
        bannerJob?.cancel()
        bannerJob = null
        waitingForInternet.set(false)
        bannerContainer?.animate()?.cancel()
        bannerContainer?.removeAllViews()
        super.onDestroy()
    }

    override fun onViewBanner(show: Boolean) {
        val banner = bannerContainer ?: return
        bannerWanted = show
        if (isPremiumUser) {
            banner.visibility = View.GONE
            return
        }
        // Premium sementara bisa habis di tengah sesi; banner belum pernah diminta.
        if (show) setupAdsIfReady()
        // Saat paused banner tetap tersembunyi; onResume yang menerapkan status terakhir.
        if (bannerPaused) return
        val target = if (show) View.VISIBLE else View.GONE
        banner.animate().alpha(if (show) 1f else 0f).withStartAction {
            banner.visibility = target
        }
    }

    override fun loadInterstitial() {
        loadInterstitialIfNeeded(isPremiumUser)
    }

    override fun loadReward() {
        loadRewardedAd(isPremiumUser)
    }

    /** Satu pintu untuk banner dan interstitial; aman dipanggil berulang. */
    private fun setupAdsIfReady() {
        if (isFinishing || isDestroyed) return
        if (!buildersReady || !essentialsReady || !consentGathered) return
        if (!canRequestAdsSafely() || isPremiumUser) return

        if (!isInternetTrulyAvailable(this)) {
            waitingForInternet.set(true)
            return
        }

        // Consent bisa baru terbaca setelah setupGDPR selesai; aman dipanggil berulang.
        MyApp.initializeAdMob(this)
        loadInterstitial()
        requestBannerOnce()
    }

    private fun requestBannerOnce() {
        if (bannerRequested) return
        bannerRequested = true
        bannerJob = lifecycleScope.launch {
            delay(adsInitialDelayMs)
            // Job harus selesai di sini: onStop memakai isActive untuk tahu banner belum diminta.
            loadBannerGame(bannerContainer, isBannerPortrait)
        }
    }

    private fun registerNetworkCallback() {
        if (networkCallbackRegistered) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                connectivityManager.registerDefaultNetworkCallback(networkCallback)
            } else {
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                connectivityManager.registerNetworkCallback(request, networkCallback)
            }
            networkCallbackRegistered = true
        } catch (e: Exception) {
            Log.e(TAG, "register callback error: ${e.message}")
        }
    }

    private fun unregisterNetworkCallback() {
        if (!networkCallbackRegistered) return
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            Log.e(TAG, "unregister callback error: ${e.message}")
        } finally {
            networkCallbackRegistered = false
        }
    }

    private companion object {
        const val TAG = "BaseGameActivity"
    }
}

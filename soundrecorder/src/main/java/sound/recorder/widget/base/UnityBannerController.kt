package sound.recorder.widget.base

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.unity3d.services.banners.BannerErrorInfo
import com.unity3d.services.banners.BannerView
import com.unity3d.services.banners.UnityBannerSize
import java.util.concurrent.atomic.AtomicBoolean

class UnityBannerController(
    private val activity: Activity,
    private val lifecycleOwner: LifecycleOwner,
    private val placementId: String = "Banner_Android",
    private val loadTimeoutMs: Long = 10_000L
) : DefaultLifecycleObserver {

    @Volatile
    private var bannerView: BannerView? = null

    private val isLoading = AtomicBoolean(false)
    private val isDestroyed = AtomicBoolean(false)

    @Volatile
    private var timeoutRunnable: Runnable? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        lifecycleOwner.lifecycle.addObserver(this)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        cleanup()
    }

    fun load(container: FrameLayout) {

        // Jangan load kalau sudah ada banner
        if (bannerView != null) return

        // Pastikan hanya satu load berjalan
        if (!isLoading.compareAndSet(false, true)) return

        isDestroyed.set(false)

        if (!isLifecycleValid()) {
            isLoading.set(false)
            return
        }

        container.post {

            if (!isLifecycleValid()) {
                isLoading.set(false)
                return@post
            }

            loadInternal(container)
        }
    }

    private fun loadInternal(container: FrameLayout) {

        try {

            val banner = BannerView(
                activity,
                placementId,
                UnityBannerSize.standard
            )

            bannerView = banner

            val timeout = Runnable {
                handleTimeout(banner)
            }

            timeoutRunnable = timeout
            mainHandler.postDelayed(timeout, loadTimeoutMs)

            banner.listener = createBannerListener(banner)

            if (banner.parent == null) {
                container.addView(banner)
            }

            banner.load()

        } catch (e: Exception) {

            Log.e("UNITY_BANNER", "Error creating banner", e)

            isLoading.set(false)

            mainHandler.post {
                destroy()
            }
        }
    }

    private fun createBannerListener(
        banner: BannerView
    ) = object : BannerView.Listener() {

        override fun onBannerLoaded(view: BannerView?) {

            if (bannerView !== banner) return

            cancelTimeout()
            isLoading.set(false)

            Log.d("UNITY_BANNER", "Banner loaded")
        }

        override fun onBannerFailedToLoad(
            view: BannerView?,
            errorInfo: BannerErrorInfo?
        ) {

            if (bannerView !== banner) return

            cancelTimeout()
            isLoading.set(false)

            Log.e(
                "UNITY_BANNER",
                "Banner failed: ${errorInfo?.errorMessage}"
            )

            // ❗ Putus call stack Unity
            mainHandler.post {
                destroyInternal(banner)
            }
        }

        override fun onBannerClick(view: BannerView?) {}
        override fun onBannerShown(view: BannerView?) {}
        override fun onBannerLeftApplication(view: BannerView?) {}
    }

    private fun handleTimeout(banner: BannerView) {

        if (!isLoading.get() || bannerView !== banner || isDestroyed.get()) {
            return
        }

        Log.e("UNITY_BANNER", "Load timeout - detach only")

        isLoading.set(false)

        // ❗ timeout TIDAK destroy
        (banner.parent as? ViewGroup)?.removeView(banner)
    }

    fun destroy() {

        val banner = bannerView ?: return

        // ❗ Putus call stack caller
        mainHandler.post {
            destroyInternal(banner)
        }
    }

    private fun destroyInternal(banner: BannerView) {

        if (!isDestroyed.compareAndSet(false, true)) return
        if (bannerView !== banner) return

        cancelTimeout()

        try {

            banner.listener = null
            (banner.parent as? ViewGroup)?.removeView(banner)

            // ❗ Destroy selalu dijalankan terpisah
            mainHandler.post {
                try {
                    banner.destroy()
                } catch (e: Exception) {
                    Log.e("UNITY_BANNER", "Error destroying banner", e)
                }
            }

        } finally {

            bannerView = null
            isLoading.set(false)
        }
    }

    private fun cancelTimeout() {

        timeoutRunnable?.let {
            mainHandler.removeCallbacks(it)
            timeoutRunnable = null
        }
    }

    private fun isLifecycleValid(): Boolean {
        return lifecycleOwner.lifecycle.currentState
            .isAtLeast(Lifecycle.State.STARTED)
    }

    private fun cleanup() {

        cancelTimeout()

        destroy()

        lifecycleOwner.lifecycle.removeObserver(this)
    }
}

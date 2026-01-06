package sound.recorder.widget.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

class NetworkWatcher(private val owner: LifecycleOwner, private val onAvailable: () -> Unit) {

    private var connectivityManager: ConnectivityManager? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d("NetworkWatcher", "Internet available")
            onAvailable()
        }
    }

    fun start(context: Context) {
        connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // 1. Cek langsung
        if (isInternetConnected(context)) {
            onAvailable()
        } else {
            // 2. Daftarkan network callback aman untuk semua versi API >= 21
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager?.registerNetworkCallback(request, networkCallback)
        }

        // 3. Cleanup otomatis saat owner hancur
        owner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                connectivityManager?.unregisterNetworkCallback(networkCallback)
                connectivityManager = null
                super.onDestroy(owner)
            }
        })
    }

    private fun isInternetConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val actNw = cm.activeNetwork ?: return false
            val nc = cm.getNetworkCapabilities(actNw) ?: return false
            return nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            return cm.activeNetworkInfo?.isConnected ?: false
        }
    }
}

/*
package sound.recorder.widget.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

interface NetworkChangeListener {
    fun onNetworkChanged(isConnected: Boolean)
}

class NetworkChangeReceiver(private val listener: NetworkChangeListener) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val connected = isInternetAvailable(context)
        listener.onNetworkChanged(connected)
    }

    private fun isInternetAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            cm.activeNetwork ?: return false
        } else {
            TODO("VERSION.SDK_INT < M")
        }
        val nc = cm.getNetworkCapabilities(network) ?: return false
        return nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
*/

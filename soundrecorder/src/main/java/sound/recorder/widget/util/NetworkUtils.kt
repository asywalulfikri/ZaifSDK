package sound.recorder.widget.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import java.lang.ref.WeakReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NetworkUtils {

    companion object {

        // Fungsi untuk mengecek apakah perangkat terhubung ke internet
        fun isInternetConnected(context: Context, callback: (Boolean) -> Unit) {
            val weakContext = WeakReference(context)  // Menyimpan referensi ke context secara lemah untuk mencegah memory leak

            // Menjalankan pengecekan koneksi di background thread
            GlobalScope.launch(Dispatchers.Main) {
                val isConnected = withContext(Dispatchers.IO) {
                    val context = weakContext.get() ?: return@withContext false  // Cek apakah context masih ada
                    val connectivityManager =
                        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val networkCapabilities = connectivityManager.activeNetwork ?: return@withContext false
                        val actNw = connectivityManager.getNetworkCapabilities(networkCapabilities) ?: return@withContext false

                        // Mengecek berbagai jenis koneksi (WiFi, Data Seluler, Ethernet, Bluetooth)
                        when {
                            actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> return@withContext true
                            actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> return@withContext true
                            actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> return@withContext true
                            actNw.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> return@withContext true
                            else -> return@withContext false
                        }
                    } else {
                        // Untuk Android versi di bawah Marshmallow, menggunakan cara lama
                        @Suppress("DEPRECATION")
                        val networkInfo = connectivityManager.activeNetworkInfo ?: return@withContext false
                        @Suppress("DEPRECATION")
                        return@withContext networkInfo.isConnected
                    }
                }

                // Menjalankan callback di main thread setelah pengecekan selesai
                callback(isConnected)
            }
        }
    }
}

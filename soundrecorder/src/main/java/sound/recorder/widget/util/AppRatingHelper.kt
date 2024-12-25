package sound.recorder.widget.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

class AppRatingHelper(context: Context) {

    // Menggunakan WeakReference untuk menghindari memory leak
    private val contextRef = WeakReference(context)

    // Fungsi untuk membuka halaman rating aplikasi di Play Store
    fun openRating() {
        val context = contextRef.get() ?: return  // Mengambil context, jika sudah tidak ada, tidak lakukan apa-apa

        val appPackageName = context.packageName

        // Menjalankan kode di background untuk mencegah ANR
        GlobalScope.launch(Dispatchers.Main) {
            // Menggunakan coroutine untuk menjalankan task di background
            try {
                withContext(Dispatchers.IO) {
                    // Mencoba membuka halaman aplikasi di Google Play Store menggunakan URI khusus
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("market://details?id=$appPackageName")
                        )
                    )
                }
            } catch (e: ActivityNotFoundException) {
                // Jika tidak ada aplikasi Play Store, membuka halaman aplikasi di browser
                withContext(Dispatchers.IO) {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://play.google.com/store/apps/details?id=$appPackageName")
                        )
                    )
                }
            }
        }
    }


    fun openPlayStoreForMoreApps(context: Context?, devName: String) {
        context?.let {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://developer?id=$devName"))
                intent.setPackage("com.android.vending") // Specify the Play Store app package name

                it.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/developer?id=$devName"))
                it.startActivity(intent)
            }
        }
    }


}

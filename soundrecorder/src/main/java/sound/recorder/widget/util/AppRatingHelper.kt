package sound.recorder.widget.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.lang.ref.WeakReference

class AppRatingHelper(context: Context) {

    // Menggunakan WeakReference untuk menghindari memory leak
    private val contextRef = WeakReference(context)

    // Fungsi untuk membuka halaman rating aplikasi di Play Store
    fun openRating() {
        val context = contextRef.get() ?: return  // Mengambil context, jika sudah tidak ada, tidak lakukan apa-apa

        val appPackageName = context.packageName

        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$appPackageName"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: ActivityNotFoundException) {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$appPackageName"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
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

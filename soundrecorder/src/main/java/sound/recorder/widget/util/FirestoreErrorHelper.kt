package sound.recorder.widget.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

/**
 * Error query Firestore di layar admin (khusus developer).
 *
 * Query yang butuh composite index gagal dengan link "create index" di pesannya. Beberapa HP
 * (mis. Vivo) menyensor URL di logcat jadi "*****", jadi link diambil dari pesan exception
 * dan ditampilkan di dialog dengan tombol Buka / Salin.
 */
object FirestoreErrorHelper {

    private const val TAG = "FirestoreError"
    private val INDEX_URL = Regex("""https://console\.firebase\.google\.com/\S+""")

    fun show(context: Context?, error: Exception, prefix: String = "Gagal memuat data") {
        val message = error.message.orEmpty()
        Log.e(TAG, "$prefix: $message", error)
        if (context == null) return

        val url = INDEX_URL.find(message)?.value?.trimEnd('.', ',', ')')
        if (url == null) {
            Toast.makeText(context, "$prefix: $message", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            AlertDialog.Builder(context)
                .setTitle("Index Firestore belum ada")
                .setMessage("Query ini butuh composite index.\n\n$url")
                .setPositiveButton("Buka") { _, _ ->
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } catch (e: Exception) {
                        copy(context, url)
                    }
                }
                .setNeutralButton("Salin") { _, _ -> copy(context, url) }
                .setNegativeButton("Tutup", null)
                .show()
        } catch (e: Exception) {
            // Fragment sudah lepas / window tidak valid: tetap beri tahu lewat Toast.
            Toast.makeText(context, "$prefix: $message", Toast.LENGTH_LONG).show()
        }
    }

    private fun copy(context: Context, url: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        cm.setPrimaryClip(ClipData.newPlainText("Firestore index", url))
        Toast.makeText(context, "Link disalin", Toast.LENGTH_SHORT).show()
    }
}

package sound.recorder.widget.ui.fragment

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import sound.recorder.widget.R

/** Salin isi laporan di layar admin ke clipboard. */
internal fun Context.copyAdminText(text: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.admin_copy), text))
    Toast.makeText(this, R.string.admin_copied, Toast.LENGTH_SHORT).show()
}

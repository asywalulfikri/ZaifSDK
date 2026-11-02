package sound.recorder.widget.ui.dialog

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import sound.recorder.widget.R
import sound.recorder.widget.recording.RecordingDialogViews

/**
 * Dialog informatif untuk memberitahu user tentang delay audio pada perangkat Bluetooth.
 * "Mengerti": tidak muncul lagi. "Ingatkan lagi nanti": muncul lagi saat halaman dibuka berikutnya.
 * Panggil [show] dengan context Activity saat halaman alat musik dibuka; dipakai semua app.
 */
object DelayInfoDialog {

    private const val PREF_NAME = "zaif_sdk_prefs"
    private const val KEY_SHOWN = "delay_info_shown"
    private val ACCENT = Color.parseColor("#FF6B35")

    fun show(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        
        // Cek apakah sudah pernah ditampilkan
        if (prefs.getBoolean(KEY_SHOWN, false)) return

        val dialog = AlertDialog.Builder(context).create()

        // Root Layout
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0C1628")) // Dark blue background
                cornerRadius = 48f
                setStroke(2, Color.parseColor("#FF6B35")) // Orange accent
            }
            val p = 56
            setPadding(p, p, p, p)
        }

        // Title
        root.addView(TextView(context).apply {
            text = context.getString(R.string.information)
            setTextColor(Color.parseColor("#FF6B35"))
            textSize = 18f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = 32
            layoutParams = lp
        })

        // Message
        root.addView(TextView(context).apply {
            text = context.getString(R.string.info_delay)
            setTextColor(Color.WHITE)
            textSize = 14f
            setLineSpacing(0f, 1.4f)
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = 48
            layoutParams = lp
        })

        val buttons = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }

        // Tidak menyimpan apa pun, jadi dialog muncul lagi saat halaman dibuka berikutnya.
        buttons.addView(button(context, context.getString(R.string.remind_me_later), primary = false) {
            dialog.dismiss()
        }, buttonParams(endMargin = 24))

        buttons.addView(button(context, context.getString(R.string.understand), primary = true) {
            // Simpan status agar tidak muncul lagi
            prefs.edit().putBoolean(KEY_SHOWN, true).apply()
            dialog.dismiss()
        }, buttonParams(endMargin = 0))
        root.addView(buttons)

        dialog.setView(root)
        dialog.setCancelable(false) // User harus memilih salah satu tombol

        try {
            dialog.show()
            RecordingDialogViews.applyWindow(dialog, context, 0.85, 0.5)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun button(context: Context, label: String, primary: Boolean, onClick: () -> Unit) =
        TextView(context).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(24, 24, 24, 24)
            background = GradientDrawable().apply {
                cornerRadius = 16f
                if (primary) setColor(ACCENT) else setStroke(2, ACCENT)
            }
            setOnClickListener { onClick() }
        }

    private fun buttonParams(endMargin: Int) = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
        marginEnd = endMargin
    }

    /**
     * Reset status (Hanya untuk keperluan testing)
     */
    fun reset(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_SHOWN)
            .apply()
    }
}

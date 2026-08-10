package sound.recorder.widget.ui.dialog

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import sound.recorder.widget.R

/**
 * Dialog informatif untuk memberitahu user tentang delay audio pada perangkat Bluetooth.
 * Muncul hanya sekali (perpetual dismiss) setelah user klik OK.
 */
object DelayInfoDialog {

    private const val PREF_NAME = "zaif_sdk_prefs"
    private const val KEY_SHOWN = "delay_info_shown"

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

        // OK Button
        val btnOk = TextView(context).apply {
            text = context.getString(R.string.understand)
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            val pH = 64; val pV = 24
            setPadding(pH, pV, pH, pV)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#FF6B35"))
                cornerRadius = 16f
            }
            setOnClickListener {
                // Simpan status agar tidak muncul lagi
                prefs.edit().putBoolean(KEY_SHOWN, true).apply()
                dialog.dismiss()
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.gravity = Gravity.CENTER
            layoutParams = lp
        }
        root.addView(btnOk)

        dialog.setView(root)
        dialog.setCancelable(false) // User harus klik OK
        
        try {
            dialog.show()
            dialog.window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setLayout(
                    (context.resources.displayMetrics.widthPixels * 0.85).toInt(),
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
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

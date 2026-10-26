package sound.recorder.widget.recording

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.Keep
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.intuit.sdp.R as SdpR

/**
 * Tampilan dialog rekaman (daftar rekaman, export, ubah nama, dll) — netral, untuk semua aplikasi alat musik.
 * Atur sekali per aplikasi, misal di Application:
 * `RecordingDialogStyle.default = RecordingDialogStyle(accentColor = Color.parseColor("#E53935"))`
 *
 * @param accentColor warna utama (tombol Mainkan, progress, pilihan aktif).
 *        null = otomatis memakai `colorPrimary` dari tema aplikasi, jadi menyatu dengan tiap aplikasi.
 * @param darkMode null = ikut tema sistem, true/false = paksa gelap/terang.
 */
@Keep
data class RecordingDialogStyle(
    @ColorInt val accentColor: Int? = null,
    val darkMode: Boolean? = null
) {
    companion object {
        @JvmStatic
        var default = RecordingDialogStyle()
    }
}

/** Warna turunan dari [RecordingDialogStyle] sesuai tema terang/gelap. */
internal class RecordingDialogPalette(context: Context, style: RecordingDialogStyle) {
    val dark = style.darkMode ?: ((context.resources.configuration.uiMode and
        Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES)
    val accent = style.accentColor ?: themeColor(context, androidx.appcompat.R.attr.colorPrimary) ?: Color.parseColor("#2F6FED")
    val surface = if (dark) Color.parseColor("#1F1F23") else Color.WHITE
    val surfaceAlt = if (dark) Color.parseColor("#2A2A2F") else Color.parseColor("#F3F4F6")
    val text = if (dark) Color.parseColor("#F2F2F2") else Color.parseColor("#1F1F1F")
    val textSecondary = if (dark) Color.parseColor("#A3A3AA") else Color.parseColor("#6B6B73")
    val outline = if (dark) Color.parseColor("#3A3A40") else Color.parseColor("#E2E3E8")
    val onAccent = if (ColorUtils.calculateLuminance(accent) > 0.5) Color.BLACK else Color.WHITE
    val accentSoft = ColorUtils.setAlphaComponent(accent, if (dark) 0x40 else 0x1F)
    val success = Color.parseColor(if (dark) "#81C784" else "#2E7D32")
    val error = Color.parseColor(if (dark) "#EF9A9A" else "#C62828")
    val ripple = ColorUtils.setAlphaComponent(accent, 0x33)

    private companion object {
        /** Warna dari atribut tema; null jika atribut tidak ada / bukan warna (fallback ke warna default). */
        fun themeColor(context: Context, attr: Int): Int? {
            val tv = TypedValue()
            if (!context.theme.resolveAttribute(attr, tv, true)) return null
            if (tv.type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT) return tv.data
            if (tv.resourceId == 0) return null
            // Bisa berupa @color atau color selector; tipe resource lain bukan warna
            if (context.resources.getResourceTypeName(tv.resourceId) != "color") return null
            return ContextCompat.getColor(context, tv.resourceId)
        }
    }
}

/** Komponen view sederhana yang dipakai bersama oleh dialog-dialog rekaman. */
internal object RecordingDialogViews {

    fun Context.sdp(id: Int) = resources.getDimensionPixelSize(id)
    fun Context.sdpF(id: Int) = resources.getDimension(id)

    fun rounded(context: Context, color: Int, radius: Int, strokeColor: Int? = null) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = context.sdpF(radius)
            strokeColor?.let { setStroke(context.sdp(SdpR.dimen._1sdp), it) }
        }

    fun circle(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }

    /** Wadah dialog: latar rounded + padding standar. */
    fun dialogRoot(context: Context, c: RecordingDialogPalette) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(context, c.surface, SdpR.dimen._12sdp)
        val pad = context.sdp(SdpR.dimen._16sdp)
        setPadding(pad, pad, pad, context.sdp(SdpR.dimen._8sdp))
    }

    fun title(context: Context, c: RecordingDialogPalette, label: String) = TextView(context).apply {
        text = label
        setTextColor(c.text)
        textSize = 16f
        typeface = Typeface.DEFAULT_BOLD
    }

    fun body(context: Context, c: RecordingDialogPalette, label: String) = TextView(context).apply {
        text = label
        setTextColor(c.textSecondary)
        textSize = 12f
        setLineSpacing(0f, 1.2f)
    }

    /** Tombol teks datar (Batal, Tutup, Simpan di footer dialog). */
    fun textButton(context: Context, c: RecordingDialogPalette, label: String, color: Int = c.accent) =
        TextView(context).apply {
            text = label
            setTextColor(color)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            val pH = context.sdp(SdpR.dimen._12sdp)
            val pV = context.sdp(SdpR.dimen._8sdp)
            setPadding(pH, pV, pH, pV)
            background = RippleDrawable(ColorStateList.valueOf(c.ripple), null, null)
        }

    /** Tombol kotak: [primary] = terisi warna aksen, selain itu bergaris tepi. */
    fun boxButton(context: Context, c: RecordingDialogPalette, label: String, primary: Boolean, onClick: () -> Unit) =
        TextView(context).apply {
            text = label
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(if (primary) c.onAccent else c.accent)
            background = RippleDrawable(
                ColorStateList.valueOf(c.ripple),
                if (primary) rounded(context, c.accent, SdpR.dimen._8sdp)
                else rounded(context, Color.TRANSPARENT, SdpR.dimen._8sdp, c.outline),
                null
            )
            setOnClickListener { onClick() }
        }

    /** Ikon vektor yang diwarnai. */
    fun icon(context: Context, @DrawableRes res: Int, color: Int) = ImageView(context).apply {
        setImageResource(res)
        imageTintList = ColorStateList.valueOf(color)
        scaleType = ImageView.ScaleType.FIT_CENTER
    }

    /** Tombol ikon bulat (mis. tutup ✕). */
    fun iconButton(
        context: Context,
        c: RecordingDialogPalette,
        @DrawableRes res: Int,
        contentDescription: String,
        onClick: () -> Unit
    ) = FrameLayout(context).apply {
        this.contentDescription = contentDescription
        background = RippleDrawable(ColorStateList.valueOf(c.ripple), null, circle(Color.WHITE))
        val size = context.sdp(SdpR.dimen._16sdp)
        addView(icon(context, res, c.textSecondary), FrameLayout.LayoutParams(size, size, Gravity.CENTER))
        setOnClickListener { onClick() }
    }

    /** Baris tombol rata kanan di bawah dialog. */
    fun footer(context: Context, vararg buttons: View) = LinearLayout(context).apply {
        gravity = Gravity.END
        setPadding(0, context.sdp(SdpR.dimen._8sdp), 0, 0)
        buttons.forEach { addView(it) }
    }

    fun vSpace(context: Context, dimen: Int) = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(-1, context.sdp(dimen))
    }

    /** Satu pilihan di [actionSheet]. [color] null = warna teks biasa. */
    class SheetItem(@DrawableRes val icon: Int, val label: String, val color: Int? = null, val onClick: () -> Unit)

    /** Dialog daftar pilihan berikon (pengganti AlertDialog.setItems yang polos). */
    fun actionSheet(context: Context, c: RecordingDialogPalette, title: String, items: List<SheetItem>): Dialog {
        val dialog = androidx.appcompat.app.AlertDialog.Builder(context).create()
        val root = dialogRoot(context, c)
        root.addView(title(context, c, title).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        root.addView(vSpace(context, SdpR.dimen._8sdp))
        items.forEach { item ->
            val color = item.color ?: c.text
            root.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val p = context.sdp(SdpR.dimen._10sdp)
                setPadding(p, p, p, p)
                background = RippleDrawable(ColorStateList.valueOf(c.ripple), null, rounded(context, Color.WHITE, SdpR.dimen._8sdp))
                val size = context.sdp(SdpR.dimen._16sdp)
                addView(icon(context, item.icon, if (item.color == null) c.textSecondary else color), LinearLayout.LayoutParams(size, size))
                addView(TextView(context).apply {
                    text = item.label
                    setTextColor(color)
                    textSize = 14f
                    setPadding(context.sdp(SdpR.dimen._12sdp), 0, 0, 0)
                })
                setOnClickListener {
                    dialog.dismiss()
                    item.onClick()
                }
            }, LinearLayout.LayoutParams(-1, -2))
        }
        root.addView(footer(context, textButton(context, c, context.getString(sound.recorder.widget.R.string.close)).apply {
            setOnClickListener { dialog.dismiss() }
        }), LinearLayout.LayoutParams(-1, -2))
        dialog.setView(root)
        dialog.show()
        applyWindow(dialog, context, 0.85, 0.45)
        return dialog
    }

    /** Dialog konfirmasi: judul, pesan, tombol batal + tombol aksi (opsional merah untuk aksi berbahaya). */
    fun confirmDialog(
        context: Context,
        c: RecordingDialogPalette,
        title: String,
        message: String,
        positiveLabel: String,
        negativeLabel: String,
        destructive: Boolean = false,
        onPositive: () -> Unit
    ): Dialog {
        val dialog = androidx.appcompat.app.AlertDialog.Builder(context).create()
        val root = dialogRoot(context, c)
        root.addView(title(context, c, title))
        root.addView(vSpace(context, SdpR.dimen._6sdp))
        root.addView(body(context, c, message))
        root.addView(footer(
            context,
            textButton(context, c, negativeLabel, c.textSecondary).apply { setOnClickListener { dialog.dismiss() } },
            textButton(context, c, positiveLabel, if (destructive) c.error else c.accent).apply {
                setOnClickListener {
                    dialog.dismiss()
                    onPositive()
                }
            }
        ), LinearLayout.LayoutParams(-1, -2))
        dialog.setView(root)
        dialog.show()
        applyWindow(dialog, context, 0.85, 0.50)
        return dialog
    }

    /** Kolom input bergaya sama (latar lembut + garis tepi tipis). */
    fun styleInput(context: Context, c: RecordingDialogPalette, input: android.widget.EditText) {
        input.setTextColor(c.text)
        input.setHintTextColor(c.textSecondary)
        input.background = rounded(context, c.surfaceAlt, SdpR.dimen._8sdp, c.outline)
        val p = context.sdp(SdpR.dimen._10sdp)
        input.setPadding(p, p, p, p)
    }

    /**
     * Tutup dialog otomatis saat Activity pemiliknya di-destroy (mis. rotasi layar),
     * agar tidak "leaked window" dan coroutine milik dialog ikut berhenti lewat onDismiss.
     */
    fun bindToLifecycle(dialog: Dialog, context: Context) {
        var ctx: Context? = context
        while (ctx is android.content.ContextWrapper && ctx !is androidx.lifecycle.LifecycleOwner) ctx = ctx.baseContext
        val owner = ctx as? androidx.lifecycle.LifecycleOwner ?: return
        val observer = object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onDestroy(owner: androidx.lifecycle.LifecycleOwner) {
                if (dialog.isShowing) dialog.dismiss()
            }
        }
        owner.lifecycle.addObserver(observer)
        // Tidak memakai setOnDismissListener agar tidak menimpa listener milik pemanggil
        dialog.window?.decorView?.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {
                owner.lifecycle.removeObserver(observer)
                v.removeOnAttachStateChangeListener(this)
            }
        })
    }

    /** Latar transparan + lebar responsif (portrait / landscape). */
    fun applyWindow(dialog: Dialog, context: Context, portraitWidth: Double, landscapeWidth: Double) {
        bindToLifecycle(dialog, context)
        val dm = context.resources.displayMetrics
        val isPortrait = context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (dm.widthPixels * if (isPortrait) portraitWidth else landscapeWidth).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }
}

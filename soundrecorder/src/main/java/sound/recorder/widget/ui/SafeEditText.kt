package sound.recorder.widget.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.Spannable
import android.text.method.ArrowKeyMovementMethod
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.widget.TextView
import androidx.appcompat.widget.AppCompatEditText
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Crash Crashlytics Darbuka (OPPO CPH1803, Android 8.1, app 1.95): saat pengguna menyentuh EditText di dalam dialog,
 * `IndexOutOfBoundsException: setSpan (-1 ... -1) starts before 0` dilempar dari
 * `ArrowKeyMovementMethod.onTouchEvent` -> `Selection.setSelection`. Android menghitung posisi karakter yang disentuh = -1
 * (layout teks belum siap / ColorOS Android 8.x). Penyebabnya ada di sistem, bukan di kode app, jadi tidak bisa dihilangkan
 * dari sisi kita; ini jaring pengaman: sentuhan itu diabaikan, app tidak crash. Dicatat SEKALI per proses sebagai non-fatal
 * di Crashlytics (supaya jumlah kejadiannya tetap terlihat tanpa membanjiri).
 *
 * Pakai salah satunya:
 *  - [SafeEditText] / [SafeTextInputEditText] di XML (pengganti `<EditText>` / `<TextInputEditText>`, tampilan sama),
 *  - [makeTouchSafe] pada EditText yang dibuat lewat kode (`EditText(context).apply { makeTouchSafe(); ... }`).
 */
class SafeArrowKeyMovementMethod : ArrowKeyMovementMethod() {

    override fun onTouchEvent(widget: TextView, text: Spannable, event: MotionEvent): Boolean =
        try {
            super.onTouchEvent(widget, text, event)
        } catch (e: IndexOutOfBoundsException) {
            report(e)
            false
        } catch (e: NullPointerException) {
            report(e)
            false
        }

    companion object {
        private val reported = AtomicBoolean(false)
        private val shared by lazy { SafeArrowKeyMovementMethod() }

        @JvmStatic fun getInstance(): SafeArrowKeyMovementMethod = shared

        fun report(e: RuntimeException) {
            Log.w("SafeEditText", "Sentuhan EditText diabaikan: ${e.message}")
            if (reported.compareAndSet(false, true)) {
                runCatching { FirebaseCrashlytics.getInstance().recordException(e) }
            }
        }
    }
}

/** Memasang [SafeArrowKeyMovementMethod] pada EditText ini. Aman dipanggil berulang. */
fun android.widget.EditText.makeTouchSafe() {
    if (movementMethod !is SafeArrowKeyMovementMethod) {
        movementMethod = SafeArrowKeyMovementMethod.getInstance()
    }
    hideInsertionHandle()
}

/**
 * Crash Crashlytics (Samsung Android 15): `NullPointerException: Magnifier.getPosition()` dari
 * `Editor$InsertionHandleView.onTouchEvent` / `PopupWindow$PopupDecorView`. Bug framework: kaca pembesar (Magnifier) dipakai
 * saat handle kursor digeser. Stack trace lewat PopupWindow milik handle, jadi tidak bisa ditangkap dari movement method.
 * Mitigasi: di API 29+ handle kursor (teardrop) diganti drawable transparan, sehingga pengguna tidak bisa menggeser handle
 * dan Magnifier tidak terpakai. Kursor tetap bisa dipindah dengan mengetuk teks; handle pilihan teks (kiri/kanan) tidak diubah.
 */
fun android.widget.EditText.hideInsertionHandle() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
    runCatching {
        val size = (resources.displayMetrics.density * 24).toInt()
        setTextSelectHandle(GradientDrawable().apply {
            setSize(size, size)
            setColor(Color.TRANSPARENT)
        })
    }
}

/** `AppCompatEditText` (yang dipakai AppCompat untuk `<EditText>` di XML) dengan sentuhan yang aman. */
open class SafeEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyleAttr) {
    init { makeTouchSafe() }

    override fun onTouchEvent(event: MotionEvent): Boolean =
        try { super.onTouchEvent(event) } catch (e: NullPointerException) {
            SafeArrowKeyMovementMethod.report(e); false
        }
}

/** `TextInputEditText` dengan sentuhan yang aman, untuk dipakai di dalam `TextInputLayout`. */
open class SafeTextInputEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.editTextStyle
) : TextInputEditText(context, attrs, defStyleAttr) {
    init { makeTouchSafe() }

    override fun onTouchEvent(event: MotionEvent): Boolean =
        try { super.onTouchEvent(event) } catch (e: NullPointerException) {
            SafeArrowKeyMovementMethod.report(e); false
        }
}

package sound.recorder.widget.ui.dialog

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.intuit.sdp.R as SdpR
import sound.recorder.widget.R
import sound.recorder.widget.builder.ZaifSDKBuilder
import sound.recorder.widget.music.MusicListDialogHelper
import sound.recorder.widget.recording.RecordingListDialogHelper.zaifSDKConfig

class ReportBugDialog {

    private fun Context.sdp(id: Int)    = resources.getDimensionPixelSize(id)

    @SuppressLint("UseKtx")
    fun show(context: Context) {
        zaifSDKConfig = ZaifSDKBuilder.load(context)
        showSongRequestDialog(context)
    }

    @SuppressLint("UseKtx")
    private fun showSongRequestDialog(context: Context) {
        val d = AlertDialog.Builder(context).create()

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0C1628"))
                cornerRadius = 48f
                setStroke(2, Color.parseColor("#FF6B35"))
            }
            val p = 56
            setPadding(p, p, p, p)
        }

        root.addView(TextView(context).apply {
            text = context.getString(R.string.report)
            setTextColor(Color.parseColor("#FF6B35"))
            textSize = 16f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = 24
            layoutParams = lp
        })


        root.addView(TextView(context).apply {
            text = context.getString(R.string.bug_info)
            setTextColor(Color.parseColor("#BBBBBB"))
            textSize = 12f
            setLineSpacing(0f, 1.4f)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = 20
            layoutParams = lp
        })

        val etTitle = EditText(context).apply {
            hint = context.getString(R.string.bug_name)
            setHintTextColor(Color.parseColor("#666666"))
            setTextColor(Color.WHITE)
            textSize = 14f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            filters = arrayOf(InputFilter.LengthFilter(100))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A2A40"))
                cornerRadius = 16f
                setStroke(1, Color.parseColor("#334466"))
            }
            val p = 28
            setPadding(p, p, p, p)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = 28
            layoutParams = lp
        }
        root.addView(etTitle)

        val etDescription = EditText(context).apply {
            hint = context.getString(R.string.bug_description)
            setHintTextColor(Color.parseColor("#666666"))
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.TOP
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            filters = arrayOf(InputFilter.LengthFilter(400))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A2A40"))
                cornerRadius = 16f
                setStroke(1, Color.parseColor("#334466"))
            }
            val p = 28
            val tinggi = context.sdp(SdpR.dimen._64sdp)
            setPadding(p, p, p, p)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                tinggi
            )
            lp.bottomMargin = 28
            layoutParams = lp
        }
        root.addView(etDescription)

        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }

        btnRow.addView(TextView(context).apply {
            text = context.getString(R.string.cancel)
            setTextColor(Color.parseColor("#888888"))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            val pH = 40; val pV = 24
            setPadding(pH, pV, pH, pV)
            setOnClickListener { d.dismiss() }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.marginEnd = 16
            layoutParams = lp
        })

        btnRow.addView(TextView(context).apply {
            text = context.getString(R.string.send)
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            val pH = 48; val pV = 24
            setPadding(pH, pV, pH, pV)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#FF6B35"))
                cornerRadius = 16f
            }
            setOnClickListener {
                val title = etTitle.text.toString().trim()
                val desc = etDescription.text.toString().trim()
                if (title.isEmpty()) {
                    //etTitle.error = context.getString(R.string.song_title_cannot_empty)
                    Toast.makeText(context,context.getString(R.string.bug_name_cannot_empty),
                        Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (desc.isEmpty()) {
                    //etTitle.error = context.getString(R.string.song_title_cannot_empty)
                    Toast.makeText(context,context.getString(R.string.desc_bug_cannot_empty),
                        Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                d.dismiss()
                submitSongRequest(context, title,desc)
            }
        })

        root.addView(btnRow)
        d.setView(root)
        d.show()
        d.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (context.resources.displayMetrics.widthPixels * 0.85).toInt(),
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        etTitle.requestFocus()
    }

    private fun submitSongRequest(context: Context, bugTitle: String,bugDescription :String ) {
        val config = ZaifSDKBuilder.load(context)

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            val token = if (task.isSuccessful) task.result else "unknown"

            val data = hashMapOf(
                "bug_title"    to bugTitle,
                "requested_at"  to System.currentTimeMillis(),
                "bug_description"  to bugDescription,
                "status"        to "pending",
                "app_id"        to config?.applicationId,
                "firebaseToken" to token,
                "deviceInfo"    to getInfo()
            )

            FirebaseFirestore.getInstance()
                .collection("bug_reports")
                .add(data)
                .addOnSuccessListener {
                    Toast.makeText(context, context.getString(R.string.report_bug_sent), Toast.LENGTH_LONG).show()
                }
                .addOnFailureListener {
                    Toast.makeText(context, context.getString(R.string.report_bug_failed), Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun getInfo(): String {
        val appInfo = "VC" + zaifSDKConfig?.versionCode
        val androidVersion = "SDK" + Build.VERSION.SDK_INT
        val androidOS = "OS" + Build.VERSION.RELEASE

        return Build.MANUFACTURER + " " + Build.MODEL + " , " + androidOS + ", " + appInfo + ", " + androidVersion
    }

}
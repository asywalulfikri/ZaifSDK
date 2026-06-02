package sound.recorder.widget.util

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import sound.recorder.widget.R
import com.intuit.sdp.R as SdpR

object NotificationBannerHelper {

    private const val PREF_KEY_SEEN = "promotion_banner_seen"
    private const val PREF_NAME     = "recordingWidget"

    // Gaming Palette
    private const val BG_TOP    = "#0F0C29"   // space blue
    private const val BG_MID    = "#302B63"   // deep purple
    private const val BG_BTM    = "#24243E"   // midnight blue
    private const val NEON_CYAN = "#00F2FF"   // neon cyan
    private const val NEON_MAG  = "#FF00FF"   // neon magenta
    private const val GOLD      = "#FFE000"   // bright gold
    private const val BODY      = "#FFFFFF"   // white text

    private fun Context.sdp(id: Int)  = resources.getDimensionPixelSize(id)
    private fun Context.sdpF(id: Int) = resources.getDimension(id)

    fun showIfNotSeen(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(PREF_KEY_SEEN, false)) return

        val dialog = AlertDialog.Builder(context).create()
        val metrics = context.resources.displayMetrics

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor(BG_TOP), Color.parseColor(BG_MID), Color.parseColor(BG_BTM))
            ).apply {
                cornerRadius = context.sdpF(SdpR.dimen._20sdp)
                setStroke(context.sdp(SdpR.dimen._2sdp), GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(Color.parseColor(NEON_CYAN), Color.parseColor(NEON_MAG))
                ).let { Color.parseColor(NEON_CYAN) }) // Simplified stroke for now
                // Let's use a solid neon stroke for simplicity in code-based UI
                setStroke(context.sdp(SdpR.dimen._1sdp), Color.parseColor(NEON_CYAN))
            }
        }

        // ─── Header ───
        val padH = context.sdp(SdpR.dimen._12sdp)
        val padV = context.sdp(SdpR.dimen._10sdp)
        val header = FrameLayout(context)

        val headerContent = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padH, padV, padH, context.sdp(SdpR.dimen._6sdp))

            addView(TextView(context).apply {
                text = context.getString(R.string.system_notification)
                setTextColor(Color.parseColor(NEON_CYAN))
                textSize = 9f
                letterSpacing = 0.3f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            })
            addView(TextView(context).apply {
                text = context.getString(R.string.notification).uppercase()
                setTextColor(Color.parseColor(GOLD))
                textSize = 17f
                typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
                setShadowLayer(8f, 0f, 0f, Color.parseColor(GOLD))
                val topPad = context.sdp(SdpR.dimen._2sdp)
                setPadding(0, topPad, 0, 0)
            })
        }

        val closeSize   = context.sdp(SdpR.dimen._24sdp)
        val closeMargin = context.sdp(SdpR.dimen._6sdp)
        val closeBtn = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(closeSize, closeSize).apply {
                gravity = Gravity.TOP or Gravity.END
                setMargins(0, closeMargin, closeMargin, 0)
            }
            background = RippleDrawable(
                ColorStateList.valueOf(Color.parseColor("#4000F2FF")), null, 
                GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) }
            )
            addView(TextView(context).apply {
                text = "✕"
                setTextColor(Color.parseColor(NEON_CYAN))
                textSize = 14f
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(-1, -1)
            })
            setOnClickListener { markSeen(context); dialog.dismiss() }
        }

        header.addView(headerContent)
        header.addView(closeBtn)
        root.addView(header)

        // ─── Glow divider ───
        root.addView(buildDivider(context, NEON_CYAN))

        // ─── Scrollable body ───
        val scrollView = ScrollView(context).apply { isVerticalScrollBarEnabled = false }
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padH, context.sdp(SdpR.dimen._8sdp), padH, context.sdp(SdpR.dimen._8sdp))
        }

        body.addView(TextView(context).apply {
            text = context.getString(R.string.promotion_my_note_info)
            setTextColor(Color.parseColor(BODY))
            textSize = 12f
            setLineSpacing(0f, 1.3f)
        })

        body.addView(LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(-1, context.sdp(SdpR.dimen._8sdp))
        })

        body.addView(buildNoteChip(context, context.getString(R.string.not_approve_notification)))

        scrollView.addView(body)
        root.addView(scrollView, LinearLayout.LayoutParams(-1, (metrics.heightPixels * 0.28).toInt()))

        // ─── Glow divider ───
        root.addView(buildDivider(context, NEON_MAG))

        // ─── Confirm button ───
        val btnContainer = FrameLayout(context).apply {
            setPadding(padH, context.sdp(SdpR.dimen._8sdp), padH, context.sdp(SdpR.dimen._10sdp))
        }
        btnContainer.addView(TextView(context).apply {
            text = context.getString(R.string.understand).uppercase()
            setTextColor(Color.BLACK)
            textSize = 13f
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            letterSpacing = 0.15f
            setPadding(0, context.sdp(SdpR.dimen._8sdp), 0, context.sdp(SdpR.dimen._8sdp))
            
            val normalBg = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor(NEON_CYAN), Color.parseColor(NEON_MAG))
            ).apply { cornerRadius = context.sdpF(SdpR.dimen._24sdp) }
            
            background = RippleDrawable(
                ColorStateList.valueOf(Color.parseColor("#40FFFFFF")),
                normalBg,
                null
            )
            setOnClickListener { markSeen(context); dialog.dismiss() }
        })
        root.addView(btnContainer)

        dialog.setView(root)
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setLayout(
                (metrics.widthPixels * 0.82).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            attributes?.windowAnimations = android.R.style.Animation_Dialog
        }
    }

    private fun markSeen(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(PREF_KEY_SEEN, true).apply()
    }

    private fun buildDivider(context: Context, colorStr: String) = LinearLayout(context).apply {
        layoutParams = LinearLayout.LayoutParams(-1, context.sdp(SdpR.dimen._2sdp))
        background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.TRANSPARENT, Color.parseColor(colorStr), Color.TRANSPARENT)
        )
    }

    private fun buildNoteChip(context: Context, text: String) = TextView(context).apply {
        this.text = "❖  $text"
        setTextColor(Color.parseColor(NEON_CYAN))
        textSize = 11f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setLineSpacing(0f, 1.3f)
        val pad = context.sdp(SdpR.dimen._10sdp)
        setPadding(pad, pad, pad, pad)
        background = GradientDrawable().apply {
            setColor(Color.parseColor("#1500F2FF"))
            cornerRadius = context.sdpF(SdpR.dimen._8sdp)
            setStroke(
                context.sdp(SdpR.dimen._1sdp),
                Color.parseColor("#4000F2FF")
            )
        }
    }
}
package sound.recorder.widget.recording

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.cardview.widget.CardView
import androidx.core.content.FileProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sound.recorder.widget.R
import sound.recorder.widget.builder.ZaifSDKBuilder
import sound.recorder.widget.builder.ZaifSDKConfig
import sound.recorder.widget.recording.database.AppDatabase
import sound.recorder.widget.recording.database.RecordingEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.intuit.sdp.R as SdpR

object RecordingListDialogHelper {

    private const val COLOR_WOOD_DARK  = "1A0F09"
    private const val COLOR_WOOD_MED   = "2D1B10"
    private const val COLOR_GOLD       = "D2B48C"
    private const val COLOR_IVORY      = "F5F5DC"
    private const val COLOR_GOLD_DIM   = "8A7456"
    private const val COLOR_DANGER     = "CF6679"
    private const val COLOR_WOOD_LIGHT = "3E2717"
    private const val COLOR_SHARE      = "4A9EBF"
    private const val COLOR_JSON       = "81C784"
    private const val COLOR_PROMOTE    = "FFB347"

    private const val MAX_NAME_LENGTH  = 30
    private const val PREFS_PROMOTED      = "zaif_promoted_notes"
    private const val KEY_PROMOTED_IDS   = "promoted_ids"
    private const val KEY_LAST_PROMO_DATE = "last_promo_date"

    // ─── Helper shorthand ───
    private fun Context.sdp(id: Int) = resources.getDimensionPixelSize(id)
    private fun Context.sdpF(id: Int) = resources.getDimension(id)

    private fun todayString() = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    private fun hasPromotedToday(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_PROMOTED, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LAST_PROMO_DATE, null) == todayString()
    }

    private fun markAsPromoted(context: Context, recordId: Int) {
        val prefs = context.getSharedPreferences(PREFS_PROMOTED, Context.MODE_PRIVATE)
        val ids   = prefs.getStringSet(KEY_PROMOTED_IDS, mutableSetOf())!!.toMutableSet()
        ids.add(recordId.toString())
        prefs.edit()
            .putStringSet(KEY_PROMOTED_IDS, ids)
            .putString(KEY_LAST_PROMO_DATE, todayString())
            .apply()
    }

    private fun isPromoted(context: Context, recordId: Int): Boolean {
        val prefs = context.getSharedPreferences(PREFS_PROMOTED, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_PROMOTED_IDS, emptySet())!!.contains(recordId.toString())
    }
    var zaifSDKConfig : ZaifSDKConfig? =null

    fun show(context: Context, instrumentFilter: String, onPlay: (RecordingEntity) -> Unit) {
        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#$COLOR_WOOD_DARK"))
                cornerRadius = context.sdpF(SdpR.dimen._14sdp)
                setStroke(
                    context.sdp(SdpR.dimen._1sdp),
                    Color.parseColor("#22D2B48C")
                )
            }
        }

        val headerContainer = FrameLayout(context)
        val headerPad = context.sdp(SdpR.dimen._16sdp)
        val headerPadB = context.sdp(SdpR.dimen._8sdp)
        val headerTextLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(headerPad, headerPad, headerPad, headerPadB)
        }

        headerTextLayout.addView(TextView(context).apply {
            text = context.getString(R.string.record_saved)
            setTextColor(Color.parseColor("#$COLOR_GOLD"))
            textSize = 15f
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        })

        headerTextLayout.addView(TextView(context).apply {
            text = context.getString(R.string.list_record_result)
            setTextColor(Color.parseColor("#$COLOR_GOLD_DIM"))
            textSize = 10f
        })

        val dialog = AlertDialog.Builder(context).create()

        val closeBtnSize = context.sdp(SdpR.dimen._32sdp)
        val closeBtnMargin = context.sdp(SdpR.dimen._8sdp)
        val closeBtn = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(closeBtnSize, closeBtnSize).apply {
                gravity = Gravity.TOP or Gravity.END
                setMargins(0, closeBtnMargin, closeBtnMargin, 0)
            }
            background = RippleDrawable(
                ColorStateList.valueOf(Color.parseColor("#20FFFFFF")), null, null
            )
            addView(TextView(context).apply {
                text = "✕"
                setTextColor(Color.parseColor("#$COLOR_GOLD_DIM"))
                textSize = 16f
                gravity = Gravity.CENTER
            })
            setOnClickListener { dialog.dismiss() }
        }

        headerContainer.addView(headerTextLayout)
        headerContainer.addView(closeBtn)
        rootLayout.addView(headerContainer)

        val scrollView = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        val listPadH = context.sdp(SdpR.dimen._12sdp)
        val listPadV = context.sdp(SdpR.dimen._8sdp)
        val listLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(listPadH, listPadV, listPadH, listPadV)
        }
        scrollView.addView(listLayout)

        val displayMetrics = context.resources.displayMetrics
        val maxHeight = (displayMetrics.heightPixels * 0.75).toInt()
        rootLayout.addView(scrollView, LinearLayout.LayoutParams(-1, maxHeight))

        dialog.setView(rootLayout)

        CoroutineScope(Dispatchers.Main).launch {
            val recordings = withContext(Dispatchers.IO) {
                AppDatabase.getInstance(context).recordingDao().getAll()
            }

            val filtered = recordings.filter {
                it.setName.equals(instrumentFilter, ignoreCase = true)
            }

            if (filtered.isEmpty()) {
                listLayout.addView(showEmptyState(context))
            } else {
                val spacerH = context.sdp(SdpR.dimen._8sdp)
                filtered.forEach { rec ->
                    listLayout.addView(
                        buildRecordingCard(context, rec, dialog, onPlay, listLayout, filtered.toMutableList())
                    )
                    listLayout.addView(View(context).apply {
                        layoutParams = LinearLayout.LayoutParams(-1, spacerH)
                    })
                }
            }
        }

        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (displayMetrics.widthPixels * 0.92).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun buildRecordingCard(
        context: Context,
        rec: RecordingEntity,
        dialog: AlertDialog,
        onPlay: (RecordingEntity) -> Unit,
        listLayout: LinearLayout,
        allRecs: MutableList<RecordingEntity>
    ): View {
        val card = CardView(context).apply {
            radius = context.sdpF(SdpR.dimen._10sdp)
            cardElevation = 0f
            setCardBackgroundColor(Color.parseColor("#$COLOR_WOOD_MED"))
        }

        // Single horizontal row — hemat tinggi untuk landscape
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val pH = context.sdp(SdpR.dimen._10sdp)
            val pV = context.sdp(SdpR.dimen._8sdp)
            setPadding(pH, pV, pH, pV)
        }

        val hasMicAudio = !rec.audioPath.isNullOrEmpty()
        val sdf         = SimpleDateFormat("dd/MM  HH:mm", Locale.getDefault())
        val iconType    = if (hasMicAudio) "🎙" else "⏺"

        // ── Info: ikon + nama + tanggal ──────────────────────────
        val nameTv = TextView(context).apply {
            text = rec.name.uppercase()
            setTextColor(Color.parseColor("#$COLOR_IVORY"))
            textSize = 12f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        row.addView(TextView(context).apply {
            text = iconType
            textSize = 16f
            setPadding(0, 0, context.sdp(SdpR.dimen._8sdp), 0)
        })

        row.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            addView(nameTv)
            addView(TextView(context).apply {
                text = "${sdf.format(Date(rec.createdAt))} · ${formatDuration(rec.durationMs)}"
                setTextColor(Color.parseColor("#$COLOR_GOLD_DIM"))
                textSize = 8f
            })
        })

        // ── Divider vertikal ─────────────────────────────────────
        row.addView(View(context).apply {
            val h = context.sdp(SdpR.dimen._28sdp)
            layoutParams = LinearLayout.LayoutParams(1, h).also {
                it.marginStart = context.sdp(SdpR.dimen._8sdp)
                it.marginEnd   = context.sdp(SdpR.dimen._8sdp)
            }
            setBackgroundColor(Color.parseColor("#33D2B48C"))
        })

        // ── Tombol PLAY — menonjol ───────────────────────────────
        row.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            val btnH = context.sdp(SdpR.dimen._32sdp)
            layoutParams = LinearLayout.LayoutParams(-2, btnH)
            val pH = context.sdp(SdpR.dimen._12sdp)
            setPadding(pH, 0, pH, 0)
            background = RippleDrawable(
                ColorStateList.valueOf(Color.parseColor("#50FFFFFF")),
                GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(Color.parseColor("#B8720A"), Color.parseColor("#F0B429"))
                ).apply { cornerRadius = context.sdpF(SdpR.dimen._8sdp) },
                null
            )
            addView(TextView(context).apply {
                text = "▶"
                textSize = 13f
                setTextColor(Color.parseColor("#1A0800"))
                setPadding(0, 0, context.sdp(SdpR.dimen._6sdp), 0)
            })
            addView(TextView(context).apply {
                text = "Play"
                setTextColor(Color.parseColor("#1A0800"))
                textSize = 12f
                typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            })
            setOnClickListener { dialog.dismiss(); onPlay(rec) }
        })

        // ── Tombol sekunder kecil ────────────────────────────────
        val gap = context.sdp(SdpR.dimen._6sdp)

        fun addCompact(icon: String, label: String, colorHex: String, onClick: () -> Unit) {
            row.addView(spacer(context, gap))
            row.addView(buildCompactBtn(context, icon, label, colorHex, onClick))
        }

        if (hasMicAudio) addCompact("📤", "Bagikan", COLOR_SHARE) { shareAudioFile(context, rec) }

        val isDebug = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (isDebug) addCompact("{}", "JSON", COLOR_JSON) { shareJsonFile(context, rec) }

        addCompact("✏", "Edit", COLOR_GOLD) { showRenameDialog(context, rec, nameTv) }

        addCompact("🗑", "Hapus", COLOR_DANGER) {
            CoroutineScope(Dispatchers.Main).launch {
                withContext(Dispatchers.IO) {
                    rec.audioPath?.let { path -> File(path).takeIf { it.exists() }?.delete() }
                    AppDatabase.getInstance(context).recordingDao().delete(rec)
                }
                val idx = listLayout.indexOfChild(card)
                if (idx != -1) {
                    listLayout.removeViewAt(idx)
                    if (idx < listLayout.childCount) listLayout.removeViewAt(idx)
                }
                allRecs.remove(rec)
                if (allRecs.isEmpty()) listLayout.addView(showEmptyState(context))
            }
        }

        // ── Promosi: container yang bisa di-swap tanpa rebuild card ──
        val promoteContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        fun applyPromoteState(promoted: Boolean) {
            promoteContainer.removeAllViews()
            if (promoted) {
                promoteContainer.addView(buildCompactBtn(context, "✓", "Dipromot", "777777") {
                    Toast.makeText(context, "Not ini sudah pernah dipromot", Toast.LENGTH_SHORT).show()
                })
            } else {
                promoteContainer.addView(buildCompactBtn(context, "🌟", "Promosi", COLOR_PROMOTE) {
                    if (hasPromotedToday(context)) {
                        Toast.makeText(context, "Kamu hanya bisa promot 1 not per hari", Toast.LENGTH_SHORT).show()
                    } else {
                        showPromoteDialog(context, rec) { applyPromoteState(true) }
                    }
                })
            }
        }

        if (zaifSDKConfig?.isPromotNot == true) {
            row.addView(spacer(context, gap))
            row.addView(promoteContainer)
            applyPromoteState(isPromoted(context, rec.id))
        }

        card.addView(row)
        return card
    }

    private fun buildCompactBtn(
        context: Context,
        icon: String,
        label: String,
        colorHex: String,
        onClick: () -> Unit
    ) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        val size = context.sdp(SdpR.dimen._32sdp)
        layoutParams = LinearLayout.LayoutParams(size, size)
        background = RippleDrawable(
            ColorStateList.valueOf(Color.parseColor("#40FFFFFF")),
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#25$colorHex"))
                setStroke(context.sdp(SdpR.dimen._1sdp), Color.parseColor("#$colorHex"))
            },
            null
        )
        addView(TextView(context).apply {
            text = icon
            textSize = 11f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(-2, -2).also { it.gravity = Gravity.CENTER_HORIZONTAL }
        })
        addView(TextView(context).apply {
            text = label
            setTextColor(Color.parseColor("#$colorHex"))
            textSize = 6f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(-2, -2).also { it.gravity = Gravity.CENTER_HORIZONTAL }
        })
        setOnClickListener { onClick() }
    }

    // ─── PROMOTE ─────────────────────────────────────────────────────────────

    private fun showPromoteDialog(context: Context, rec: RecordingEntity, onPromoted: () -> Unit = {}) {
        val dialog = AlertDialog.Builder(context).create()

        val padH = context.sdp(SdpR.dimen._16sdp)
        val padV = context.sdp(SdpR.dimen._12sdp)
        zaifSDKConfig = ZaifSDKBuilder.load(context)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#$COLOR_WOOD_DARK"))
                cornerRadius = context.sdpF(SdpR.dimen._14sdp)
                setStroke(context.sdp(SdpR.dimen._1sdp), Color.parseColor("#22D2B48C"))
            }
            setPadding(padH, padV, padH, padV)
        }

        root.addView(TextView(context).apply {
            text = context.getString(R.string.promosikan)
            setTextColor(Color.parseColor("#$COLOR_GOLD"))
            textSize = 15f
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        })

        root.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(-1, context.sdp(SdpR.dimen._8sdp))
        })

        root.addView(TextView(context).apply {
            text = context.getString(R.string.promot_info)
            setTextColor(Color.parseColor("#$COLOR_IVORY"))
            textSize = 12f
            setLineSpacing(0f, 1.4f)
        })

        root.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(-1, context.sdp(SdpR.dimen._12sdp))
        })

        val nameInput = EditText(context).apply {
            hint = context.getString(R.string.your_name)
            setHintTextColor(Color.parseColor("#$COLOR_GOLD_DIM"))
            setTextColor(Color.parseColor("#$COLOR_IVORY"))
            textSize = 13f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#$COLOR_WOOD_MED"))
                cornerRadius = context.sdpF(SdpR.dimen._8sdp)
                setStroke(context.sdp(SdpR.dimen._1sdp), Color.parseColor("#44D2B48C"))
            }
            val ep = context.sdp(SdpR.dimen._10sdp)
            setPadding(ep, ep, ep, ep)
        }
        root.addView(nameInput, LinearLayout.LayoutParams(-1, -2))

        root.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(-1, context.sdp(SdpR.dimen._12sdp))
        })

        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }

        btnRow.addView(buildTextButton(context, context.getString(R.string.cancel), COLOR_GOLD_DIM) {
            dialog.dismiss()
        })

        btnRow.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(context.sdp(SdpR.dimen._8sdp), 1)
        })

        btnRow.addView(buildTextButton(context, context.getString(R.string.send), COLOR_PROMOTE) {
            val senderName = nameInput.text.toString().trim()
            sendNoteToFirestore(context, rec, senderName, onPromoted)
            dialog.dismiss()
        })

        root.addView(btnRow)

        dialog.setView(root)
        dialog.show()
        val dm = context.resources.displayMetrics
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout((dm.widthPixels * 0.88).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    private fun sendNoteToFirestore(context: Context, rec: RecordingEntity, senderName: String, onSuccess: () -> Unit = {}) {
        val jsonNote = """
            {
              "id": ${rec.id},
              "name": "${rec.name}",
              "category": "${rec.setName}",
              "events": ${rec.eventsJson},
              "audio_path": "${rec.audioPath ?: ""}",
              "created_at": ${rec.createdAt},
              "duration_ms": ${rec.durationMs},
              "is_earphone": ${rec.isEarphoneRecording}
            }
        """.trimIndent()

        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            val appId = zaifSDKConfig?.applicationId ?: return@addOnSuccessListener
            val data = hashMapOf(
                "sender_name"    to senderName,
                "firebase_token" to token,
                "json_note"      to jsonNote,
                "record_name"    to rec.name,
                "category"       to rec.setName,
                "language"   to listOf(getLanguageCode()),
                "appId"          to appId,
                "status"         to "DRAFT",
                "deviceInfo"     to getInfo(),
                "submitted_at"   to System.currentTimeMillis()
            )

            FirebaseFirestore.getInstance()
                .collection(appId)
                .add(data)
                .addOnSuccessListener {
                    markAsPromoted(context, rec.id)
                    onSuccess()
                    Toast.makeText(context, context.getString(R.string.send_note_success), Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    Toast.makeText(context, context.getString(R.string.send_note_failed), Toast.LENGTH_SHORT).show()
                }
        }.addOnFailureListener {
            Toast.makeText(context, context.getString(R.string.send_note_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun getInfo(): String {
        val appInfo = "VC" + zaifSDKConfig?.versionCode
        val androidVersion = "SDK" + Build.VERSION.SDK_INT
        val androidOS = "OS" + Build.VERSION.RELEASE

        return Build.MANUFACTURER + " " + Build.MODEL + " , " + androidOS + ", " + appInfo + ", " + androidVersion
    }
    fun getLanguageCode():String{
        val config = Resources.getSystem().configuration
        val locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.locales[0]
        } else {
            config.locale
        }

        return locale.language
    }
    private fun buildTextButton(
        context: Context,
        label: String,
        colorHex: String,
        onClick: () -> Unit
    ) = TextView(context).apply {
        text = label
        setTextColor(Color.parseColor("#$colorHex"))
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        val padH = context.sdp(SdpR.dimen._12sdp)
        val padV = context.sdp(SdpR.dimen._8sdp)
        setPadding(padH, padV, padH, padV)
        background = RippleDrawable(
            ColorStateList.valueOf(Color.parseColor("#20FFFFFF")), null, null
        )
        setOnClickListener { onClick() }
    }

    // ─── SHARE ───────────────────────────────────────────────────────────────

    private fun shareAudioFile(context: Context, rec: RecordingEntity) {
        val path = rec.audioPath ?: return
        val file = File(path)
        if (!file.exists()) {
            android.widget.Toast.makeText(
                context, context.getString(R.string.file_not_found), android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }
        try {
            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.provider", file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/mp4"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, rec.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, rec.name))
        } catch (e: Exception) {
            android.widget.Toast.makeText(
                context, context.getString(R.string.share_failed), android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    // ─── EXPORT JSON ─────────────────────────────────────────────────────────

    private fun shareJsonFile(context: Context, rec: RecordingEntity) {
        val jsonContent = """
            {
              "id": ${rec.id},
              "name": "${rec.name}",
              "category": "${rec.setName}",
              "events": ${rec.eventsJson},
              "audio_path": "${rec.audioPath ?: ""}",
              "created_at": ${rec.createdAt},
              "duration_ms": ${rec.durationMs},
              "is_earphone": ${rec.isEarphoneRecording}
            }
        """.trimIndent()

        val fileName = "Export_${rec.name.replace(Regex("[^a-zA-Z0-9]"), "_")}.json"
        val file = File(context.cacheDir, fileName)
        try {
            file.writeText(jsonContent)
            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.provider", file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "JSON: ${rec.name}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                // Specific for WhatsApp if you want, but general is better
                // setPackage("com.whatsapp") // Optional: force WhatsApp
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share JSON via"))
        } catch (e: Exception) {
            android.widget.Toast.makeText(
                context, "Export Failed: ${e.message}", android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    // ─── BUTTON BUILDERS ─────────────────────────────────────────────────────

    private fun buildShareButton(
        context: Context,
        size: Int,
        onClick: () -> Unit
    ) = FrameLayout(context).apply {
        layoutParams = LinearLayout.LayoutParams(size, size)
        background = RippleDrawable(
            ColorStateList.valueOf(Color.parseColor("#40FFFFFF")),
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#20$COLOR_SHARE"))
                setStroke(context.sdp(SdpR.dimen._1sdp), Color.parseColor("#$COLOR_SHARE"))
            },
            null
        )
        val pad = context.sdp(SdpR.dimen._6sdp)
        addView(android.widget.ImageView(context).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            setImageResource(R.drawable.ic_shared_forward)
            setColorFilter(Color.parseColor("#$COLOR_SHARE"))
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            setPadding(pad, pad, pad, pad)
        })
        setOnClickListener { onClick() }
    }

    private fun buildIconButton(
        context: Context,
        icon: String,
        bgColor: String,
        iconColor: Int,
        size: Int,
        onClick: () -> Unit
    ) = FrameLayout(context).apply {
        layoutParams = LinearLayout.LayoutParams(size, size)
        background = RippleDrawable(
            ColorStateList.valueOf(Color.parseColor("#40FFFFFF")),
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(bgColor))
                if (bgColor.startsWith("#20")) setStroke(
                    context.sdp(SdpR.dimen._1sdp), iconColor
                )
            },
            null
        )
        addView(TextView(context).apply {
            text = icon
            setTextColor(iconColor)
            textSize = 9f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        })
        setOnClickListener { onClick() }
    }

    // ─── HELPERS ─────────────────────────────────────────────────────────────

    private fun spacer(context: Context, width: Int) = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(width, 1)
    }

    private fun showEmptyState(context: Context) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        val pad = context.sdp(SdpR.dimen._32sdp)
        setPadding(0, pad, 0, pad)
        addView(TextView(context).apply {
            text = "∅"
            setTextColor(Color.parseColor("#$COLOR_WOOD_LIGHT"))
            textSize = 36f
        })
        addView(TextView(context).apply {
            text = context.getString(R.string.no_data_record)
            setTextColor(Color.parseColor("#$COLOR_GOLD_DIM"))
            textSize = 11f
            val topPad = context.sdp(SdpR.dimen._8sdp)
            setPadding(0, topPad, 0, 0)
        })
    }

    private fun formatDuration(ms: Long): String {
        val sec = ms / 1000
        return if (sec < 60) "${sec}s" else "${sec / 60}m ${sec % 60}s"
    }

    // ─── RENAME ──────────────────────────────────────────────────────────────

    private fun showRenameDialog(context: Context, rec: RecordingEntity, nameTv: TextView) {
        val dialog = AlertDialog.Builder(context).create()
        val padH = context.sdp(SdpR.dimen._16sdp)
        val padV = context.sdp(SdpR.dimen._12sdp)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#$COLOR_WOOD_DARK"))
                cornerRadius = context.sdpF(SdpR.dimen._14sdp)
                setStroke(context.sdp(SdpR.dimen._1sdp), Color.parseColor("#22D2B48C"))
            }
            setPadding(padH, padV, padH, padV)
        }

        root.addView(TextView(context).apply {
            text = context.getString(R.string.edit_note)
            setTextColor(Color.parseColor("#$COLOR_GOLD"))
            textSize = 15f
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        })

        root.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(-1, context.sdp(SdpR.dimen._12sdp))
        })

        val counterTv = TextView(context).apply {
            text = "${rec.name.length}/15"
            setTextColor(Color.parseColor("#$COLOR_GOLD_DIM"))
            textSize = 10f
            gravity = Gravity.END
        }

        val nameInput = EditText(context).apply {
            setText(rec.name)
            setTextColor(Color.parseColor("#$COLOR_IVORY"))
            setHintTextColor(Color.parseColor("#$COLOR_GOLD_DIM"))
            textSize = 13f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            filters = arrayOf<InputFilter>(InputFilter.LengthFilter(MAX_NAME_LENGTH))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#$COLOR_WOOD_MED"))
                cornerRadius = context.sdpF(SdpR.dimen._8sdp)
                setStroke(context.sdp(SdpR.dimen._1sdp), Color.parseColor("#44D2B48C"))
            }
            val ep = context.sdp(SdpR.dimen._10sdp)
            setPadding(ep, ep, ep, ep)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val len = s?.length ?: 0
                    counterTv.text = "$len/$MAX_NAME_LENGTH"
                    counterTv.setTextColor(Color.parseColor(
                        if (len >= MAX_NAME_LENGTH) "#$COLOR_DANGER" else "#$COLOR_GOLD_DIM"
                    ))
                }
            })
        }

        root.addView(nameInput, LinearLayout.LayoutParams(-1, -2))

        root.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(-1, context.sdp(SdpR.dimen._4sdp))
        })
        root.addView(counterTv)

        root.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(-1, context.sdp(SdpR.dimen._12sdp))
        })

        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        btnRow.addView(buildTextButton(context, context.getString(R.string.cancel), COLOR_GOLD_DIM) {
            dialog.dismiss()
        })
        btnRow.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(context.sdp(SdpR.dimen._8sdp), 1)
        })
        btnRow.addView(buildTextButton(context, context.getString(R.string.save), COLOR_GOLD) {
            val newName = nameInput.text.toString().trim().ifEmpty { rec.name }
            CoroutineScope(Dispatchers.Main).launch {
                withContext(Dispatchers.IO) {
                    AppDatabase.getInstance(context).recordingDao().updateName(rec.id, newName)
                }
                nameTv.text = newName.uppercase()
            }
            dialog.dismiss()
        })
        root.addView(btnRow)

        dialog.setView(root)
        dialog.show()
        val dm = context.resources.displayMetrics
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout((dm.widthPixels * 0.88).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
            setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        }
        nameInput.requestFocus()
    }

    // ─── PILL BUTTON ─────────────────────────────────────────────────────────

    private fun buildTextPillButton(
        context: Context,
        label: String,
        colorHex: String,
        onClick: () -> Unit
    ) = TextView(context).apply {
        text = label
        setTextColor(Color.parseColor("#$colorHex"))
        textSize = 9f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        val padH = context.sdp(SdpR.dimen._8sdp)
        val padV = context.sdp(SdpR.dimen._4sdp)
        setPadding(padH, padV, padH, padV)
        background = RippleDrawable(
            ColorStateList.valueOf(Color.parseColor("#40FFFFFF")),
            GradientDrawable().apply {
                setColor(Color.parseColor("#20$colorHex"))
                cornerRadius = context.sdpF(SdpR.dimen._12sdp)
                setStroke(context.sdp(SdpR.dimen._1sdp), Color.parseColor("#$colorHex"))
            },
            null
        )
        setOnClickListener { onClick() }
    }
}
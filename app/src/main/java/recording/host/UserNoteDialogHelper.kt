package recording.host

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LifecycleCoroutineScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import sound.recorder.widget.recording.database.RecordedTap
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.intuit.sdp.R as SdpR

class UserNoteDialogHelper(
    private val lifecycleScope: LifecycleCoroutineScope,
    private val onTabSelect: (slendro: Boolean) -> Unit,
    private val onTriggerAnim: (padIndex: Int) -> Unit,
    private val onHighlight: (padIndex: Int) -> Unit,
    private val onClearHighlight: () -> Unit,
    private val onLearnStepUpdate: (step: Int, total: Int) -> Unit,
    private val onLearnVisible: (visible: Boolean) -> Unit,
    private val onPlaybackStatusChanged: (isPlaying: Boolean) -> Unit,
    private val onToast: (message: String) -> Unit,
    private val onRequestAd: (onComplete: () -> Unit) -> Unit = { it() }
) {

    private var mContext: Context? = null

    // ─── Cache ───────────────────────────────────────────────────

    data class NoteItem(
        val recordName: String,
        val senderName: String,
        val submittedAt: Long,
        val jsonNote: String
    )

    companion object {
        private const val CACHE_TTL_MS   = 15 * 60 * 1000L
        private const val UNLOCK_TTL_MS  = 7 * 24 * 60 * 60 * 1000L // 1 minggu
        private const val PREFS_UNLOCKED = "zaif_note_unlocks"

        private data class CachedResult(val notes: List<NoteItem>, val fetchedAt: Long)
        private val cache = mutableMapOf<String, CachedResult>()

        private fun isCacheValid(key: String): Boolean {
            val c = cache[key] ?: return false
            return System.currentTimeMillis() - c.fetchedAt < CACHE_TTL_MS
        }

        private fun noteUnlockKey(note: NoteItem) = "note_${note.submittedAt}"

        private fun isNoteUnlocked(context: Context, note: NoteItem): Boolean {
            val ts = context.getSharedPreferences(PREFS_UNLOCKED, Context.MODE_PRIVATE)
                .getLong(noteUnlockKey(note), -1L)
            return ts != -1L && System.currentTimeMillis() - ts < UNLOCK_TTL_MS
        }

        private fun markNoteUnlocked(context: Context, note: NoteItem) {
            context.getSharedPreferences(PREFS_UNLOCKED, Context.MODE_PRIVATE)
                .edit().putLong(noteUnlockKey(note), System.currentTimeMillis()).apply()
        }

        fun clearCache() = cache.clear()
        fun clearCache(instrumentType: String) { cache.remove(instrumentType) }
    }

    private var playJob: Job? = null
    var isLearning = false
        private set
    private var learnEvents = listOf<RecordedTap>()
    private var learnStep = 0
    private var learnTypeKey = ""

    // ─── Dialog full screen ──────────────────────────────────────

    fun show(context: Context, instrumentType: String) {
        this.mContext = context
        
        // 🔥 Pre-cache strings to avoid AssetManager deadlock on Vivo/Oppo/Realme
        val titleText    = context.getString(sound.recorder.widget.R.string.user_notes_title)
        val subtitleText = context.getString(sound.recorder.widget.R.string.user_notes_subtitle)
        val closeText    = context.getString(sound.recorder.widget.R.string.close)
        val searchHint   = context.getString(sound.recorder.widget.R.string.search_record)
        val errorMsg     = "Gagal memuat: "

        val dialog = AlertDialog.Builder(context, android.R.style.Theme_NoTitleBar_Fullscreen).create()
        val allNotes = mutableListOf<NoteItem>()

        // ── Root ──
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F0100510"))
            layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            isClickable = true
            isFocusable = true
        }

        // ── Header bar ──
        val header = FrameLayout(context).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#3E1A06"), Color.parseColor("#1A0803"))
            )
            val p = context.sdp(SdpR.dimen._12sdp)
            setPadding(p, p, p, p)
        }
        header.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).also {
                it.gravity = Gravity.CENTER_VERTICAL or Gravity.START
            }
            addView(TextView(context).apply {
                text = "♪  $titleText"
                setTextColor(Color.parseColor("#F0B429"))
                textSize = 16f
                typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            })
            addView(TextView(context).apply {
                text = subtitleText
                setTextColor(Color.parseColor("#8A7456"))
                textSize = 9f
            })
        })
        header.addView(TextView(context).apply {
            text = closeText
            setTextColor(Color.parseColor("#CF6679"))
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            val pH = context.sdp(SdpR.dimen._12sdp)
            val pV = context.sdp(SdpR.dimen._8sdp)
            setPadding(pH, pV, pH, pV)
            layoutParams = FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).also {
                it.gravity = Gravity.CENTER_VERTICAL or Gravity.END
            }
            background = RippleDrawable(
                ColorStateList.valueOf(Color.parseColor("#30FFFFFF")),
                GradientDrawable().apply {
                    setColor(Color.parseColor("#30CF6679"))
                    cornerRadius = context.sdpF(SdpR.dimen._8sdp)
                }, null
            )
            setOnClickListener { dialog.dismiss() }
        })
        root.addView(header)

        // ── Search bar ──
        val searchContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val pH = context.sdp(SdpR.dimen._12sdp)
            val pV = context.sdp(SdpR.dimen._8sdp)
            setPadding(pH, pV, pH, pV)
            setBackgroundColor(Color.parseColor("#1A0803"))
        }
        searchContainer.addView(TextView(context).apply {
            text = "🔍"
            textSize = 14f
            setPadding(0, 0, context.sdp(SdpR.dimen._8sdp), 0)
        })
        val searchField = EditText(context).apply {
            hint = searchHint
            setHintTextColor(Color.parseColor("#4A3020"))
            setTextColor(Color.parseColor("#F5F5DC"))
            textSize = 12f
            background = null
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            maxLines = 1
        }
        searchContainer.addView(searchField)
        root.addView(searchContainer)

        // 🔥 Pre-cache more strings for populateList and dialogs
        val noDataText     = context.getString(sound.recorder.widget.R.string.no_data_record)
        val playLabel      = context.getString(sound.recorder.widget.R.string.play)
        val learnLabel     = context.getString(sound.recorder.widget.R.string.learn)
        val noteLockedText = context.getString(sound.recorder.widget.R.string.note_locked)
        val watchAdText    = context.getString(sound.recorder.widget.R.string.watch_ad_unlock_note)
        val cancelText     = context.getString(sound.recorder.widget.R.string.cancel)
        val watchAdLabel   = context.getString(sound.recorder.widget.R.string.watch_ad_label)

        // ── Divider ──
        root.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, context.sdp(SdpR.dimen._1sdp))
            setBackgroundColor(Color.parseColor("#33F0B429"))
        })

        // ── Loading ──
        val progress = ProgressBar(context).apply {
            layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).also {
                it.gravity = Gravity.CENTER_HORIZONTAL
                it.setMargins(0, context.sdp(SdpR.dimen._32sdp), 0, context.sdp(SdpR.dimen._32sdp))
            }
        }
        root.addView(progress)

        // ── List ──
        val scroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            visibility = View.GONE
        }
        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val p = context.sdp(SdpR.dimen._10sdp)
            setPadding(p, p, p, p)
        }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        // ── Search listener ──
        searchField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = s.toString().lowercase().trim()
                list.removeAllViews()
                val filtered = if (q.isEmpty()) allNotes
                else allNotes.filter {
                    it.recordName.lowercase().contains(q) || it.senderName.lowercase().contains(q)
                }
                populateList(
                    context, list, dialog, filtered.take(20), // Batasi 20 item pertama untuk performa
                    noDataText, playLabel, learnLabel, noteLockedText, watchAdText, cancelText, watchAdLabel
                )
            }
        })

        dialog.setView(root, 0, 0, 0, 0) // Menghilangkan spacing default AlertDialog
        dialog.show()

        dialog.window?.apply {
            setLayout(MATCH_PARENT, MATCH_PARENT)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            
            // Membuat layout melampaui batas layar (termasuk status bar dan navigation bar)
            addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            
            // Tambahan untuk memastikan benar-benar memenuhi area bawah (nav bar)
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                navigationBarColor = Color.TRANSPARENT
            }

            // Hilangkan dim background agar benar-benar fokus ke UI dialog
            setDimAmount(0.0f)
        }

        // ── Cache check ──
        if (isCacheValid(instrumentType)) {
            progress.visibility = View.GONE
            scroll.visibility = View.VISIBLE
            allNotes.addAll(cache[instrumentType]!!.notes)
            populateList(
                context, list, dialog, allNotes.take(30), // Batasi awal 30 item
                noDataText, playLabel, learnLabel, noteLockedText, watchAdText, cancelText, watchAdLabel
            )
            return
        }

        val languageCode = Locale.getDefault().language
        FirebaseFirestore.getInstance()
            .collection("balera.music.android")
            .whereEqualTo("status", "published")
            .whereEqualTo("category", instrumentType)
            .whereArrayContainsAny("language", listOf("en", languageCode))
            .orderBy("submitted_at", Query.Direction.DESCENDING)
            .limit(50)
            .get()
            .addOnSuccessListener { snapshot ->
                progress.visibility = View.GONE
                scroll.visibility = View.VISIBLE
                val notes = snapshot.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    val jsonNote = d["json_note"] as? String ?: ""
                    if (jsonNote.isBlank()) return@mapNotNull null
                    NoteItem(
                        recordName  = d["record_name"]  as? String ?: "-",
                        senderName  = d["sender_name"]  as? String ?: "-",
                        submittedAt = d["submitted_at"] as? Long   ?: 0L,
                        jsonNote    = jsonNote
                    )
                }
                cache[instrumentType] = CachedResult(notes, System.currentTimeMillis())
                allNotes.addAll(notes)
                populateList(
                    context, list, dialog, allNotes.take(30), // Batasi awal 30 item
                    noDataText, playLabel, learnLabel, noteLockedText, watchAdText, cancelText, watchAdLabel
                )
            }
            .addOnFailureListener {
                progress.visibility = View.GONE
                scroll.visibility = View.VISIBLE
                Toast.makeText(context, "Gagal memuat: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun populateList(
        context: Context,
        list: LinearLayout,
        dialog: AlertDialog,
        notes: List<NoteItem>,
        noDataText: String,
        playLabel: String,
        learnLabel: String,
        noteLockedText: String,
        watchAdText: String,
        cancelText: String,
        watchAdLabel: String
    ) {
        if (notes.isEmpty()) {
            list.addView(TextView(context).apply {
                text = noDataText
                setTextColor(Color.parseColor("#4A3020"))
                textSize = 13f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also {
                    val m = context.sdp(SdpR.dimen._32sdp)
                    it.topMargin = m
                }
            })
            return
        }

        val sdf    = SimpleDateFormat("dd/MM  HH:mm", Locale.getDefault())
        val spacer = context.sdp(SdpR.dimen._6sdp)

        notes.forEach { note ->
            // ... (keep row logic)
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = RippleDrawable(
                    ColorStateList.valueOf(Color.parseColor("#20FFFFFF")),
                    GradientDrawable().apply {
                        setColor(Color.parseColor("#2A1208"))
                        cornerRadius = context.sdpF(SdpR.dimen._10sdp)
                        setStroke(context.sdp(SdpR.dimen._1sdp), Color.parseColor("#33F0B429"))
                    }, null
                )
                val pH = context.sdp(SdpR.dimen._12sdp)
                val pV = context.sdp(SdpR.dimen._8sdp)
                setPadding(pH, pV, pH, pV)
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also {
                    it.bottomMargin = spacer
                }
            }

            row.addView(TextView(context).apply {
                text = "🎵"
                textSize = 18f
                setPadding(0, 0, context.sdp(SdpR.dimen._10sdp), 0)
            })

            row.addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                addView(TextView(context).apply {
                    text = note.recordName.uppercase()
                    setTextColor(Color.parseColor("#F5F5DC"))
                    textSize = 12f
                    typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                })
                addView(TextView(context).apply {
                    text = "👤 ${note.senderName}  ·  🕐 ${sdf.format(Date(note.submittedAt))}"
                    setTextColor(Color.parseColor("#8A7456"))
                    textSize = 8f
                })
            })

            // ── Ikon kunci (disembunyikan setelah unlock) ──
            val lockTv = TextView(context).apply {
                text = "🔒"
                textSize = 11f
                visibility = if (isNoteUnlocked(context, note)) View.GONE else View.VISIBLE
                setPadding(0, 0, context.sdp(SdpR.dimen._6sdp), 0)
            }
            row.addView(lockTv)

            row.addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(1, context.sdp(SdpR.dimen._28sdp)).also {
                    it.marginEnd = context.sdp(SdpR.dimen._10sdp)
                }
                setBackgroundColor(Color.parseColor("#33F0B429"))
            })

            // Fungsi lokal: jalankan aksi, minta iklan jika belum unlock
            fun runWithAdGate(action: () -> Unit) {
                if (isNoteUnlocked(context, note)) {
                    action()
                    return
                }
                showAdConfirmDialog(context, noteLockedText, watchAdText, cancelText, watchAdLabel) {
                    onRequestAd {
                        markNoteUnlocked(context, note)
                        lockTv.visibility = View.GONE
                        action()
                    }
                }
            }

            // ── Tombol Play ──
            row.addView(buildActionBtn(context, "▶", playLabel, "B8720A", "F0B429") {
                runWithAdGate { dialog.dismiss(); playUserNote(note.jsonNote) }
            })

            row.addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(context.sdp(SdpR.dimen._8sdp), 1)
            })

            // ── Tombol Belajar ──
            row.addView(buildActionBtn(context, "★", learnLabel, "1A4A6B", "80DCFF") {
                runWithAdGate { dialog.dismiss(); startLearnMode(note.jsonNote) }
            })

            list.addView(row)
        }
    }

    fun onBilahHit(index: Int) {
        if (!isLearning) return
        val expected = learnEvents.getOrNull(learnStep) ?: return
        if (index == expected.padIndex) {
            learnStep++
            showLearnStep()
        }
    }

    private fun parseEvents(jsonNote: String): Pair<List<RecordedTap>, String> {
        val arr    = JSONObject(jsonNote).getJSONArray("events")
        val events = mutableListOf<RecordedTap>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            events.add(RecordedTap(
                padIndex  = o.getInt("padIndex"),
                timestamp = o.getLong("timestamp"),
                metadata  = o.optString("metadata", "")
            ))
        }
        val typeKey = if (events.firstOrNull()?.metadata.orEmpty().contains("slendro"))
            "demung_slendro" else "demung_pelog"
        return Pair(events, typeKey)
    }

    private fun playUserNote(jsonNote: String) {
        val (events, typeKey) = try { parseEvents(jsonNote) } catch (e: Exception) {
            onToast(mContext?.getString(sound.recorder.widget.R.string.invalid_note_format) ?: "Format not tidak valid")
            return
        }
        if (events.isEmpty()) return
        stopAll()
        onPlaybackStatusChanged(true)
        playJob = lifecycleScope.launch {
            var lastTs = 0L
            for (event in events) {
                val gap = (event.timestamp - lastTs).coerceIn(0L, 3000L)
                if (gap > 0) delay(gap)
                lastTs = event.timestamp
                withContext(Dispatchers.Main) {
                    val key = event.metadata?.takeIf { it.isNotBlank() } ?: typeKey
                    onTabSelect(key.contains("slendro"))
                    onTriggerAnim(event.padIndex)
                    SoundPlayUtils.playSound(key, "type${event.padIndex + 1}")
                }
            }
            onPlaybackStatusChanged(false)
        }
    }

    private fun startLearnMode(jsonNote: String) {
        val (events, typeKey) = try { parseEvents(jsonNote) } catch (e: Exception) {
            onToast(mContext?.getString(sound.recorder.widget.R.string.invalid_note_format) ?: "Format not tidak valid")
            return
        }
        if (events.isEmpty()) return
        playJob?.cancel()
        isLearning   = true
        learnEvents  = events
        learnStep    = 0
        learnTypeKey = typeKey
        onLearnVisible(true)
        showLearnStep()
    }

    private fun showLearnStep() {
        if (learnStep >= learnEvents.size) {
            onToast(mContext?.getString(sound.recorder.widget.R.string.learn_complete) ?: "Selesai! Kamu sudah memainkan semua not.")
            stopAll()
            return
        }
        val event = learnEvents[learnStep]
        val key   = event.metadata?.takeIf { it.isNotBlank() } ?: learnTypeKey
        onTabSelect(key.contains("slendro"))
        onHighlight(event.padIndex)
        onLearnStepUpdate(learnStep + 1, learnEvents.size)
    }

    fun stopAll() {
        playJob?.cancel()
        playJob     = null
        isLearning  = false
        learnEvents = emptyList()
        learnStep   = 0
        onClearHighlight()
        onLearnVisible(false)
        onPlaybackStatusChanged(false)
    }

    // ─── Dialog konfirmasi sebelum iklan ─────────────────────────

    private fun showAdConfirmDialog(
        context: Context,
        noteLockedText: String,
        watchAdText: String,
        cancelText: String,
        watchAdLabel: String,
        onConfirm: () -> Unit
    ) {
        val d = AlertDialog.Builder(context).create()

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A0803"))
                cornerRadius = context.sdpF(SdpR.dimen._14sdp)
                setStroke(context.sdp(SdpR.dimen._1sdp), Color.parseColor("#33F0B429"))
            }
            val p = context.sdp(SdpR.dimen._16sdp)
            setPadding(p, p, p, p)
        }

        root.addView(TextView(context).apply {
            text = "🔒  $noteLockedText"
            setTextColor(Color.parseColor("#F0B429"))
            textSize = 14f
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        })

        root.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, context.sdp(SdpR.dimen._8sdp))
        })

        root.addView(TextView(context).apply {
            text = watchAdText
            setTextColor(Color.parseColor("#D2B48C"))
            textSize = 11f
            setLineSpacing(0f, 1.4f)
        })

        root.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, context.sdp(SdpR.dimen._12sdp))
        })

        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }

        btnRow.addView(buildDialogBtn(context, cancelText, "777777") { d.dismiss() })

        btnRow.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(context.sdp(SdpR.dimen._8sdp), 1)
        })

        btnRow.addView(buildDialogBtn(context, watchAdLabel, "F0B429") {
            d.dismiss()
            onConfirm()
        })

        root.addView(btnRow)
        d.setView(root)
        d.show()
        d.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (context.resources.displayMetrics.widthPixels * 0.80).toInt(),
                WRAP_CONTENT
            )
        }
    }

    private fun buildDialogBtn(ctx: Context, label: String, colorHex: String, onClick: () -> Unit) =
        TextView(ctx).apply {
            text = label
            setTextColor(Color.parseColor("#$colorHex"))
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            val pH = ctx.sdp(SdpR.dimen._14sdp)
            val pV = ctx.sdp(SdpR.dimen._8sdp)
            setPadding(pH, pV, pH, pV)
            background = RippleDrawable(
                ColorStateList.valueOf(Color.parseColor("#40FFFFFF")),
                GradientDrawable().apply {
                    setColor(Color.parseColor("#20$colorHex"))
                    cornerRadius = ctx.sdpF(SdpR.dimen._8sdp)
                    setStroke(ctx.sdp(SdpR.dimen._1sdp), Color.parseColor("#$colorHex"))
                }, null
            )
            setOnClickListener { onClick() }
        }

    private fun buildActionBtn(
        ctx: Context,
        icon: String,
        label: String,
        bgColorHex: String,
        fgColorHex: String,
        onClick: () -> Unit
    ) = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        val size = ctx.sdp(SdpR.dimen._44sdp)
        layoutParams = LinearLayout.LayoutParams(size, size)
        background = RippleDrawable(
            ColorStateList.valueOf(Color.parseColor("#50FFFFFF")),
            GradientDrawable().apply {
                setColor(Color.parseColor("#CC$bgColorHex"))
                cornerRadius = ctx.sdpF(SdpR.dimen._8sdp)
            }, null
        )
        addView(TextView(ctx).apply {
            text = icon
            textSize = 14f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).also {
                it.gravity = Gravity.CENTER_HORIZONTAL
            }
        })
        addView(TextView(ctx).apply {
            text = label
            setTextColor(Color.parseColor("#$fgColorHex"))
            textSize = 7f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).also {
                it.gravity = Gravity.CENTER_HORIZONTAL
            }
        })
        setOnClickListener { onClick() }
    }

    private fun Context.sdp(id: Int)  = resources.getDimensionPixelSize(id)
    private fun Context.sdpF(id: Int) = resources.getDimension(id)
}

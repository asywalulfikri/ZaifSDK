package sound.recorder.widget.recording

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.core.graphics.ColorUtils
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sound.recorder.widget.R
import sound.recorder.widget.builder.ZaifSDKBuilder
import sound.recorder.widget.builder.ZaifSDKConfig
import sound.recorder.widget.recording.RecordingDialogViews.applyWindow
import sound.recorder.widget.recording.RecordingDialogViews.body
import sound.recorder.widget.recording.RecordingDialogViews.circle
import sound.recorder.widget.recording.RecordingDialogViews.dialogRoot
import sound.recorder.widget.recording.RecordingDialogViews.footer
import sound.recorder.widget.recording.RecordingDialogViews.icon
import sound.recorder.widget.recording.RecordingDialogViews.iconButton
import sound.recorder.widget.recording.RecordingDialogViews.rounded
import sound.recorder.widget.recording.RecordingDialogViews.textButton
import sound.recorder.widget.recording.RecordingDialogViews.title
import sound.recorder.widget.recording.RecordingDialogViews.vSpace
import sound.recorder.widget.recording.database.AppDatabase
import sound.recorder.widget.recording.database.RecordingEntity
import sound.recorder.widget.recording.export.RecordingExportDialog
import sound.recorder.widget.recording.export.RecordingExporter
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.intuit.sdp.R as SdpR

/**
 * Daftar rekaman tersimpan per instrumen.
 * Tiap kartu: ▶ Mainkan + tombol berlabel Ekspor, Ubah nama, Promosikan, Hapus.
 * Share audio dilakukan lewat Ekspor (MP3/MIDI) agar melewati gate rewarded ad.
 * Tampilan mengikuti [RecordingDialogStyle.default].
 */
object RecordingListDialogHelper {

    private const val MAX_NAME_LENGTH  = 30
    private const val PREFS_PROMOTED      = "zaif_promoted_notes"
    private const val KEY_PROMOTED_IDS   = "promoted_ids"
    private const val KEY_LAST_PROMO_DATE = "last_promo_date"
    private const val KEY_PROMO_COUNT_TODAY = "promo_count_today"
    private const val MAX_DAILY_PROMOS   = 5
    private val actionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private fun Context.sdp(id: Int) = resources.getDimensionPixelSize(id)

    private fun todayString() = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    private fun hasPromotedToday(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_PROMOTED, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_LAST_PROMO_DATE, null) != todayString()) return false
        return prefs.getInt(KEY_PROMO_COUNT_TODAY, 0) >= MAX_DAILY_PROMOS
    }

    private fun markAsPromoted(context: Context, recordId: Int) {
        val prefs = context.getSharedPreferences(PREFS_PROMOTED, Context.MODE_PRIVATE)
        val ids   = prefs.getStringSet(KEY_PROMOTED_IDS, mutableSetOf())!!.toMutableSet()
        ids.add(recordId.toString())
        val today = todayString()
        val currentCount = if (prefs.getString(KEY_LAST_PROMO_DATE, null) == today)
            prefs.getInt(KEY_PROMO_COUNT_TODAY, 0) else 0
        prefs.edit()
            .putStringSet(KEY_PROMOTED_IDS, ids)
            .putString(KEY_LAST_PROMO_DATE, today)
            .putInt(KEY_PROMO_COUNT_TODAY, currentCount + 1)
            .apply()
    }

    private fun isPromoted(context: Context, recordId: Int): Boolean {
        val prefs = context.getSharedPreferences(PREFS_PROMOTED, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_PROMOTED_IDS, emptySet())!!.contains(recordId.toString())
    }
    var zaifSDKConfig : ZaifSDKConfig? =null

    /**
     * @param onRequestExportReward jika diisi, user harus nonton rewarded ad sebelum export
     *        (panggil `onRewarded` setelah iklan selesai). null = export tanpa iklan.
     *
     * @JvmOverloads menjaga signature lama `show(Context, String, Boolean, (RecordingEntity) -> Unit)`
     * tetap ada di bytecode, agar kode/library yang di-compile dengan SDK versi lama tidak NoSuchMethodError.
     */
    @JvmOverloads
    fun show(
        context: Context,
        instrumentFilter: String,
        showPromot: Boolean = true,
        onRequestExportReward: ((onRewarded: () -> Unit) -> Unit)? = null,
        style: RecordingDialogStyle = RecordingDialogStyle.default,
        /** Rekam lapis (overdub) di atas rekaman ini. null = tombol tidak ditampilkan. */
        onOverdub: ((RecordingEntity) -> Unit)? = null,
        onPlay: (RecordingEntity) -> Unit
    ) {
        zaifSDKConfig = ZaifSDKBuilder.load(context)
        val c = RecordingDialogPalette(context, style)
        val dialog = AlertDialog.Builder(context).create()

        val root = dialogRoot(context, c)

        // ── Header ──
        val countTv = body(context, c, "")
        root.addView(FrameLayout(context).apply {
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 0, context.sdp(SdpR.dimen._36sdp), 0)
                addView(title(context, c, context.getString(R.string.list_record_result)))
                addView(countTv)
            })
            val size = context.sdp(SdpR.dimen._30sdp)
            addView(
                iconButton(context, c, R.drawable.zaif_rec_ic_close, context.getString(R.string.close)) {
                    dialog.dismiss()
                },
                FrameLayout.LayoutParams(size, size, Gravity.END or Gravity.TOP)
            )
        })
        root.addView(vSpace(context, SdpR.dimen._12sdp))

        // ── Daftar (tinggi mengikuti isi, maksimal 70% layar) ──
        val maxHeight = (context.resources.displayMetrics.heightPixels * 0.70).toInt()
        val scrollView = object : ScrollView(context) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST))
            }
        }.apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val listLayout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        scrollView.addView(listLayout)
        root.addView(scrollView, LinearLayout.LayoutParams(-1, -2))
        root.addView(vSpace(context, SdpR.dimen._8sdp))

        dialog.setView(root)

        val items = mutableListOf<RecordingEntity>()
        fun refreshCount() {
            countTv.text = context.getString(R.string.list_count, items.size)
            if (items.isEmpty() && listLayout.childCount == 0) listLayout.addView(emptyState(context, c))
        }

        val dialogScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        dialog.setOnDismissListener { dialogScope.cancel() }
        dialogScope.launch {
            val recordings = withContext(Dispatchers.IO) {
                AppDatabase.getInstance(context).recordingDao().getAll()
            }
            items += recordings.filter { it.setName.equals(instrumentFilter, ignoreCase = true) }
            fun addRow(rec: RecordingEntity, atTop: Boolean) {
                lateinit var row: View
                row = buildRow(
                    context, c, rec, showPromot, onRequestExportReward,
                    onPlay = { current -> dialog.dismiss(); onPlay(current) },
                    onDeleted = {
                        listLayout.removeView(row)
                        items.remove(rec)
                        refreshCount()
                    },
                    onAdded = { added ->
                        // Hasil potong = rekaman baru → tampil paling atas
                        items.add(0, added)
                        addRow(added, atTop = true)
                        refreshCount()
                        scrollView.smoothScrollTo(0, 0)
                    },
                    onOverdub = onOverdub?.let { cb -> { current -> dialog.dismiss(); cb(current) } }
                )
                if (atTop) listLayout.addView(row, 0) else listLayout.addView(row)
            }
            items.toList().forEach { addRow(it, atTop = false) }
            refreshCount()
        }

        dialog.show()
        applyWindow(dialog, context, 0.92, 0.65)
    }

    // ─── KARTU REKAMAN ───────────────────────────────────────────────────────
    // Semua aksi tampil langsung dengan ikon + tulisan (tanpa menu tersembunyi),
    // supaya mudah dipahami anak-anak. Satu warna aksen; merah hanya untuk Hapus.

    private fun buildRow(
        context: Context,
        c: RecordingDialogPalette,
        original: RecordingEntity,
        showPromot: Boolean,
        onRequestExportReward: ((onRewarded: () -> Unit) -> Unit)?,
        onPlay: (RecordingEntity) -> Unit,
        onDeleted: () -> Unit,
        onAdded: (RecordingEntity) -> Unit,
        onOverdub: ((RecordingEntity) -> Unit)?
    ): View {
        // Disalin ulang saat diubah nama, agar export/putar memakai nama terbaru
        var rec = original
        val hasMicAudio = !rec.audioPath.isNullOrEmpty()

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(context, c.surface, SdpR.dimen._10sdp, c.outline)
        }

        // ── Atas: ikon jenis rekaman, nama + info, tombol Mainkan ──
        val nameTv = TextView(context).apply {
            text = rec.name
            setTextColor(c.text)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        val dateText = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(rec.createdAt))
        val smallIcon = context.sdp(SdpR.dimen._10sdp)
        val metaRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, context.sdp(SdpR.dimen._2sdp), 0, 0)
            addView(icon(context, R.drawable.zaif_rec_ic_timer, c.textSecondary), LinearLayout.LayoutParams(smallIcon, smallIcon))
            addView(TextView(context).apply {
                text = "${formatDuration(rec.durationMs)}   $dateText"
                setTextColor(c.textSecondary)
                textSize = 10f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(context.sdp(SdpR.dimen._3sdp), 0, 0, 0)
            })
        }

        val tileSize = context.sdp(SdpR.dimen._30sdp)
        val tileIcon = context.sdp(SdpR.dimen._16sdp)
        card.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val p = context.sdp(SdpR.dimen._8sdp)
            setPadding(p, p, p, p)
            addView(FrameLayout(context).apply {
                background = rounded(context, c.accentSoft, SdpR.dimen._8sdp)
                addView(
                    icon(context, if (hasMicAudio) R.drawable.zaif_rec_ic_mic else R.drawable.zaif_rec_ic_music, c.accent),
                    FrameLayout.LayoutParams(tileIcon, tileIcon, Gravity.CENTER)
                )
            }, LinearLayout.LayoutParams(tileSize, tileSize))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(context.sdp(SdpR.dimen._8sdp), 0, context.sdp(SdpR.dimen._6sdp), 0)
                addView(nameTv)
                addView(metaRow)
                songRow(context, c, rec, smallIcon)?.let { addView(it) }
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(playButton(context, c) { onPlay(rec) }, LinearLayout.LayoutParams(-2, context.sdp(SdpR.dimen._26sdp)))
        })

        card.addView(View(context).apply { setBackgroundColor(c.outline) }, LinearLayout.LayoutParams(-1, 1))

        // ── Bawah: tombol aksi berlabel ──
        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            val p = context.sdp(SdpR.dimen._3sdp)
            setPadding(p, p, p, p)
        }
        fun addAction(view: View) {
            actions.addView(view, LinearLayout.LayoutParams(0, context.sdp(SdpR.dimen._34sdp), 1f))
        }

        addAction(actionButton(context, c, R.drawable.zaif_rec_ic_export, context.getString(R.string.export_title), c.accent) {
            RecordingExportDialog.show(context, rec, onRequestReward = onRequestExportReward)
        })
        addAction(actionButton(context, c, R.drawable.zaif_rec_ic_trim, context.getString(R.string.record_trim), c.text) {
            showTrimDialog(context, c, rec, onAdded)
        })
        if (onOverdub != null) {
            addAction(actionButton(context, c, R.drawable.zaif_rec_ic_layers, context.getString(R.string.record_overdub), c.text) {
                onOverdub(rec)
            })
        }
        addAction(actionButton(context, c, R.drawable.zaif_rec_ic_edit, context.getString(R.string.list_rename), c.text) {
            showRenameDialog(context, c, rec) { newName ->
                rec = rec.copy(name = newName)
                nameTv.text = newName
            }
        })

        if (zaifSDKConfig?.isPromotNot == true && showPromot) {
            val promoteSlot = FrameLayout(context)
            fun renderPromote(promoted: Boolean) {
                promoteSlot.removeAllViews()
                promoteSlot.addView(
                    if (promoted) {
                        actionButton(context, c, R.drawable.zaif_rec_ic_check, context.getString(R.string.active), c.success) {
                            Toast.makeText(context, context.getString(R.string.success_saved), Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        actionButton(context, c, R.drawable.zaif_rec_ic_star, context.getString(R.string.promosikan), c.text) {
                            if (hasPromotedToday(context)) {
                                Toast.makeText(context, context.getString(R.string.limit_promot), Toast.LENGTH_SHORT).show()
                            } else {
                                showPromoteDialog(context, c, rec) { renderPromote(true) }
                            }
                        }
                    },
                    FrameLayout.LayoutParams(-1, -1)
                )
            }
            renderPromote(isPromoted(context, rec.id))
            addAction(promoteSlot)
        }

        val isDebug = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (isDebug) {
            addAction(actionButton(context, c, R.drawable.zaif_rec_ic_code, "JSON", c.text) { shareJsonFile(context, rec) })
        }
        addAction(actionButton(context, c, R.drawable.zaif_rec_ic_delete, context.getString(R.string.delete), c.error) {
            showDeleteConfirm(context, c, rec, onDeleted)
        })
        card.addView(actions)

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(card)
            addView(vSpace(context, SdpR.dimen._6sdp))
        }
    }

    /** Baris "🎵 Judul lagu" untuk rekaman yang ikut merekam lagu; null jika tanpa lagu. */
    private fun songRow(context: Context, c: RecordingDialogPalette, rec: RecordingEntity, iconSize: Int): View? {
        val titles = RecordedMusicJson.parse(rec.musicJson).mapNotNull { it.title?.takeIf(String::isNotBlank) }.distinct()
        if (titles.isEmpty()) return null
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, context.sdp(SdpR.dimen._2sdp), 0, 0)
            addView(icon(context, R.drawable.zaif_rec_ic_queue_music, c.accent), LinearLayout.LayoutParams(iconSize, iconSize))
            addView(TextView(context).apply {
                text = titles.joinToString(" · ")
                setTextColor(c.accent)
                textSize = 10f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(context.sdp(SdpR.dimen._3sdp), 0, 0, 0)
            })
        }
    }

    /** Tombol "▶ Mainkan" berlabel, warna aksen. */
    private fun playButton(context: Context, c: RecordingDialogPalette, onClick: () -> Unit) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(context.sdp(SdpR.dimen._8sdp), 0, context.sdp(SdpR.dimen._10sdp), 0)
        background = RippleDrawable(ColorStateList.valueOf(c.ripple), rounded(context, c.accent, SdpR.dimen._14sdp), null)
        val size = context.sdp(SdpR.dimen._14sdp)
        addView(icon(context, R.drawable.zaif_rec_ic_play, c.onAccent), LinearLayout.LayoutParams(size, size))
        addView(TextView(context).apply {
            text = context.getString(R.string.play)
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(c.onAccent)
            maxLines = 1
            setPadding(context.sdp(SdpR.dimen._3sdp), 0, 0, 0)
        })
        contentDescription = context.getString(R.string.play)
        setOnClickListener { onClick() }
    }

    /** Tombol aksi datar: ikon di atas, tulisan di bawah. */
    private fun actionButton(
        context: Context,
        c: RecordingDialogPalette,
        @DrawableRes iconRes: Int,
        label: String,
        color: Int,
        onClick: () -> Unit
    ) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        background = RippleDrawable(ColorStateList.valueOf(c.ripple), null, rounded(context, Color.WHITE, SdpR.dimen._6sdp))
        val size = context.sdp(SdpR.dimen._14sdp)
        addView(icon(context, iconRes, color), LinearLayout.LayoutParams(size, size))
        addView(TextView(context).apply {
            text = label
            textSize = 9f
            gravity = Gravity.CENTER
            setTextColor(if (color == c.text) c.textSecondary else color)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(context.sdp(SdpR.dimen._2sdp), context.sdp(SdpR.dimen._2sdp), context.sdp(SdpR.dimen._2sdp), 0)
        })
        contentDescription = label
        setOnClickListener { onClick() }
    }

    // ─── HAPUS ───────────────────────────────────────────────────────────────

    private fun showDeleteConfirm(context: Context, c: RecordingDialogPalette, rec: RecordingEntity, onDeleted: () -> Unit) {
        val dialog = AlertDialog.Builder(context).create()
        val root = dialogRoot(context, c)
        root.addView(title(context, c, context.getString(R.string.list_delete_title)))
        root.addView(vSpace(context, SdpR.dimen._6sdp))
        root.addView(body(context, c, context.getString(R.string.list_delete_message, rec.name)))
        root.addView(footer(
            context,
            textButton(context, c, context.getString(R.string.cancel), c.textSecondary).apply {
                setOnClickListener { dialog.dismiss() }
            },
            textButton(context, c, context.getString(R.string.delete), c.error).apply {
                setOnClickListener {
                    dialog.dismiss()
                    actionScope.launch {
                        withContext(Dispatchers.IO) {
                            rec.audioPath?.let { path -> File(path).takeIf { it.exists() }?.delete() }
                            AppDatabase.getInstance(context).recordingDao().delete(rec)
                        }
                        onDeleted()
                    }
                }
            }
        ), LinearLayout.LayoutParams(-1, -2))
        dialog.setView(root)
        dialog.show()
        applyWindow(dialog, context, 0.85, 0.45)
    }

    // ─── UBAH NAMA ───────────────────────────────────────────────────────────

    /** Pilih bagian rekaman yang disimpan → disimpan sebagai rekaman baru (asli tidak berubah). */
    private fun showTrimDialog(
        context: Context,
        c: RecordingDialogPalette,
        rec: RecordingEntity,
        onSaved: (RecordingEntity) -> Unit
    ) {
        val taps = RecordingTrim.parseTaps(rec.eventsJson)
        val music = RecordedMusicJson.parse(rec.musicJson)
        val duration = maxOf(rec.durationMs, taps.maxOfOrNull { it.timestamp } ?: 0L, music.maxOfOrNull { it.endMs } ?: 0L)

        val dialog = AlertDialog.Builder(context).create()
        val root = dialogRoot(context, c)
        root.addView(title(context, c, context.getString(R.string.record_trim_title)))
        root.addView(vSpace(context, SdpR.dimen._4sdp))
        root.addView(body(context, c, context.getString(R.string.record_trim_hint)))
        root.addView(vSpace(context, SdpR.dimen._12sdp))

        val rangeTv = TextView(context).apply {
            setTextColor(c.text)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        fun renderRange(start: Long, end: Long) {
            rangeTv.text = "${formatTrimTime(start)}  –  ${formatTrimTime(end)}   (${formatTrimTime(end - start)})"
        }
        val range = TrimRangeView(
            context, duration,
            tapTimes = taps.filter { it.metadata != RecordingExporter.OFF_METADATA }.map { it.timestamp },
            musicRanges = music.map { it.atMs..it.endMs },
            minLengthMs = RecordingTrim.MIN_LENGTH_MS,
            accent = c.accent,
            trackColor = c.surfaceAlt,
            markerColor = c.textSecondary
        ).apply { onRangeChanged = { s, e -> renderRange(s, e) } }
        renderRange(0L, duration)
        root.addView(rangeTv, LinearLayout.LayoutParams(-1, -2))
        root.addView(vSpace(context, SdpR.dimen._6sdp))
        root.addView(range, LinearLayout.LayoutParams(-1, -2))

        val cancelBtn = textButton(context, c, context.getString(R.string.cancel), c.textSecondary)
        val saveBtn = textButton(context, c, context.getString(R.string.save))
        cancelBtn.setOnClickListener { dialog.dismiss() }
        saveBtn.isEnabled = duration > RecordingTrim.MIN_LENGTH_MS
        saveBtn.setOnClickListener {
            saveBtn.isEnabled = false
            cancelBtn.isEnabled = false
            dialog.setCancelable(false)
            val app = context.applicationContext
            actionScope.launch {
                val saved = RecordingTrim.saveTrimmedCopy(app, rec, range.startMs, range.endMs)
                Toast.makeText(
                    app,
                    app.getString(if (saved != null) R.string.record_trim_saved else R.string.record_trim_failed),
                    Toast.LENGTH_SHORT
                ).show()
                if (dialog.isShowing) dialog.dismiss()
                saved?.let(onSaved)
            }
        }
        root.addView(footer(context, cancelBtn, saveBtn), LinearLayout.LayoutParams(-1, -2))

        dialog.setView(root)
        dialog.show()
        applyWindow(dialog, context, 0.92, 0.65)
    }

    /** 0:03.5 */
    private fun formatTrimTime(ms: Long): String {
        val totalTenths = ms / 100
        val m = totalTenths / 600
        val sec = (totalTenths / 10) % 60
        return String.format(Locale.US, "%d:%02d.%d", m, sec, totalTenths % 10)
    }

    private fun showRenameDialog(
        context: Context,
        c: RecordingDialogPalette,
        rec: RecordingEntity,
        onRenamed: (String) -> Unit
    ) {
        val dialog = AlertDialog.Builder(context).create()
        val root = dialogRoot(context, c)
        root.addView(title(context, c, context.getString(R.string.list_rename)))
        root.addView(vSpace(context, SdpR.dimen._12sdp))

        val counterTv = TextView(context).apply {
            text = "${rec.name.length}/$MAX_NAME_LENGTH"
            setTextColor(c.textSecondary)
            textSize = 10f
            gravity = Gravity.END
        }
        val nameInput = inputField(context, c, context.getString(R.string.list_rename)).apply {
            setText(rec.name)
            setSelection(text.length)
            filters = arrayOf<InputFilter>(InputFilter.LengthFilter(MAX_NAME_LENGTH))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val len = s?.length ?: 0
                    counterTv.text = "$len/$MAX_NAME_LENGTH"
                    counterTv.setTextColor(if (len >= MAX_NAME_LENGTH) c.error else c.textSecondary)
                }
            })
        }
        root.addView(nameInput, LinearLayout.LayoutParams(-1, -2))
        root.addView(vSpace(context, SdpR.dimen._4sdp))
        root.addView(counterTv, LinearLayout.LayoutParams(-1, -2))

        root.addView(footer(
            context,
            textButton(context, c, context.getString(R.string.cancel), c.textSecondary).apply {
                setOnClickListener { dialog.dismiss() }
            },
            textButton(context, c, context.getString(R.string.save)).apply {
                setOnClickListener {
                    val newName = nameInput.text.toString().trim().ifEmpty { rec.name }
                    actionScope.launch {
                        withContext(Dispatchers.IO) {
                            AppDatabase.getInstance(context).recordingDao().updateName(rec.id, newName)
                        }
                        onRenamed(newName)
                    }
                    dialog.dismiss()
                }
            }
        ), LinearLayout.LayoutParams(-1, -2))

        dialog.setView(root)
        dialog.show()
        applyWindow(dialog, context, 0.88, 0.50)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        nameInput.requestFocus()
    }

    // ─── PROMOSI ─────────────────────────────────────────────────────────────

    private fun showPromoteDialog(context: Context, c: RecordingDialogPalette, rec: RecordingEntity, onPromoted: () -> Unit = {}) {
        val dialog = AlertDialog.Builder(context).create()
        zaifSDKConfig = ZaifSDKBuilder.load(context)
        val root = dialogRoot(context, c)
        root.addView(title(context, c, context.getString(R.string.promosikan)))
        root.addView(vSpace(context, SdpR.dimen._6sdp))
        root.addView(body(context, c, context.getString(R.string.promot_info)))
        root.addView(vSpace(context, SdpR.dimen._12sdp))

        val nameInput = inputField(context, c, context.getString(R.string.your_name))
        root.addView(nameInput, LinearLayout.LayoutParams(-1, -2))

        root.addView(footer(
            context,
            textButton(context, c, context.getString(R.string.cancel), c.textSecondary).apply {
                setOnClickListener { dialog.dismiss() }
            },
            textButton(context, c, context.getString(R.string.send)).apply {
                setOnClickListener {
                    sendNoteToFirestore(context, rec, nameInput.text.toString().trim(), onPromoted)
                    dialog.dismiss()
                }
            }
        ), LinearLayout.LayoutParams(-1, -2))

        dialog.setView(root)
        dialog.show()
        applyWindow(dialog, context, 0.90, 0.60)
    }

    // ─── FIRESTORE / SHARE / JSON ───────────────────────────────────────────

    private fun sendNoteToFirestore(context: Context, rec: RecordingEntity, senderName: String, onSuccess: () -> Unit = {}) {
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            val appId = zaifSDKConfig?.applicationId ?: return@addOnSuccessListener

            // Jalankan sinkronisasi di Background Thread (Dispatchers.Default) agar tidak ANR jika note sangat panjang
            actionScope.launch {
                val syncedEvents = withContext(Dispatchers.Default) {
                    sound.recorder.widget.util.Utils.syncJsonNoteTimestamps(rec.eventsJson, 500L)
                }

                val jsonNote = """
                    {
                      "id": ${rec.id},
                      "name": "${rec.name}",
                      "category": "${rec.setName}",
                      "events": $syncedEvents,
                      "audio_path": "${rec.audioPath ?: ""}",
                      "created_at": ${rec.createdAt},
                      "duration_ms": ${rec.durationMs},
                      "is_earphone": ${rec.isEarphoneRecording}
                    }
                """.trimIndent()

                val data = hashMapOf(
                    "sender_name"    to senderName,
                    "firebase_token" to token,
                    "json_note"      to jsonNote,
                    "record_name"    to rec.name,
                    "category"       to rec.setName,
                    "language"       to getLanguageList(),
                    "appId"          to appId,
                    "status"         to "DRAFT",
                    "is_free"         to true,
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

    private fun getLanguageList(): List<String> {
        val code = getLanguageCode()
        return if (code == "id" || code == "in") {
            listOf("id", "in")
        } else {
            listOf(code)
        }
    }
    // ─── SHARE ───────────────────────────────────────────────────────────────

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
        actionScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    file.writeText(jsonContent)
                }
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
    }

    // ─── VIEW HELPERS ────────────────────────────────────────────────────────

    private fun inputField(context: Context, c: RecordingDialogPalette, hintText: String) = EditText(context).apply {
        hint = hintText
        setHintTextColor(c.textSecondary)
        setTextColor(c.text)
        textSize = 14f
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        background = rounded(context, c.surfaceAlt, SdpR.dimen._8sdp, c.outline)
        val p = context.sdp(SdpR.dimen._10sdp)
        setPadding(p, p, p, p)
    }

    private fun emptyState(context: Context, c: RecordingDialogPalette) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        val pad = context.sdp(SdpR.dimen._24sdp)
        setPadding(pad, pad, pad, pad)
        val size = context.sdp(SdpR.dimen._56sdp)
        addView(FrameLayout(context).apply {
            background = circle(c.accentSoft)
            val iconSize = context.sdp(SdpR.dimen._26sdp)
            addView(icon(context, R.drawable.zaif_rec_ic_music, c.accent), FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER))
        }, LinearLayout.LayoutParams(size, size))
        addView(vSpace(context, SdpR.dimen._12sdp))
        addView(TextView(context).apply {
            text = context.getString(R.string.no_data_record)
            setTextColor(c.text)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        })
        addView(vSpace(context, SdpR.dimen._4sdp))
        addView(body(context, c, context.getString(R.string.list_empty_hint)).apply { gravity = Gravity.CENTER })
    }

    /** Durasi format m:ss, sama di semua bahasa. */
    private fun formatDuration(ms: Long): String {
        val sec = ms / 1000
        return String.format(Locale.US, "%d:%02d", sec / 60, sec % 60)
    }
}

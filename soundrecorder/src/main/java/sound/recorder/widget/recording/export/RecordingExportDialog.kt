package sound.recorder.widget.recording.export

import android.Manifest
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.SystemClock
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.annotation.Keep
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.ColorUtils
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.PermissionListener
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import sound.recorder.widget.R
import sound.recorder.widget.builder.ZaifSDKBuilder
import sound.recorder.widget.util.CoinManager
import sound.recorder.widget.recording.RecordingDialogPalette
import sound.recorder.widget.recording.RecordingDialogStyle
import sound.recorder.widget.recording.RecordingDialogViews.applyWindow
import sound.recorder.widget.recording.RecordingDialogViews.boxButton
import sound.recorder.widget.recording.RecordingDialogViews.icon
import sound.recorder.widget.recording.RecordingDialogViews.rounded
import sound.recorder.widget.recording.RecordingDialogViews.sdp
import sound.recorder.widget.recording.RecordingDialogViews.textButton
import sound.recorder.widget.recording.RecordingDialogViews.vSpace
import sound.recorder.widget.recording.database.RecordingEntity
import java.io.File
import kotlin.coroutines.resume
import com.intuit.sdp.R as SdpR

/**
 * Dialog export rekaman yang netral, bisa dipakai di semua aplikasi alat musik.
 * Pilih MP3/MIDI → Bagikan / WhatsApp / Simpan. Progress + tombol Batal tampil di dalam dialog.
 *
 * Jika [show] diberi `onRequestReward`, user harus menonton rewarded ad dulu (sekali per dialog dibuka)
 * sebelum Bagikan / WhatsApp / Simpan dijalankan, kecuali masih ada jatah [RecordingExporter.freeExportsPerDay] hari ini.
 *
 * Warna bisa disesuaikan per aplikasi sekali saja, misal di Application:
 * `RecordingDialogStyle.default = RecordingDialogStyle(accentColor = Color.parseColor("#E53935"))`
 */
@Keep
object RecordingExportDialog {

    /** Panel progress baru muncul kalau proses > nilai ini, agar export MIDI yang instan tidak berkedip. */
    private const val SHOW_PROGRESS_DELAY_MS = 250L

    private enum class Action { SHARE, WHATSAPP, SAVE }

    /**
     * @param onRequestReward tampilkan rewarded ad lalu panggil `onRewarded` setelah iklan selesai.
     *        null = tanpa iklan (misal user premium).
     */
    @JvmStatic
    @JvmOverloads
    fun show(
        context: Context,
        rec: RecordingEntity,
        style: RecordingDialogStyle = RecordingDialogStyle.default,
        onRequestReward: ((onRewarded: () -> Unit) -> Unit)? = null
    ) {
        val c = RecordingDialogPalette(context, style)

        val dialog = AlertDialog.Builder(context).create()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        dialog.setOnDismissListener { scope.cancel() }

        val mp3Available = RecordingExporter.canExportMp3(rec)
        var selected = if (mp3Available) RecordingExporter.Format.MP3 else RecordingExporter.Format.MIDI
        // File hasil export disimpan selama dialog terbuka agar Bagikan → Simpan tidak render ulang
        val exported = mutableMapOf<RecordingExporter.Format, File>()
        var workJob: Job? = null
        // Gratis 1x per hari: jatah baru dipakai saat export berhasil dibuat (gagal / batal tetap gratis)
        val freeExport = onRequestReward != null && RecordingExporter.hasFreeExport(context)
        var freeConsumed = false
        // Cukup nonton iklan sekali selama dialog terbuka (Bagikan lalu Simpan tidak perlu iklan lagi)
        var rewardUnlocked = onRequestReward == null || freeExport

        val pad = context.sdp(SdpR.dimen._16sdp)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(context, c.surface, SdpR.dimen._12sdp)
            setPadding(pad, pad, pad, context.sdp(SdpR.dimen._8sdp))
        }

        // ── Judul ──
        root.addView(TextView(context).apply {
            text = context.getString(R.string.export_dialog_title)
            setTextColor(c.text)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(context).apply {
            text = "${rec.name} · ${formatDuration(context, rec.durationMs)}"
            setTextColor(c.textSecondary)
            textSize = 12f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        root.addView(vSpace(context, SdpR.dimen._14sdp))

        // ── Pilihan format (segmented) ──
        val segment = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(context, c.surfaceAlt, SdpR.dimen._8sdp)
            val p = context.sdp(SdpR.dimen._3sdp)
            setPadding(p, p, p, p)
        }
        val segmentViews = mutableMapOf<RecordingExporter.Format, TextView>()
        val hintTv = TextView(context).apply {
            setTextColor(c.textSecondary)
            textSize = 11f
            setLineSpacing(0f, 1.2f)
        }

        fun refreshFormats() {
            segmentViews.forEach { (format, tv) ->
                val isSelected = format == selected
                tv.background = if (isSelected) rounded(context, c.accent, SdpR.dimen._6sdp) else null
                tv.setTextColor(if (isSelected) c.onAccent else c.text)
            }
            val hint = context.getString(
                if (selected == RecordingExporter.Format.MP3) R.string.export_mp3_hint else R.string.export_midi_hint
            )
            // Jelaskan kenapa tombol MP3 nonaktif
            hintTv.text = if (mp3Available) hint else "$hint\n${context.getString(R.string.export_mp3_unavailable)}"
        }

        RecordingExporter.Format.entries.forEach { format ->
            val enabled = format != RecordingExporter.Format.MP3 || mp3Available
            val tv = TextView(context).apply {
                text = if (format == RecordingExporter.Format.MP3) "MP3" else "MIDI"
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                alpha = if (enabled) 1f else 0.4f
                isEnabled = enabled
                setOnClickListener {
                    selected = format
                    refreshFormats()
                }
            }
            segmentViews[format] = tv
            segment.addView(tv, LinearLayout.LayoutParams(0, context.sdp(SdpR.dimen._32sdp), 1f))
        }
        root.addView(segment)
        root.addView(vSpace(context, SdpR.dimen._6sdp))
        root.addView(hintTv)
        root.addView(vSpace(context, SdpR.dimen._14sdp))
        refreshFormats()

        // ── Tombol aksi ──
        val actionRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        root.addView(actionRow)
        val freeTv = TextView(context).apply {
            text = "🎁 " + context.getString(R.string.export_first_free)
            setTextColor(c.success)
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(0, context.sdp(SdpR.dimen._6sdp), 0, 0)
            visibility = if (freeExport) View.VISIBLE else View.GONE
        }
        root.addView(freeTv)

        // ── Progress ──
        val progressTitle = TextView(context).apply {
            setTextColor(c.text)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
        }
        val progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = ColorStateList.valueOf(c.accent)
            indeterminateTintList = ColorStateList.valueOf(c.accent)
            progressBackgroundTintList = ColorStateList.valueOf(c.outline)
        }
        val progressInfo = TextView(context).apply {
            setTextColor(c.textSecondary)
            textSize = 11f
        }
        val progressPanel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            addView(progressTitle)
            addView(vSpace(context, SdpR.dimen._8sdp))
            addView(progressBar, LinearLayout.LayoutParams(-1, context.sdp(SdpR.dimen._4sdp)))
            addView(vSpace(context, SdpR.dimen._6sdp))
            addView(progressInfo)
            addView(TextView(context).apply {
                text = context.getString(R.string.export_keep_open)
                setTextColor(c.textSecondary)
                textSize = 10f
            })
        }
        root.addView(progressPanel)

        // ── Pesan hasil ──
        val resultTv = TextView(context).apply {
            visibility = View.GONE
            textSize = 12f
            setPadding(0, context.sdp(SdpR.dimen._10sdp), 0, 0)
        }
        root.addView(resultTv)

        fun showResult(message: String, color: Int) {
            resultTv.text = message
            resultTv.setTextColor(color)
            resultTv.visibility = View.VISIBLE
        }

        // ── Footer: Tutup / Batal ──
        val footerBtn = textButton(context, c, context.getString(R.string.close))
        root.addView(vSpace(context, SdpR.dimen._8sdp))
        root.addView(LinearLayout(context).apply {
            gravity = Gravity.END
            addView(footerBtn)
        }, LinearLayout.LayoutParams(-1, -2))
        footerBtn.setOnClickListener {
            if (workJob?.isActive == true) workJob?.cancel() else dialog.dismiss()
        }

        fun setWorking(working: Boolean) {
            dialog.setCanceledOnTouchOutside(!working)
            segment.alpha = if (working) 0.5f else 1f
            segmentViews.values.forEach { it.isClickable = !working }
            actionRow.visibility = if (working) View.GONE else View.VISIBLE
            if (freeExport) freeTv.visibility = if (working) View.GONE else View.VISIBLE
            progressPanel.visibility = if (working) View.VISIBLE else View.GONE
            footerBtn.text = context.getString(if (working) R.string.cancel else R.string.close)
            footerBtn.setTextColor(if (working) c.error else c.accent)
        }

        fun perform(action: Action) {
            if (workJob?.isActive == true) return
            val format = selected
            resultTv.visibility = View.GONE
            workJob = scope.launch {
                // Tampilkan progress hanya jika proses tidak langsung selesai
                val showPanel = launch {
                    delay(SHOW_PROGRESS_DELAY_MS)
                    progressTitle.text = context.getString(R.string.export_preparing)
                    progressBar.isIndeterminate = true
                    progressInfo.text = ""
                    setWorking(true)
                }
                try {
                    var renderStart = 0L
                    val file = exported[format]?.takeIf { it.exists() }
                        ?: RecordingExporter.export(context, rec, format) { p ->
                            scope.launch {
                                if (renderStart == 0L) {
                                    renderStart = SystemClock.elapsedRealtime()
                                    progressTitle.text = context.getString(R.string.export_rendering, format.name)
                                    progressBar.isIndeterminate = false
                                }
                                progressBar.progress = p
                                progressInfo.text = progressText(context, p, SystemClock.elapsedRealtime() - renderStart)
                            }
                        }.also { exported[format] = it }
                    showPanel.cancel()
                    setWorking(false)
                    if (freeExport && !freeConsumed) {
                        freeConsumed = true
                        RecordingExporter.consumeFreeExport(context)
                    }

                    val title = "${rec.name}.${format.extension}"
                    when (action) {
                        Action.SHARE -> RecordingExporter.share(context, file, format, title)
                        Action.WHATSAPP -> if (!RecordingExporter.shareToWhatsApp(context, file, format, title)) {
                            showResult(context.getString(R.string.export_whatsapp_not_installed), c.error)
                            RecordingExporter.share(context, file, format, title)
                        }
                        Action.SAVE -> {
                            if (!ensureStoragePermission(context)) {
                                showResult(context.getString(R.string.export_permission_denied), c.error)
                                return@launch
                            }
                            val location = RecordingExporter.saveToDevice(context, file, format)
                            showResult("✓ " + context.getString(R.string.export_saved_to, location), c.success)
                        }
                    }
                } catch (e: CancellationException) {
                    showPanel.cancel()
                    if (dialog.isShowing) {
                        setWorking(false)
                        showResult(context.getString(R.string.export_cancelled), c.textSecondary)
                    }
                    throw e
                } catch (e: Mp3SourceUnavailableException) {
                    showPanel.cancel()
                    setWorking(false)
                    showResult(context.getString(R.string.export_mp3_unavailable), c.error)
                } catch (e: Throwable) {
                    showPanel.cancel()
                    setWorking(false)
                    Log.e("RecordingExport", "Export failed", e)
                    showResult(context.getString(R.string.export_failed), c.error)
                }
            }
        }

        listOf(
            context.getString(R.string.share) to Action.SHARE,
            "WhatsApp" to Action.WHATSAPP,
            context.getString(R.string.export_save_device) to Action.SAVE
        ).forEachIndexed { i, (label, action) ->
            if (i > 0) actionRow.addView(View(context), LinearLayout.LayoutParams(context.sdp(SdpR.dimen._8sdp), 1))
            actionRow.addView(
                boxButton(context, c, label, primary = action == Action.SHARE) {
                    if (rewardUnlocked || workJob?.isActive == true) {
                        perform(action)
                        return@boxButton
                    }
                    showRewardConfirm(
                        context, c,
                        onWatch = {
                            onRequestReward?.invoke {
                                // Dipanggil setelah iklan selesai ditonton
                                if (!dialog.isShowing) return@invoke
                                rewardUnlocked = true
                                perform(action)
                            }
                        },
                        onPaidWithCoins = {
                            // Koin sudah dipotong: buka kunci seperti setelah nonton iklan
                            rewardUnlocked = true
                            perform(action)
                        }
                    )
                },
                LinearLayout.LayoutParams(0, context.sdp(SdpR.dimen._36sdp), 1f)
            )
        }

        // Tombol back saat proses berjalan = batalkan proses, bukan menutup dialog
        dialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP && workJob?.isActive == true) {
                workJob?.cancel()
                true
            } else false
        }

        dialog.setView(root)
        dialog.show()
        applyWindow(dialog, context, 0.90, 0.50)
    }

    /** Konfirmasi sebelum menonton rewarded ad, dengan gaya yang sama seperti dialog export. */
    private fun showRewardConfirm(
        context: Context,
        c: RecordingDialogPalette,
        onWatch: () -> Unit,
        onPaidWithCoins: () -> Unit
    ) {
        val dialog = AlertDialog.Builder(context).create()
        val pad = context.sdp(SdpR.dimen._16sdp)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(context, c.surface, SdpR.dimen._12sdp)
            setPadding(pad, pad, pad, context.sdp(SdpR.dimen._8sdp))
        }
        root.addView(android.widget.FrameLayout(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(c.accentSoft)
            }
            val iconSize = context.sdp(SdpR.dimen._18sdp)
            addView(
                icon(context, R.drawable.zaif_rec_ic_play, c.accent),
                android.widget.FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER)
            )
        }, LinearLayout.LayoutParams(context.sdp(SdpR.dimen._36sdp), context.sdp(SdpR.dimen._36sdp)))
        root.addView(vSpace(context, SdpR.dimen._10sdp))
        root.addView(TextView(context).apply {
            text = context.getString(R.string.export_reward_title)
            setTextColor(c.text)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
        })
        root.addView(vSpace(context, SdpR.dimen._4sdp))
        root.addView(TextView(context).apply {
            text = context.getString(R.string.export_reward_message)
            setTextColor(c.textSecondary)
            textSize = 12f
            setLineSpacing(0f, 1.2f)
        })
        root.addView(vSpace(context, SdpR.dimen._8sdp))

        // Bayar dengan koin sebagai ganti iklan (hanya bila host mengaktifkan fitur koin)
        if (ZaifSDKBuilder.load(context)?.isCoin == true) {
            val cost = RecordingExporter.exportCoinCost
            val balance = CoinManager.getBalance(context)
            val enough = balance >= cost
            root.addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(context).apply {
                    text = "🪙 " + context.getString(R.string.your_coin) + ": $balance"
                    setTextColor(c.textSecondary)
                    textSize = 12f
                }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(textButton(context, c, context.getString(R.string.export_pay_coins, cost)).apply {
                    alpha = if (enough) 1f else 0.4f
                    setOnClickListener {
                        if (CoinManager.spendCoins(context, cost)) {
                            dialog.dismiss()
                            onPaidWithCoins()
                        } else {
                            Toast.makeText(context, context.getString(R.string.coin_not_enough), Toast.LENGTH_SHORT).show()
                        }
                    }
                })
            }, LinearLayout.LayoutParams(-1, -2))
        }

        val cancelBtn = textButton(context, c, context.getString(R.string.cancel)).apply {
            setTextColor(c.textSecondary)
            setOnClickListener { dialog.dismiss() }
        }
        val watchBtn = textButton(context, c, context.getString(R.string.export_reward_watch)).apply {
            setOnClickListener {
                dialog.dismiss()
                onWatch()
            }
        }
        root.addView(LinearLayout(context).apply {
            gravity = Gravity.END
            addView(cancelBtn)
            addView(watchBtn)
        }, LinearLayout.LayoutParams(-1, -2))

        dialog.setView(root)
        dialog.show()
        applyWindow(dialog, context, 0.82, 0.45)
    }

    /** Android 9 ke bawah: minta izin penyimpanan. @return true jika boleh menyimpan. */
    private suspend fun ensureStoragePermission(context: Context): Boolean {
        if (!RecordingExporter.needsStoragePermission(context)) return true
        return suspendCancellableCoroutine { cont ->
            Dexter.withContext(context)
                .withPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .withListener(object : PermissionListener {
                    override fun onPermissionGranted(response: PermissionGrantedResponse?) {
                        if (cont.isActive) cont.resume(true)
                    }
                    override fun onPermissionDenied(response: PermissionDeniedResponse?) {
                        if (cont.isActive) cont.resume(false)
                    }
                    override fun onPermissionRationaleShouldBeShown(request: PermissionRequest?, token: PermissionToken?) {
                        token?.continuePermissionRequest()
                    }
                })
                .check()
        }
    }

    // ─── TEXT ────────────────────────────────────────────────────────────────

    private fun progressText(context: Context, percent: Int, elapsedMs: Long): String {
        // Estimasi baru stabil setelah beberapa persen
        if (percent < 3 || elapsedMs < 500) return context.getString(R.string.export_progress_percent, percent)
        val remainingMs = elapsedMs * (100 - percent) / percent
        return context.getString(R.string.export_progress_eta, percent, formatDuration(context, remainingMs))
    }

    private fun formatDuration(context: Context, ms: Long): String {
        val sec = ((ms + 999) / 1000).toInt()
        return if (sec < 60) context.getString(R.string.export_eta_sec, sec)
        else context.getString(R.string.export_eta_min, sec / 60, sec % 60)
    }
}

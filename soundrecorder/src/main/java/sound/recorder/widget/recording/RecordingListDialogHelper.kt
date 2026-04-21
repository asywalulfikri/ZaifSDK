package sound.recorder.widget.recording

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.cardview.widget.CardView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sound.recorder.widget.recording.database.AppDatabase
import sound.recorder.widget.recording.database.RecordedTap
import sound.recorder.widget.recording.database.RecordingEntity
import sound.recorder.widget.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object RecordingListDialogHelper {

    // Palette Warna Warisan (Emas & Kayu)
    private const val COLOR_WOOD_DARK = "#1A0F09"
    private const val COLOR_WOOD_MED = "#2D1B10"
    private const val COLOR_GOLD = "#D2B48C"
    private const val COLOR_IVORY = "#F5F5DC"
    private const val COLOR_GOLD_DIM = "#8A7456"
    private const val COLOR_DANGER = "#CF6679"
    private const val COLOR_WOOD_LIGHT = "#3E2717"

    /**
     * @param instrumentFilter Nama instrumen (misal: "rebana", "harmonica") untuk memfilter daftar.
     */
    fun show(context: Context, instrumentFilter: String, onPlay: (List<RecordedTap>) -> Unit) {
        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor(COLOR_WOOD_DARK))
                cornerRadius = 45f
                setStroke(2, Color.parseColor("#22D2B48C"))
            }
        }

        // ── Header Section ──────────────────────────────────────────
        val headerContainer = FrameLayout(context)
        val headerTextLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 20)
        }

        val titleView = TextView(context).apply {
            text = context.getString(R.string.record_saved)
            setTextColor(Color.parseColor(COLOR_GOLD))
            textSize = 17f
            letterSpacing = 0.05f
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        }
        val subtitleView = TextView(context).apply {
            text = context.getString(R.string.list_record_result)
            setTextColor(Color.parseColor(COLOR_GOLD_DIM))
            textSize = 11f
        }
        headerTextLayout.addView(titleView)
        headerTextLayout.addView(subtitleView)

        val dialog = AlertDialog.Builder(context).create()

        val closeBtn = FrameLayout(context).apply {
            val size = 90
            layoutParams = FrameLayout.LayoutParams(size, size).apply {
                gravity = Gravity.TOP or Gravity.END
                setMargins(0, 20, 20, 0)
            }
            background =
                RippleDrawable(ColorStateList.valueOf(Color.parseColor("#20FFFFFF")), null, null)
            addView(TextView(context).apply {
                text = "✕"
                setTextColor(Color.parseColor(COLOR_GOLD_DIM))
                textSize = 18f
                gravity = Gravity.CENTER
            })
            setOnClickListener { dialog.dismiss() }
        }

        headerContainer.addView(headerTextLayout)
        headerContainer.addView(closeBtn)
        rootLayout.addView(headerContainer)

        // ── List Area ───────────────────────────────────────────────
        val scrollView = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val listLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 10, 40, 40)
        }
        scrollView.addView(listLayout)

        val displayMetrics = context.resources.displayMetrics
        val maxHeight = (displayMetrics.heightPixels * 0.75).toInt()
        rootLayout.addView(scrollView, LinearLayout.LayoutParams(-1, maxHeight))

        dialog.setView(rootLayout)

        CoroutineScope(Dispatchers.Main).launch {
            val recordings = withContext(Dispatchers.IO) {
                AppDatabase.Companion.getInstance(context).recordingDao().getAll()
            }

            // FILTER DATA BERDASARKAN INSTRUMEN
            val filteredRecordings = recordings.filter {
                it.setName.equals(instrumentFilter, ignoreCase = true)
            }

            if (filteredRecordings.isEmpty()) {
                listLayout.addView(showEmptyState(context))
            } else {
                filteredRecordings.forEach { rec ->
                    val row = buildRecordingCard(context, rec, dialog, onPlay, listLayout, filteredRecordings.toMutableList())
                    listLayout.addView(row)
                    listLayout.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(-1, 25) })
                }
            }
        }

        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val width = (displayMetrics.widthPixels * 0.92).toInt()
            setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
            decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }
    }

    private fun buildRecordingCard(
        context: Context,
        rec: RecordingEntity,
        dialog: AlertDialog,
        onPlay: (List<RecordedTap>) -> Unit,
        listLayout: LinearLayout,
        allRecs: MutableList<RecordingEntity>
    ): View {
        val card = CardView(context).apply {
            radius = 30f
            cardElevation = 0f
            setCardBackgroundColor(Color.parseColor(COLOR_WOOD_MED))
        }

        val innerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(35, 30, 35, 30)
            gravity = Gravity.CENTER_VERTICAL
        }

        val iconView = TextView(context).apply {
            text = "⏺"
            setTextColor(Color.parseColor(COLOR_GOLD_DIM))
            textSize = 12f
            setPadding(0, 0, 30, 0)
        }

        val infoLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }

        val nameView = TextView(context).apply {
            text = rec.name.uppercase()
            setTextColor(Color.parseColor(COLOR_IVORY))
            textSize = 13f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        val metaView = TextView(context).apply {
            val sdf = SimpleDateFormat("dd/MM/yy • HH:mm", Locale.getDefault())
            text = "${sdf.format(Date(rec.createdAt))} | ${formatDuration(rec.durationMs)}"
            setTextColor(Color.parseColor(COLOR_GOLD_DIM))
            textSize = 10f
        }
        infoLayout.addView(nameView)
        infoLayout.addView(metaView)

        val actionLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val playBtn = buildIconButton(context, "▶", COLOR_GOLD, Color.BLACK) {
            onPlay(parseEvents(rec.eventsJson))
            dialog.dismiss()
        }

        val deleteBtn = buildIconButton(context, "🗑", "#20CF6679", Color.parseColor(COLOR_DANGER)) {
            CoroutineScope(Dispatchers.Main).launch {
                withContext(Dispatchers.IO) {
                    AppDatabase.Companion.getInstance(context).recordingDao().delete(rec)
                }
                val idx = listLayout.indexOfChild(card)
                if (idx != -1) {
                    listLayout.removeViewAt(idx)
                    // Hapus juga spacer view di bawahnya jika ada
                    if (idx < listLayout.childCount) listLayout.removeViewAt(idx)
                }
                allRecs.remove(rec)
                if (allRecs.isEmpty()) listLayout.addView(showEmptyState(context))
            }
        }

        actionLayout.addView(playBtn)
        actionLayout.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(20, 1) })
        actionLayout.addView(deleteBtn)

        innerLayout.addView(iconView)
        innerLayout.addView(infoLayout)
        innerLayout.addView(actionLayout)
        card.addView(innerLayout)

        return card
    }

    private fun buildIconButton(context: Context, icon: String, bgColor: String, iconColor: Int, onClick: () -> Unit) = FrameLayout(
        context
    ).apply {
        val btnSize = 85
        layoutParams = LinearLayout.LayoutParams(btnSize, btnSize)
        background = RippleDrawable(
            ColorStateList.valueOf(Color.parseColor("#40FFFFFF")),
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(bgColor))
                if (bgColor.startsWith("#20")) {
                    setStroke(2, iconColor)
                }
            },
            null
        )

        addView(TextView(context).apply {
            text = icon
            setTextColor(iconColor)
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        })
        setOnClickListener { onClick() }
    }

    private fun showEmptyState(context: Context) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(0, 100, 0, 100)

        addView(TextView(context).apply {
            text = "∅"
            setTextColor(Color.parseColor(COLOR_WOOD_LIGHT))
            textSize = 40f
        })
        addView(TextView(context).apply {
            text = context.getString(R.string.no_data_record)
            setTextColor(Color.parseColor(COLOR_GOLD_DIM))
            textSize = 12f
            setPadding(0, 20, 0, 0)
        })
    }

    private fun formatDuration(ms: Long): String {
        val sec = ms / 1000
        return if (sec < 60) "${sec}s" else  "${sec / 60}m ${sec % 60}s"
    }

    private fun parseEvents(json: String): List<RecordedTap> {
        if (json.isEmpty()) return emptyList()
        val result = mutableListOf<RecordedTap>()
        Log.d("RECORD_DEBUG", "Raw JSON dari DB: $json")

        // Regex untuk mengambil padIndex, timestamp, dan metadata secara aman
        val pattern = Regex("""padIndex=(\d+),timestamp=(\d+),meta=([^|]*)""")

        try {
            pattern.findAll(json).forEach { matchResult ->
                val padIndex = matchResult.groupValues[1].toInt()
                val timestamp = matchResult.groupValues[2].toLong()
                val metadata = matchResult.groupValues[3].trim().takeIf { it.isNotEmpty() && it != "null" }

                val tap = RecordedTap(padIndex, timestamp, metadata)
                Log.d("RECORD_DEBUG", "Parsed Tap -> Pad: ${tap.padIndex}, Meta: ${tap.metadata}")
                result.add(tap)
            }
        } catch (e: Exception) {
            Log.e("RECORD_DEBUG", "Error Parsing: ${e.message}")
        }
        return result
    }
}
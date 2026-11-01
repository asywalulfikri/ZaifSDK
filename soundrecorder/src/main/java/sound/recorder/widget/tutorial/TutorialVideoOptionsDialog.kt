package sound.recorder.widget.tutorial

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.InputFilter
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.edit
import androidx.core.graphics.ColorUtils
import sound.recorder.widget.R
import sound.recorder.widget.recording.RecordingDialogPalette
import sound.recorder.widget.recording.RecordingDialogStyle
import sound.recorder.widget.recording.RecordingDialogViews.applyWindow
import sound.recorder.widget.recording.RecordingDialogViews.boxButton
import sound.recorder.widget.recording.RecordingDialogViews.circle
import sound.recorder.widget.recording.RecordingDialogViews.rounded
import sound.recorder.widget.recording.RecordingDialogViews.sdp
import sound.recorder.widget.recording.RecordingDialogViews.styleInput
import sound.recorder.widget.recording.RecordingDialogViews.textButton
import sound.recorder.widget.recording.RecordingDialogViews.vSpace
import com.intuit.sdp.R as SdpR

/** Tampilan overlay video. Pilihan terakhir user disimpan untuk export berikutnya. */
internal data class TutorialVideoStyle(
    val showTitle: Boolean = true,
    val showNote: Boolean = true,
    val showWatermark: Boolean = true,
    val watermark: String? = null,
    val noteColor: Int = DEFAULT_NOTE_COLOR,
    /** Watermark besar di tengah untuk versi gratis; tidak bisa dimatikan. Null = premium. */
    val forcedWatermark: String? = null
) {
    companion object {
        const val DEFAULT_NOTE_COLOR = -14275 // #FFC83D
        private const val PREFS = "tutorial_video_style"

        fun load(context: Context): TutorialVideoStyle {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return TutorialVideoStyle(
                showTitle = p.getBoolean("title", true),
                showNote = p.getBoolean("note", true),
                showWatermark = p.getBoolean("watermark_on", false),
                watermark = p.getString("watermark", null),
                noteColor = p.getInt("note_color", DEFAULT_NOTE_COLOR)
            )
        }

        fun save(context: Context, style: TutorialVideoStyle) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
                putBoolean("title", style.showTitle)
                putBoolean("note", style.showNote)
                putBoolean("watermark_on", style.showWatermark)
                putString("watermark", style.watermark)
                putInt("note_color", style.noteColor)
            }
        }
    }
}

/** Jatah video gratis: sekali sehari, dipakai hanya bila video berhasil tersimpan. */
internal object TutorialVideoQuota {
    private const val PREFS = "tutorial_video_quota"
    private const val KEY_DAY = "free_day"

    private fun today(): Int {
        val cal = java.util.Calendar.getInstance()
        return cal.get(java.util.Calendar.YEAR) * 10_000 +
            (cal.get(java.util.Calendar.MONTH) + 1) * 100 + cal.get(java.util.Calendar.DAY_OF_MONTH)
    }

    fun hasFree(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_DAY, 0) != today()

    fun consume(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putInt(KEY_DAY, today()) }
    }
}

/** Watermark versi gratis di tengah gambar; dipakai sama oleh preview dan rekaman. */
internal fun drawCenterWatermark(canvas: Canvas, text: String, width: Int, height: Int, paint: Paint) {
    paint.clearShadowLayer()
    paint.color = Color.argb(120, 255, 255, 255)
    paint.typeface = Typeface.DEFAULT_BOLD
    paint.textAlign = Paint.Align.CENTER
    paint.textSize = height * 0.12f
    val textWidth = paint.measureText(text)
    val maxWidth = width * 0.8f
    if (textWidth > maxWidth) paint.textSize *= maxWidth / textWidth
    val y = height / 2f - (paint.descent() + paint.ascent()) / 2f
    canvas.drawText(text, width / 2f, y, paint)
}

/** Contoh hasil video: posisi dan ukuran sama dengan overlay di [TutorialVideoRecorder]. */
private class TutorialVideoPreview(context: Context, private val songTitle: String) : View(context) {
    var style = TutorialVideoStyle()
        set(value) { field = value; invalidate() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val w = MeasureSpec.getSize(widthSpec)
        setMeasuredDimension(w, w * 9 / 16)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val pad = h * 0.03f
        paint.style = Paint.Style.FILL
        paint.setShadowLayer(0f, 0f, 0f, 0)
        canvas.drawColor(Color.parseColor("#1565C0"))

        // Tuts contoh.
        val keys = 12
        val keyW = w / keys
        val top = h * 0.32f
        paint.color = Color.parseColor("#ECEFF7")
        for (i in 0 until keys) {
            rect.set(i * keyW + keyW * 0.04f, top, (i + 1) * keyW - keyW * 0.04f, h * 0.95f)
            canvas.drawRoundRect(rect, keyW * 0.1f, keyW * 0.1f, paint)
        }
        paint.color = Color.parseColor("#1B1B1F")
        for (i in 1 until keys) {
            if (i % 7 == 3 || i % 7 == 0) continue
            rect.set(i * keyW - keyW * 0.28f, top, i * keyW + keyW * 0.28f, h * 0.68f)
            canvas.drawRoundRect(rect, keyW * 0.08f, keyW * 0.08f, paint)
        }

        paint.textAlign = Paint.Align.LEFT
        paint.typeface = Typeface.DEFAULT_BOLD
        if (style.showTitle) {
            paint.textSize = h * 0.055f
            val th = paint.textSize
            rect.set(pad, pad, pad + paint.measureText(songTitle) + th * 1.2f, pad + th * 1.7f)
            paint.color = Color.argb(150, 0, 0, 0)
            canvas.drawRoundRect(rect, rect.height() / 2, rect.height() / 2, paint)
            paint.color = Color.WHITE
            canvas.drawText(songTitle, rect.left + th * 0.6f, rect.centerY() + th * 0.35f, paint)
        }
        if (style.showNote) {
            paint.textSize = h * 0.09f
            val nh = paint.textSize
            rect.set(w - pad - nh * 1.8f, pad, w - pad, pad + nh * 1.5f)
            paint.color = style.noteColor
            canvas.drawRoundRect(rect, nh * 0.35f, nh * 0.35f, paint)
            paint.color = Color.parseColor("#2B2340")
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("5", rect.centerX(), rect.centerY() + nh * 0.35f, paint)
        }
        val mark = style.watermark
        if (style.showWatermark && !mark.isNullOrBlank()) {
            paint.textSize = h * 0.05f
            paint.textAlign = Paint.Align.RIGHT
            paint.color = Color.parseColor("#2B2340")
            canvas.drawText(mark, w - pad, h - pad, paint)
        }
        style.forcedWatermark?.let { drawCenterWatermark(canvas, it, width, height, paint) }
    }
}

/** Dialog custom tampilan video sebelum rekaman dimulai. */
internal object TutorialVideoOptionsDialog {

    private const val MAX_WATERMARK = 24
    private val NOTE_COLORS = intArrayOf(
        TutorialVideoStyle.DEFAULT_NOTE_COLOR,
        Color.parseColor("#4FC3F7"),
        Color.parseColor("#81C784"),
        Color.parseColor("#FF8A80"),
        Color.WHITE
    )

    @SuppressLint("SetTextI18n")
    fun show(
        context: Context,
        songTitle: String,
        forcedWatermark: String?,
        onStart: (TutorialVideoStyle) -> Unit
    ) {
        val c = RecordingDialogPalette(context, RecordingDialogStyle.default)
        var current = TutorialVideoStyle.load(context).copy(forcedWatermark = forcedWatermark)
        val dialog = AlertDialog.Builder(context).create()
        val preview = TutorialVideoPreview(context, songTitle).apply { style = current }

        fun update(change: (TutorialVideoStyle) -> TutorialVideoStyle) {
            current = change(current)
            preview.style = current
        }

        val pad = context.sdp(SdpR.dimen._14sdp)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(context, c.surface, SdpR.dimen._12sdp)
            setPadding(pad, pad, pad, context.sdp(SdpR.dimen._8sdp))
        }
        root.addView(TextView(context).apply {
            text = context.getString(R.string.tutorial_video_options_title)
            setTextColor(c.text)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
        })
        if (forcedWatermark != null) {
            root.addView(TextView(context).apply {
                text = context.getString(R.string.tutorial_video_free_hint)
                setTextColor(c.textSecondary)
                textSize = 11f
            })
        }
        root.addView(vSpace(context, SdpR.dimen._8sdp))

        val controls = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        fun addSwitch(label: Int, checked: Boolean, onChange: (Boolean) -> Unit): SwitchCompat =
            SwitchCompat(context).apply {
                text = context.getString(label)
                setTextColor(c.text)
                textSize = 13f
                isChecked = checked
                setOnCheckedChangeListener { _, value -> onChange(value) }
                controls.addView(this, LinearLayout.LayoutParams(-1, context.sdp(SdpR.dimen._30sdp)))
            }

        addSwitch(R.string.tutorial_video_show_title, current.showTitle) { v -> update { it.copy(showTitle = v) } }
        addSwitch(R.string.tutorial_video_show_note, current.showNote) { v -> update { it.copy(showNote = v) } }

        val colorRow = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        colorRow.addView(TextView(context).apply {
            text = context.getString(R.string.tutorial_video_note_color)
            setTextColor(c.textSecondary)
            textSize = 12f
        }, LinearLayout.LayoutParams(0, -2, 1f))
        val swatches = NOTE_COLORS.map { color ->
            View(context).also { swatch ->
                val size = context.sdp(SdpR.dimen._20sdp)
                colorRow.addView(swatch, LinearLayout.LayoutParams(size, size).apply {
                    marginStart = context.sdp(SdpR.dimen._5sdp)
                })
            }
        }
        fun refreshSwatches() {
            swatches.forEachIndexed { i, swatch ->
                val selected = NOTE_COLORS[i] == current.noteColor
                swatch.background = circle(NOTE_COLORS[i]).apply {
                    setStroke(
                        context.sdp(if (selected) SdpR.dimen._2sdp else SdpR.dimen._1sdp),
                        if (selected) c.accent else ColorUtils.setAlphaComponent(c.text, 60)
                    )
                }
            }
        }
        swatches.forEachIndexed { i, swatch ->
            swatch.setOnClickListener {
                update { it.copy(noteColor = NOTE_COLORS[i]) }
                refreshSwatches()
            }
        }
        refreshSwatches()
        controls.addView(colorRow, LinearLayout.LayoutParams(-1, context.sdp(SdpR.dimen._28sdp)))

        val input = EditText(context).apply {
            hint = context.getString(R.string.tutorial_video_watermark_hint)
            setText(current.watermark.orEmpty())
            isSingleLine = true
            filters = arrayOf(InputFilter.LengthFilter(MAX_WATERMARK))
            styleInput(context, c, this)
            isEnabled = current.showWatermark
            alpha = if (current.showWatermark) 1f else 0.5f
            addTextChangedListener(object : android.text.TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) {
                    update { it.copy(watermark = s?.toString()?.trim()) }
                }
                override fun beforeTextChanged(s: CharSequence?, st: Int, cnt: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, cnt: Int) {}
            })
        }
        addSwitch(R.string.tutorial_video_show_watermark, current.showWatermark) { v ->
            update { it.copy(showWatermark = v) }
            input.isEnabled = v
            input.alpha = if (v) 1f else 0.5f
        }
        controls.addView(input, LinearLayout.LayoutParams(-1, -2))

        val body = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        body.addView(preview, LinearLayout.LayoutParams(0, -2, 1f))
        body.addView(ScrollView(context).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(controls)
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = pad })
        root.addView(body, LinearLayout.LayoutParams(-1, -2))

        root.addView(vSpace(context, SdpR.dimen._8sdp))
        root.addView(LinearLayout(context).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            addView(textButton(context, c, context.getString(R.string.cancel)).apply {
                setTextColor(c.textSecondary)
                setOnClickListener { dialog.dismiss() }
            })
            addView(View(context), LinearLayout.LayoutParams(context.sdp(SdpR.dimen._8sdp), 1))
            addView(boxButton(context, c, context.getString(R.string.tutorial_video_start), primary = true) {
                TutorialVideoStyle.save(context, current)
                dialog.dismiss()
                onStart(current)
            }, LinearLayout.LayoutParams(context.sdp(SdpR.dimen._120sdp), context.sdp(SdpR.dimen._32sdp)))
        }, LinearLayout.LayoutParams(-1, -2))

        dialog.setView(root)
        dialog.show()
        applyWindow(dialog, context, 0.94, 0.76)
    }
}

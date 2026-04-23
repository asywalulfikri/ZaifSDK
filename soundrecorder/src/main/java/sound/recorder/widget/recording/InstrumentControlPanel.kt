package sound.recorder.widget.recording

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import sound.recorder.widget.R
import sound.recorder.widget.music.InstrumentDialogHelper
import sound.recorder.widget.music.MusicListDialogHelper
import sound.recorder.widget.recording.database.AppDatabase
import sound.recorder.widget.recording.database.RecordedTap
import sound.recorder.widget.recording.database.RecordingEntity
import sound.recorder.widget.ui.bottomSheet.BottomSheetNote

class InstrumentControlPanel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    // ─── 1. KONTRAK & KONFIGURASI ───
    interface InstrumentControlListener {
        fun onRecordStatusChanged(isRecording: Boolean)
        fun onPlaybackEvent(event: RecordedTap)
        fun onStopAll()

        fun onVolume()
    }

    data class ControlConfig(
        val textColor: Int = Color.parseColor("#F5D76E"),
        val bgColor: Int = Color.parseColor("#1F1612"),
        val btnColor: Int = Color.parseColor("#3D2510"),
        val btnPressedColor: Int = Color.parseColor("#4A2E1C"),
        val strokeColor: Int = Color.parseColor("#9B6A14"),
        val cornerRadius: Int = 8,
        val fontSize: Float = 10f,
        val fontResId: Int? = null
    )


    interface AdRequestListener {
        fun onShowRewardedAd(type: String, onComplete: () -> Unit)
    }

    // ─── 2. STATE & MANAGER ───
    private var listener: InstrumentControlListener? = null
    private var config = ControlConfig()
    private var instrumentType = "general"
    private var isRecording = false
    private var globalTypeface: Typeface? = null

    var adRequestListener: AdRequestListener? = null
    var onRequestAudioPermission: (() -> Unit)? = null
    var isMusicUnlocked = false
    var isListRecordUnlocked = false

    private val recorderManager = InstrumentRecorderManager { event ->
        listener?.onPlaybackEvent(event)
    }

    private val blinkHandler = Handler(Looper.getMainLooper())
    private lateinit var btnRecord: Button
    private lateinit var btnMusic: Button
    private lateinit var btnList: Button
    private lateinit var btnStop: Button
    private lateinit var btnNote: Button

    private lateinit var btnVolume: Button

    private val blinkRunnable = object : Runnable {
        private var isOn = false
        override fun run() {
            isOn = !isOn
            if (::btnRecord.isInitialized) {
                val stopStr = try { context.getString(R.string.stop) } catch (e: Exception) { "STOP" }
                btnRecord.text = if (isOn) "${stopStr.uppercase()} ●" else stopStr.uppercase()
                btnRecord.setTextColor(if (isOn) Color.RED else config.textColor)
            }
            blinkHandler.postDelayed(this, 600)
        }
    }

    init {
        val orientationAttr = attrs?.getAttributeIntValue("http://schemas.android.com/apk/res/android", "orientation", HORIZONTAL) ?: HORIZONTAL
        this.orientation = orientationAttr
        this.setBackgroundColor(Color.TRANSPARENT)
        loadCustomFont()
        renderUI()
    }

    private fun loadCustomFont() {
        config.fontResId?.let {
            globalTypeface = ResourcesCompat.getFont(context, it)
        }
    }

    fun setup(type: String, orientation: Int, config: ControlConfig = ControlConfig(), listener: InstrumentControlListener) {
        this.instrumentType = type
        this.config = config
        this.listener = listener
        this.orientation = orientation
        loadCustomFont()
        renderUI()

    }

    fun setNoteButtonVisible(isVisible: Boolean) {
        if (::btnNote.isInitialized) {
            btnNote.visibility = if (isVisible) VISIBLE else GONE
        }
    }

    fun setVolumeButtonVisible(isVisible: Boolean) {
        if (::btnVolume.isInitialized) {
            btnVolume.visibility = if (isVisible) VISIBLE else GONE
        }
    }

    private fun renderUI() {
        this.removeAllViews()
        this.setBackgroundColor(Color.TRANSPARENT)
        this.gravity = Gravity.CENTER
        val p = (4 * resources.displayMetrics.density).toInt()
        setPadding(p, p, p, p)

        btnMusic = createBtn(context.getString(R.string.music_unlock).uppercase())
        btnRecord = createBtn("REC ●")
        btnList = createBtn(context.getString(R.string.list_record_unlock).uppercase())
        btnNote = createBtn(context.getString(R.string.note).uppercase())
        btnStop = createBtn(context.getString(R.string.stop).uppercase())
        btnVolume = createBtn(context.getString(R.string.volume).uppercase())
        btnStop.visibility = GONE

        // FIX PANJANG PENDEK: Kunci lebar minimal tombol record
        val recordMinWidth = dpToPx(65)
        btnRecord.minimumWidth = recordMinWidth
        btnRecord.minWidth = recordMinWidth

        val layoutParams = if (this.orientation == HORIZONTAL) {
            LayoutParams(LayoutParams.WRAP_CONTENT, dpToPx(38)).apply {
                setMargins(dpToPx(4), dpToPx(2), dpToPx(4), dpToPx(2))
            }
        } else {
            LayoutParams(dpToPx(65), dpToPx(45)).apply {
                setMargins(dpToPx(2), dpToPx(4), dpToPx(2), dpToPx(4))
            }
        }

        arrayOf(btnMusic, btnRecord, btnList, btnNote, btnStop,btnVolume).forEach { btn ->
            if (btn != btnRecord) {
                btn.minWidth = 0
                btn.minimumWidth = 0
            }
            btn.minHeight = 0
            btn.minimumHeight = 0
            if (this.orientation == HORIZONTAL) btn.setPadding(dpToPx(12), 0, dpToPx(12), 0)
            else btn.setPadding(dpToPx(4), dpToPx(8), dpToPx(4), dpToPx(8))
            this.addView(btn, layoutParams)
        }

        setupClickListeners()
        refreshLockLabels()
    }

    fun setUnlockedStatus(isPremium: Boolean, musicUnlocked: Boolean, listRecordUnlocked: Boolean) {
        this.isMusicUnlocked = isPremium || musicUnlocked
        this.isListRecordUnlocked = isPremium || listRecordUnlocked
        refreshStatusLabels()
    }

    private fun refreshStatusLabels() {
        btnMusic.text = if (isMusicUnlocked) context.getString(R.string.music_unlock).uppercase() else context.getString(
            R.string.music_lock).uppercase()
        btnList.text = if (isListRecordUnlocked) context.getString(R.string.list_record_unlock).uppercase() else context.getString(
            R.string.list_record_lock).uppercase()
        invalidate()
    }


    fun recordEvent(index: Int, metadata: String) {
        if (isRecording) {
            recorderManager.onNoteEvent(index, metadata)
        }
    }

    private fun setupClickListeners() {
        btnMusic.setOnClickListener {
            if (!isMusicUnlocked) {
                InstrumentDialogHelper.showUnlockDialog(context, context.getString(R.string.list_music).uppercase()) {
                    if (!isNetworkAvailable()) {
                        Toast.makeText(context, context.getString(R.string.no_internet_connection), Toast.LENGTH_SHORT).show()
                    }else{
                        adRequestListener?.onShowRewardedAd("music") {
                            isMusicUnlocked = true;
                            refreshLockLabels()
                            handleMusicOpen()
                        }
                    }
                }
            } else handleMusicOpen()
        }

        btnRecord.setOnClickListener {
            if (isRecording) {
                stopRecording()
            } else {
                showStartRecordConfirmation()
            }
        }

        btnList.setOnClickListener {
            if (!isListRecordUnlocked) {

                InstrumentDialogHelper.showUnlockDialog(context, context.getString(R.string.list_record_result).uppercase()) {

                    if (!isNetworkAvailable()) {
                        Toast.makeText(context, context.getString(R.string.no_internet_connection), Toast.LENGTH_SHORT).show()
                    }else{
                        adRequestListener?.onShowRewardedAd("list_record") {
                            isListRecordUnlocked = true;
                            refreshLockLabels();
                            openRecordList()
                        }
                    }
                }
            } else openRecordList()
        }


        btnNote.setOnClickListener {
            val activity = context as? FragmentActivity
            activity?.let {
                val bottomSheet = BottomSheetNote()
                bottomSheet.show(it.supportFragmentManager, "xx")
            }
        }

        btnStop.setOnClickListener {
            recorderManager.stopPlayback()
            btnStop.visibility = GONE
            listener?.onStopAll()
        }

        btnVolume.setOnClickListener {
            listener?.onVolume()
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun showStartRecordConfirmation() {
        InstrumentDialogHelper.showRecordDialog(context) {
            startRecording()
        }
    }

    private fun startRecording() {
        isRecording = true
        recorderManager.startRecording()
        listener?.onRecordStatusChanged(true)
        blinkHandler.post(blinkRunnable)
        btnStop.visibility = GONE
    }

    private fun stopRecording() {
        isRecording = false
        blinkHandler.removeCallbacks(blinkRunnable)
        if (::btnRecord.isInitialized) {
            btnRecord.text = "REC ●"
            btnRecord.setTextColor(config.textColor)
        }
        listener?.onRecordStatusChanged(false)

        val events = recorderManager.stopRecording()
        if (events.isNotEmpty()) {
            InstrumentDialogHelper.showSaveRecordDialog(context) { name ->
                Toast.makeText(context,context.getString(R.string.success_saved), Toast.LENGTH_SHORT).show()
                val json = recorderManager.getEventsAsString(events)
                CoroutineScope(Dispatchers.IO).launch {
                    AppDatabase.getInstance(context).recordingDao().insert(
                        RecordingEntity(name = name, setName = instrumentType, eventsJson = json, durationMs = events.last().timestamp)
                    )
                }
            }
        }
    }

    private fun openRecordList() {
        RecordingListDialogHelper.show(context, instrumentType) { events ->
            if (events.isNotEmpty()) {
                btnStop.visibility = VISIBLE
                recorderManager.play(events) {
                    post {
                        btnStop.visibility = GONE
                        invalidate()
                    }
                }
            }
        }
    }

    private fun handleMusicOpen() {
        val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED) onRequestAudioPermission?.invoke()
        else MusicListDialogHelper.show(context)
    }

    private fun refreshLockLabels() {
        if (::btnMusic.isInitialized && ::btnList.isInitialized) {
            btnMusic.text = if (isMusicUnlocked) context.getString(R.string.music_unlock).uppercase() else context.getString(
                R.string.music_lock).uppercase()
            btnList.text = if (isListRecordUnlocked) context.getString(R.string.list_record_unlock).uppercase() else context.getString(
                R.string.list_record_lock).uppercase()
        }
    }

    private fun createBtn(label: String) = Button(context).apply {
        text = label; setTextColor(config.textColor); textSize = config.fontSize
        typeface = globalTypeface ?: Typeface.DEFAULT_BOLD
        setPadding(0, 0, 0, 0)
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), createBg(true))
            addState(intArrayOf(), createBg(false))
        }
    }

    private fun createBg(pressed: Boolean) = GradientDrawable().apply {
        setColor(if (pressed) config.btnPressedColor else config.btnColor)
        cornerRadius = dpToPx(config.cornerRadius).toFloat()
        setStroke(dpToPx(1), config.strokeColor)
    }

    private fun dpToPx(dp: Int) = (dp * resources.displayMetrics.density).toInt()
}
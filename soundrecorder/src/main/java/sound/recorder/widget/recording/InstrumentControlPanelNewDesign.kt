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
import kotlinx.coroutines.withContext
import sound.recorder.widget.R
import sound.recorder.widget.builder.ZaifSDKBuilder
import sound.recorder.widget.builder.ZaifSDKConfig
import sound.recorder.widget.music.InstrumentDialogHelper
import sound.recorder.widget.music.MusicListDialogHelper
import sound.recorder.widget.recording.database.AppDatabase
import sound.recorder.widget.recording.database.RecordedTap
import sound.recorder.widget.recording.database.RecordingEntity
import sound.recorder.widget.ui.bottomSheet.BottomSheetNotesTabbed
import com.intuit.sdp.R as SdpR

class InstrumentControlPanelNewDesign @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    // ─── KONTRAK ───
    interface InstrumentControlListener {
        fun onRecordStatusChanged(isRecording: Boolean)
        fun onPlaybackEvent(event: RecordedTap)
        fun onStopAll()
        fun onVolume()
        fun onMuteControl(mute: Boolean)
    }

    interface AdRequestListener {
        fun onShowRewardedAd(type: String, onComplete: () -> Unit)
    }

    // ─── STATE ───
    private var listener: InstrumentControlListener? = null
    private var config = ControlConfig()
    private var instrumentType = "general"
    private var isRecording = false
    private var isMicMode = false
    private var isEarphoneWhenRecording = false
    private var globalTypeface: Typeface? = null
    private var activeDialog: android.app.Dialog? = null

    var adRequestListener: AdRequestListener? = null
    var onRequestAudioPermissionMic: (() -> Unit)? = null
    var onRequestAudioPermissionAudio: (() -> Unit)? = null
    var isMusicUnlocked = false
    var isListRecordUnlocked = false
    var showPromotButton = true

    // ─── TIMER RECORDING ───
    // Dipanggil setiap detik saat recording berjalan.
    // Parameter: elapsedMs (Long), formattedTime (String) contoh "01:23"
    var onRecordingTick: ((elapsedMs: Long, formattedTime: String) -> Unit)? = null

    // Dipanggil saat recording selesai (save atau cancel).
    // isSaved = true  → user klik Save
    // isSaved = false → user klik Cancel / discard
    var onRecordingStopped: ((isSaved: Boolean) -> Unit)? = null

    private var recordingStartTime = 0L
    private val timerHandler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (!isRecording) return
            val elapsed = System.currentTimeMillis() - recordingStartTime
            val formatted = formatDuration(elapsed)
            onRecordingTick?.invoke(elapsed, formatted)
            timerHandler.postDelayed(this, 1000)
        }
    }

    private fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val minutes  = totalSec / 60
        val seconds  = totalSec % 60
        return "%02d:%02d".format(minutes, seconds)
    }

    // ─── HELPERS ───
    private lateinit var audioEngine: AudioEngine
    private lateinit var btnFactory: ControlButtonFactory
    private lateinit var blinkManager: BlinkManager

    private val recorderManager = InstrumentRecorderManager { event ->
        listener?.onPlaybackEvent(event)
    }

    // ─── BUTTONS ───
    private lateinit var btnRecord: Button
    private lateinit var btnMusic: Button
    private lateinit var btnList: Button
    private lateinit var btnStop: Button
    private lateinit var btnNote: Button
    private lateinit var btnVolume: Button

    var zaifSDKConfig : ZaifSDKConfig? =null

    private fun sdp(id: Int) = resources.getDimensionPixelSize(id)

    init {
        this.orientation = HORIZONTAL
        this.setBackgroundColor(Color.TRANSPARENT)
        initHelpers()
        renderUI()
    }

    private fun initHelpers() {
        loadCustomFont()
        zaifSDKConfig = ZaifSDKBuilder.load(context)
        audioEngine = AudioEngine(context)
        btnFactory  = ControlButtonFactory(context, config, globalTypeface)
        blinkManager = BlinkManager(
            config    = config,
            factory   = btnFactory,
            getRecordBtn = { if (::btnRecord.isInitialized) btnRecord else null },
            getStopBtn   = { if (::btnStop.isInitialized) btnStop else null },
            getRecordLabel = { try { context.getString(R.string.stop) } catch (e: Exception) { "STOP" } },
            isGameStyle = true
        )
    }

    // ─── SETUP PUBLIC ───
    fun setup(
        type: String,
        orientation: Int,
        config: ControlConfig = ControlConfig(),
        listener: InstrumentControlListener
    ) {
        this.instrumentType = type
        this.config = config
        this.listener = listener
        this.orientation = orientation
        initHelpers()
        renderUI()
    }

    fun setUnlockedStatus(isPremium: Boolean, musicUnlocked: Boolean, listRecordUnlocked: Boolean) {
        isMusicUnlocked = isPremium || musicUnlocked
        isListRecordUnlocked = isPremium || listRecordUnlocked || zaifSDKConfig?.isLockRec == false
        refreshStatusLabels()
    }

    /**
     * Update warna semua button tanpa reset state recording/playback.
     * Panggil dari Fragment saat user ganti tema/warna.
     */
    fun updateButtonColors(newConfig: ControlConfig) {
        this.config = newConfig

        // Re-init factory & blinkManager dengan config baru
        btnFactory = ControlButtonFactory(context, config, globalTypeface)
        blinkManager = BlinkManager(
            config = config,
            factory = btnFactory,
            getRecordBtn = { if (::btnRecord.isInitialized) btnRecord else null },
            getStopBtn = { if (::btnStop.isInitialized) btnStop else null },
            getRecordLabel = {
                try {
                    context.getString(R.string.stop)
                } catch (e: Exception) {
                    "STOP"
                }
            },
            isGameStyle = true
        )

        // Update tiap button tanpa rebuild layout (state recording aman)
        if (::btnMusic.isInitialized)  applyNormalStyle(btnMusic)
        if (::btnList.isInitialized)   applyNormalStyle(btnList)
        if (::btnNote.isInitialized)   applyNormalStyle(btnNote)
        if (::btnVolume.isInitialized) applyNormalStyle(btnVolume)
        if (::btnStop.isInitialized)   applyRedStyle(btnStop)
        // btnRecord: kalau sedang recording biarkan blinkManager yang handle warnanya
        if (::btnRecord.isInitialized && !isRecording) applyNormalStyle(btnRecord)
    }

    private fun applyNormalStyle(btn: Button) {
        btn.setTextColor(config.textColor)
        btn.background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), btnFactory.createBg(pressed = true))
            addState(intArrayOf(), btnFactory.createBg(pressed = false))
        }
    }

    private fun applyRedStyle(btn: Button) {
        btn.setTextColor(Color.WHITE)
        btn.background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), btnFactory.createBg(pressed = true, isRed = true))
            addState(intArrayOf(), btnFactory.createBg(pressed = false, isRed = true))
        }
    }

    // ─── UI ───
    private fun renderUI() {
        removeAllViews()
        gravity = Gravity.CENTER
        
        // Main Bar Style (Blue with Yellowish border)
        background = GradientDrawable().apply {
            setColor(Color.parseColor("#4FC3F7")) 
            cornerRadius = sdpF(SdpR.dimen._25sdp)
            setStroke(sdp(SdpR.dimen._2sdp), Color.parseColor("#FFF176"))
        }

        val pad = sdp(SdpR.dimen._6sdp)
        setPadding(pad, pad, pad, pad)

        // Buttons based on Image colors
        btnMusic  = btnFactory.createGameBtn(context.getString(R.string.music_unlock), "FFB347", R.drawable.icon_music)
        btnRecord = btnFactory.createGameBtn(context.getString(R.string.record), "8BC34A", R.drawable.ic_record)
        btnList   = btnFactory.createGameBtn("LIST REC", "9C27B0", R.drawable.ic_list)
        btnNote   = btnFactory.createGameBtn(context.getString(R.string.note), "E91E63", R.drawable.notes)
        btnVolume = btnFactory.createGameBtn(context.getString(R.string.volume), "2196F3", R.drawable.baseline_volume_up_24)
        btnStop   = btnFactory.createGameBtn(context.getString(R.string.stop), "F44336", R.drawable.ic_baseline_stop_24)
        
        btnStop.visibility = GONE

        val btnH   = sdp(SdpR.dimen._40sdp)
        val btnW   = sdp(SdpR.dimen._72sdp)
        val margin = sdp(SdpR.dimen._4sdp)

        val lp = LayoutParams(btnW, btnH).apply {
            setMargins(margin, 0, margin, 0)
        }
        
        arrayOf(btnMusic, btnRecord, btnList, btnNote, btnStop, btnVolume).forEach { addView(it, lp) }

        setupClickListeners()
    }

    private fun sdpF(id: Int) = resources.getDimension(id)

    private fun setupClickListeners() {

        btnMusic.setOnClickListener {
            if (!isMusicUnlocked) {
                if (!isNetworkAvailable()) { setToast(context.getString(R.string.no_internet_connection)); return@setOnClickListener }
                InstrumentDialogHelper.showUnlockDialog(context, context.getString(R.string.list_music).uppercase()) {
                    adRequestListener?.onShowRewardedAd("music") { isMusicUnlocked = true; refreshStatusLabels(); handleMusicOpen() }
                }
            } else handleMusicOpen()
        }


        btnRecord.setOnClickListener { if (isRecording) stopRecording() else showStartRecordConfirmation() }

        btnList.setOnClickListener {
            if (!isListRecordUnlocked) {
                InstrumentDialogHelper.showUnlockDialog(context, context.getString(R.string.list_record_result).uppercase()) {
                    if (isNetworkAvailable()) {
                        adRequestListener?.onShowRewardedAd("list_record") { isListRecordUnlocked = true; refreshStatusLabels(); openRecordList() }
                    } else setToast(context.getString(R.string.no_internet_connection))
                }
            } else openRecordList()
        }

        btnStop.setOnClickListener {
            recorderManager.stopPlayback()
            audioEngine.stopPlayingAudio()
            blinkManager.stopStopBlink()
            btnStop.visibility = GONE
            listener?.onStopAll()
            listener?.onMuteControl(false)
        }

        btnVolume.setOnClickListener { listener?.onVolume() }

        btnNote.setOnClickListener {
            (context as? FragmentActivity)?.let {
                BottomSheetNotesTabbed().show(it.supportFragmentManager, "note")
            }
        }
    }

    // ─── RECORDING ───
    private fun showStartRecordConfirmation() {
        activeDialog = InstrumentDialogHelper.showRecordChooseDialog(context) { useMic ->
            if (useMic) {
                checkMicPermission {
                    activeDialog = InstrumentDialogHelper.showCountdownDialog(context) {
                        startRecording(true)
                    }
                }
            } else {
                activeDialog = InstrumentDialogHelper.showCountdownDialog(context) {
                    startRecording(false)
                }
            }
        }
    }

    private fun checkMicPermission(onGranted: () -> Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
            onGranted()
        else onRequestAudioPermissionMic?.invoke()
    }

    fun startRecording(useMic: Boolean) {
        isRecording = true
        isMicMode = useMic
        isEarphoneWhenRecording = if (useMic) audioEngine.isEarphonePlugged() else false
        listener?.onMuteControl(false)
        if (useMic) audioEngine.startMicRecording()
        recorderManager.startRecording()
        listener?.onRecordStatusChanged(true)
        blinkManager.startRecordBlink()
        // Mulai timer tick
        recordingStartTime = System.currentTimeMillis()
        timerHandler.post(timerRunnable)
    }

    private fun stopRecording() {
        if (!isRecording) return
        isRecording = false
        timerHandler.removeCallbacks(timerRunnable)
        if (isMicMode) audioEngine.stopMicRecording()
        listener?.onMuteControl(false)
        blinkManager.resetRecordBtn()
        listener?.onRecordStatusChanged(false)

        val events = recorderManager.stopRecording()
        if (events.isNotEmpty()) {
            activeDialog = InstrumentDialogHelper.showSaveRecordDialog(
                context = context,
                onSave = { name ->
                    // User pilih SAVE
                    onRecordingStopped?.invoke(true)
                    val json = recorderManager.getEventsAsString(events)
                    val audioPath = if (isMicMode) audioEngine.currentAudioFile?.absolutePath else null
                    val earphoneUsed = isEarphoneWhenRecording
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            AppDatabase.getInstance(context.applicationContext).recordingDao().insert(
                                RecordingEntity(
                                    name = name,
                                    setName = instrumentType,
                                    eventsJson = json,
                                    durationMs = events.last().timestamp,
                                    audioPath = audioPath,
                                    isEarphoneRecording = earphoneUsed
                                )
                            )
                            withContext(Dispatchers.Main) {
                                setToast(context.getString(R.string.record_saved))
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                setToast("Error saving: ${e.message}")
                            }
                        }
                    }
                },
                onCancel = {
                    // User pilih CANCEL / discard
                    onRecordingStopped?.invoke(false)
                }
            )
        } else {
            // Tidak ada event sama sekali → langsung anggap cancel
            onRecordingStopped?.invoke(false)
        }
    }

    fun recordEvent(index: Int, metadata: String) {
        if (isRecording) recorderManager.onNoteEvent(index, metadata)
    }

    fun setPromotButtonVisible(isVisible: Boolean) {
        showPromotButton = isVisible
    }

    // ─── PLAYBACK ───
    private fun openRecordList() {
        RecordingListDialogHelper.show(context, instrumentType, showPromotButton) { entity ->
            if (entity.eventsJson.isNotEmpty()) {
                btnStop.visibility = VISIBLE
                blinkManager.startStopBlink()

                val events = recorderManager.parseJson(entity.eventsJson)
                val hasAudio = !entity.audioPath.isNullOrEmpty()

                val onFinish: () -> Unit = {
                    post { blinkManager.stopStopBlink(); btnStop.visibility = GONE; listener?.onMuteControl(false); invalidate() }
                }

                if (hasAudio) {
                    listener?.onMuteControl(!entity.isEarphoneRecording)
                    recorderManager.play(events) { post { invalidate() } }
                    audioEngine.startPlayingAudioSync(entity.audioPath!!, onFinish)
                } else {
                    listener?.onMuteControl(false)
                    recorderManager.play(events) { post { blinkManager.stopStopBlink(); btnStop.visibility = GONE; listener?.onMuteControl(false); invalidate() } }
                }
            }
        }
    }

    // ─── STOP PLAYBACK ONLY (dipanggil saat tutorial/learn mulai) ───
    fun stopPlayback() {
        recorderManager.stopPlayback()
        audioEngine.stopPlayingAudio()
        blinkManager.stopStopBlink()
        if (::btnStop.isInitialized) btnStop.visibility = GONE
        listener?.onMuteControl(false)
    }

    // ─── LIFECYCLE ───
    fun releaseAndStop() {
        isRecording = false
        isMicMode = false
        isEarphoneWhenRecording = false
        timerHandler.removeCallbacks(timerRunnable)
        audioEngine.release()
        blinkManager.removeAllCallbacks()
        blinkManager.resetRecordBtn()
        recorderManager.stopPlayback()
        if (::btnStop.isInitialized) btnStop.visibility = GONE
        activeDialog?.dismiss()
        activeDialog = null
    }

    override fun onDetachedFromWindow() {
        releaseAndStop()
        super.onDetachedFromWindow()
    }

    // ─── HELPERS ───
    fun setToast(message: String) {
        val toastContext = context.applicationContext ?: context
        Toast.makeText(toastContext, message, Toast.LENGTH_SHORT).show()
    }

    private fun refreshStatusLabels() {
        if (::btnMusic.isInitialized) btnMusic.text = context.getString(if (isMusicUnlocked) R.string.music_unlock else R.string.music_lock)
        if (::btnList.isInitialized) btnList.text = context.getString(if (isListRecordUnlocked) R.string.list_record_unlock else R.string.list_record_lock)
    }

    private fun handleMusicOpen() {
        val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED)
            onRequestAudioPermissionAudio?.invoke()
        else MusicListDialogHelper.show(context, isDownload = true)
    }

    private fun loadCustomFont() {
        config.fontResId?.let { globalTypeface = ResourcesCompat.getFont(context, it) }
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            cm.activeNetwork?.let { cm.getNetworkCapabilities(it)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } ?: false
        } else {
            @Suppress("DEPRECATION")
            cm.activeNetworkInfo?.isConnected ?: false
        }
    }

    fun setNoteButtonVisible(isVisible: Boolean) {
        if (::btnNote.isInitialized) btnNote.visibility = if (isVisible) VISIBLE else GONE
    }

    fun setVolumeButtonVisible(isVisible: Boolean) {
        if (::btnVolume.isInitialized) btnVolume.visibility = if (isVisible) VISIBLE else GONE
    }

    /**
     * Show/hide STOP button and handle blinking.
     * Use this when external playback (like UserNote) starts/stops.
     */
    fun setPlaybackStatus(isPlaying: Boolean) {
        if (!::btnStop.isInitialized) return
        if (isPlaying) {
            btnStop.visibility = VISIBLE
            blinkManager.startStopBlink()
        } else {
            blinkManager.stopStopBlink()
            btnStop.visibility = GONE
        }
    }

    fun setInstrumentType(type: String) { this.instrumentType = type }
}

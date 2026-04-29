package sound.recorder.widget.recording

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
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
import java.io.File
import java.io.IOException
import com.intuit.sdp.R as SdpR

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
        fun onMuteControl(mute: Boolean)
    }

    /**
     * Konfigurasi tampilan tombol.
     *
     * Gradient support:
     * - Set [btnGradientColors] dengan array 2 warna untuk aktifkan gradient
     * - Set [btnGradientOrientation] untuk arah gradient (default TOP_BOTTOM)
     * - Jika [btnGradientColors] null, akan pakai solid color [btnColor]
     *
     * Contoh pakai gradient di Fragment:
     * ```
     * InstrumentControlPanel.ControlConfig(
     *     btnGradientColors = intArrayOf(
     *         Color.parseColor("#5A3010"),  // warna atas
     *         Color.parseColor("#2A1008")   // warna bawah
     *     ),
     *     btnGradientOrientation = GradientDrawable.Orientation.TOP_BOTTOM
     * )
     * ```
     */
    data class ControlConfig(
        val textColor: Int = Color.parseColor("#F5D76E"),
        val bgColor: Int = Color.parseColor("#1F1612"),
        val btnColor: Int = Color.parseColor("#3D2510"),
        val btnPressedColor: Int = Color.parseColor("#4A2E1C"),
        val strokeColor: Int = Color.parseColor("#9B6A14"),
        val cornerRadiusDimenRes: Int = SdpR.dimen._6sdp,
        val fontSize: Float = 8f,
        val fontResId: Int? = null,

        // ─── Gradient support ───
        // Default: gradient emas gelap top-bottom sesuai tema yang sudah ada
        val btnGradientColors: IntArray? = intArrayOf(
            Color.parseColor("#5A3A1A"),  // atas — lebih terang
            Color.parseColor("#2A1508")   // bawah — lebih gelap
        ),
        val btnPressedGradientColors: IntArray? = intArrayOf(
            Color.parseColor("#7A5030"),  // atas pressed — lebih terang
            Color.parseColor("#3D2510")   // bawah pressed
        ),
        val btnGradientOrientation: GradientDrawable.Orientation = GradientDrawable.Orientation.TOP_BOTTOM
    )

    interface AdRequestListener {
        fun onShowRewardedAd(type: String, onComplete: () -> Unit)
    }

    // ─── Helper shorthand ───
    private fun sdp(id: Int)  = resources.getDimensionPixelSize(id)
    private fun sdpF(id: Int) = resources.getDimension(id)

    // ─── 2. STATE & MANAGER ───
    private var listener: InstrumentControlListener? = null
    private var config = ControlConfig()
    private var instrumentType = "general"
    private var isRecording = false
    private var isMicMode = false
    private var isEarphoneWhenRecording = false
    private var globalTypeface: Typeface? = null

    private var mediaRecorder: MediaRecorder? = null
    private var currentAudioFile: File? = null

    private var audioPlayer: MediaPlayer? = null
    private val syncHandler = Handler(Looper.getMainLooper())

    var adRequestListener: AdRequestListener? = null
    var onRequestAudioPermissionMic: (() -> Unit)? = null
    var onRequestAudioPermissionAudio: (() -> Unit)? = null
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

    // ─── BLINK: btnRecord saat rekam ───
    private val blinkRunnable = object : Runnable {
        private var isOn = false
        override fun run() {
            if (!isRecording) return
            isOn = !isOn
            if (::btnRecord.isInitialized) {
                val stopStr = try { context.getString(R.string.stop) } catch (e: Exception) { "STOP" }
                btnRecord.text = if (isOn) "${stopStr.uppercase()} ●" else stopStr.uppercase()
                btnRecord.setTextColor(if (isOn) Color.RED else config.textColor)
            }
            blinkHandler.postDelayed(this, 600)
        }
    }

    // ─── BLINK: btnStop saat playback berjalan ───
    private val blinkStopRunnable = object : Runnable {
        private var isOn = false
        override fun run() {
            if (!::btnStop.isInitialized || btnStop.visibility != VISIBLE) return
            isOn = !isOn
            btnStop.background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_pressed), createBg(pressed = true, isRed = true))
                addState(intArrayOf(), if (isOn) createBgStopOn() else createBg(pressed = false, isRed = true))
            }
            btnStop.setTextColor(if (isOn) Color.WHITE else Color.parseColor("#FF6666"))
            blinkHandler.postDelayed(this, 500)
        }
    }

    private fun createBgStopOn() = GradientDrawable().apply {
        colors = intArrayOf(Color.parseColor("#FF4444"), Color.parseColor("#CC0000"))
        orientation = GradientDrawable.Orientation.TOP_BOTTOM
        cornerRadius = sdpF(config.cornerRadiusDimenRes)
        setStroke(sdp(SdpR.dimen._1sdp), Color.parseColor("#FF9999"))
    }

    private fun startStopBlink() {
        blinkHandler.removeCallbacks(blinkStopRunnable)
        blinkHandler.post(blinkStopRunnable)
    }

    private fun stopStopBlink() {
        blinkHandler.removeCallbacks(blinkStopRunnable)
        if (::btnStop.isInitialized) {
            btnStop.background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_pressed), createBg(pressed = true, isRed = true))
                addState(intArrayOf(), createBg(pressed = false, isRed = true))
            }
            btnStop.setTextColor(Color.WHITE)
        }
    }

    init {
        val orientationAttr = attrs?.getAttributeIntValue(
            "http://schemas.android.com/apk/res/android", "orientation", HORIZONTAL
        ) ?: HORIZONTAL
        this.orientation = orientationAttr
        this.setBackgroundColor(Color.TRANSPARENT)
        loadCustomFont()
        renderUI()
    }

    // ─── 3. AUDIO UTILS (DETEKSI EARPHONE) ───
    private fun isEarphonePlugged(): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            for (device in devices) {
                if (device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    device.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    device.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                    device.type == AudioDeviceInfo.TYPE_USB_DEVICE
                ) return true
            }
        } else {
            @Suppress("DEPRECATION")
            return audioManager.isWiredHeadsetOn || audioManager.isBluetoothA2dpOn
        }
        return false
    }

    // ─── 4. MIC RECORDER ───
    private fun startMicAudioEngine() {
        val fileName = "REC_${System.currentTimeMillis()}.mp3"
        currentAudioFile = File(context.filesDir, fileName)

        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(currentAudioFile?.absolutePath)
            try {
                prepare()
                start()
            } catch (e: IOException) {
                Log.e("InstrumentControl", "MediaRecorder failed: ${e.message}")
            }
        }
    }

    private fun stopMicAudioEngine() {
        try {
            mediaRecorder?.apply { stop(); release() }
        } catch (e: Exception) {
            Log.e("InstrumentControl", "Stop failed: ${e.message}")
        }
        mediaRecorder = null
    }

    // ─── 5. MEDIAPLAYER: PUTAR FILE AUDIO REKAMAN MIC ───
    private fun startPlayingAudioSync(path: String, onComplete: (() -> Unit)? = null) {
        stopPlayingAudio()
        val prepareStartTime = System.currentTimeMillis()
        try {
            audioPlayer = MediaPlayer().apply {
                setDataSource(path)
                setOnPreparedListener { mp ->
                    val prepareElapsed = System.currentTimeMillis() - prepareStartTime
                    Log.d("InstrumentControl", "prepareElapsed=${prepareElapsed}ms")
                    try { mp.seekTo(prepareElapsed.toInt()) } catch (e: Exception) {
                        Log.e("InstrumentControl", "seekTo failed: ${e.message}")
                    }
                    mp.start()
                }
                setOnCompletionListener { stopPlayingAudio(); onComplete?.invoke() }
                setOnErrorListener { _, what, extra ->
                    Log.e("InstrumentControl", "MediaPlayer error: what=$what extra=$extra")
                    stopPlayingAudio(); onComplete?.invoke(); true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e("InstrumentControl", "Failed to play audio: ${e.message}")
            onComplete?.invoke()
        }
    }

    private fun stopPlayingAudio() {
        syncHandler.removeCallbacksAndMessages(null)
        try {
            audioPlayer?.apply { if (isPlaying) stop(); release() }
        } catch (e: Exception) {
            Log.e("InstrumentControl", "Failed to stop audio player: ${e.message}")
        }
        audioPlayer = null
    }

    // ─── 6. UI & SETUP ───
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
        loadCustomFont()
        renderUI()
    }

    fun setUnlockedStatus(isPremium: Boolean, musicUnlocked: Boolean, listRecordUnlocked: Boolean) {
        this.isMusicUnlocked = isPremium || musicUnlocked
        this.isListRecordUnlocked = isPremium || listRecordUnlocked
        refreshStatusLabels()
    }

    private fun renderUI() {
        this.removeAllViews()
        this.gravity = Gravity.CENTER

        val pad = sdp(SdpR.dimen._2sdp)
        setPadding(pad, pad, pad, pad)

        btnMusic  = createBtn(context.getString(R.string.music_unlock).uppercase() + "\uD83C\uDFB5")
        btnRecord = createBtn("REC ●")
        btnList   = createBtn(context.getString(R.string.list_record_unlock).uppercase())
        btnNote   = createBtn(context.getString(R.string.note).uppercase())
        btnStop   = createBtn(context.getString(R.string.stop).uppercase(), isRed = true)
        btnVolume = createBtn(context.getString(R.string.volume).uppercase())
        btnStop.visibility = GONE

        val btnH   = sdp(SdpR.dimen._32sdp)
        val btnW   = sdp(SdpR.dimen._50sdp)
        val margin = sdp(SdpR.dimen._2sdp)

        val layoutParams = LayoutParams(btnW, btnH).apply {
            setMargins(margin, margin, margin, margin)
        }

        arrayOf(btnMusic, btnRecord, btnList, btnNote, btnStop, btnVolume).forEach {
            this.addView(it, layoutParams)
        }
        setupClickListeners()
    }

    private fun setupClickListeners() {
        btnMusic.setOnClickListener {
            if (!isMusicUnlocked) {
                if (!isNetworkAvailable()) {
                    setToast(context.getString(R.string.no_internet_connection))
                    return@setOnClickListener
                }
                InstrumentDialogHelper.showUnlockDialog(
                    context, context.getString(R.string.list_music).uppercase()
                ) {
                    adRequestListener?.onShowRewardedAd("music") {
                        isMusicUnlocked = true; refreshStatusLabels(); handleMusicOpen()
                    }
                }
            } else handleMusicOpen()
        }

        btnRecord.setOnClickListener {
            if (isRecording) stopRecording() else showStartRecordConfirmation()
        }

        btnList.setOnClickListener {
            if (!isListRecordUnlocked) {
                InstrumentDialogHelper.showUnlockDialog(
                    context, context.getString(R.string.list_record_result).uppercase()
                ) {
                    if (isNetworkAvailable()) {
                        adRequestListener?.onShowRewardedAd("list_record") {
                            isListRecordUnlocked = true; refreshStatusLabels(); openRecordList()
                        }
                    } else {
                        setToast(context.getString(R.string.no_internet_connection))
                    }
                }
            } else openRecordList()
        }

        btnStop.setOnClickListener {
            recorderManager.stopPlayback()
            stopPlayingAudio()
            stopStopBlink()
            btnStop.visibility = GONE
            listener?.onStopAll()
            listener?.onMuteControl(false)
        }

        btnVolume.setOnClickListener { listener?.onVolume() }
        btnNote.setOnClickListener {
            (context as? FragmentActivity)?.let {
                BottomSheetNote().show(it.supportFragmentManager, "note")
            }
        }
    }

    // ─── 7. RECORDING LOGIC ───
    private fun showStartRecordConfirmation() {
        InstrumentDialogHelper.showRecordChooseDialog(context) { useMic ->
            if (useMic) checkMicPermission { startRecording(true) }
            else startRecording(false)
        }
    }

    private fun checkMicPermission(onGranted: () -> Unit) {
        if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) onGranted()
        else onRequestAudioPermissionMic?.invoke()
    }

    fun startRecording(useMic: Boolean) {
        isRecording = true
        isMicMode = useMic

        if (useMic) {
            isEarphoneWhenRecording = isEarphonePlugged()
            listener?.onMuteControl(false)
            startMicAudioEngine()
        } else {
            isEarphoneWhenRecording = false
            listener?.onMuteControl(false)
        }

        recorderManager.startRecording()
        listener?.onRecordStatusChanged(true)
        blinkHandler.post(blinkRunnable)
    }

    fun setToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private fun stopRecording() {
        if (!isRecording) return
        isRecording = false
        if (isMicMode) stopMicAudioEngine()
        listener?.onMuteControl(false)

        blinkHandler.removeCallbacks(blinkRunnable)
        if (::btnRecord.isInitialized) {
            btnRecord.text = "REC ●"
            btnRecord.setTextColor(config.textColor)
        }
        listener?.onRecordStatusChanged(false)

        val events = recorderManager.stopRecording()
        if (events.isNotEmpty()) {
            InstrumentDialogHelper.showSaveRecordDialog(context) { name ->
                val json = recorderManager.getEventsAsString(events)
                val audioPath = if (isMicMode) currentAudioFile?.absolutePath else null
                val earphoneUsed = isEarphoneWhenRecording
                CoroutineScope(Dispatchers.IO).launch {
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
                }
            }
        }
    }

    fun recordEvent(index: Int, metadata: String) {
        if (isRecording) recorderManager.onNoteEvent(index, metadata)
    }

    // ─── 8. PLAYBACK LOGIC ───
    private fun openRecordList() {
        RecordingListDialogHelper.show(context, instrumentType) { entity ->
            if (entity.eventsJson.isNotEmpty()) {
                btnStop.visibility = VISIBLE
                startStopBlink()

                val events = recorderManager.parseJson(entity.eventsJson)
                val hasAudioFile = !entity.audioPath.isNullOrEmpty()

                if (hasAudioFile) {
                    val onAudioFinished: () -> Unit = {
                        post {
                            stopStopBlink()
                            btnStop.visibility = GONE
                            listener?.onMuteControl(false)
                            invalidate()
                        }
                    }
                    if (entity.isEarphoneRecording) {
                        listener?.onMuteControl(false)
                        recorderManager.play(events) { post { invalidate() } }
                        startPlayingAudioSync(entity.audioPath!!, onAudioFinished)
                    } else {
                        listener?.onMuteControl(true)
                        recorderManager.play(events) { post { invalidate() } }
                        startPlayingAudioSync(entity.audioPath!!, onAudioFinished)
                    }
                } else {
                    listener?.onMuteControl(false)
                    recorderManager.play(events) {
                        post {
                            stopStopBlink()
                            btnStop.visibility = GONE
                            listener?.onMuteControl(false)
                            invalidate()
                        }
                    }
                }
            }
        }
    }

    // ─── 9. RELEASE & LIFECYCLE ───
    fun releaseAndStop() {
        if (isRecording && isMicMode) stopMicAudioEngine()
        isRecording = false
        isMicMode = false
        isEarphoneWhenRecording = false
        blinkHandler.removeCallbacks(blinkRunnable)
        stopStopBlink()
        recorderManager.stopPlayback()
        stopPlayingAudio()
        if (::btnStop.isInitialized) btnStop.visibility = GONE
        if (::btnRecord.isInitialized) {
            btnRecord.text = "REC ●"
            btnRecord.setTextColor(config.textColor)
        }
    }

    override fun onDetachedFromWindow() {
        releaseAndStop()
        super.onDetachedFromWindow()
    }

    private fun refreshStatusLabels() {
        if (::btnMusic.isInitialized) btnMusic.text =
            if (isMusicUnlocked) context.getString(R.string.music_unlock).uppercase() + "\uD83C\uDFB5"
            else context.getString(R.string.music_lock).uppercase()
        if (::btnList.isInitialized) btnList.text =
            if (isListRecordUnlocked) context.getString(R.string.list_record_unlock).uppercase()
            else context.getString(R.string.list_record_lock).uppercase()
    }

    private fun handleMusicOpen() {
        val perm = if (Build.VERSION.SDK_INT >= 33)
            Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED)
            onRequestAudioPermissionAudio?.invoke()
        else MusicListDialogHelper.show(context)
    }

    private fun loadCustomFont() {
        config.fontResId?.let { globalTypeface = ResourcesCompat.getFont(context, it) }
    }

    private fun createBtn(label: String, isRed: Boolean = false) = Button(context).apply {
        text = label
        setTextColor(if (isRed) Color.WHITE else config.textColor)
        textSize = config.fontSize
        typeface = globalTypeface ?: Typeface.DEFAULT_BOLD
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), createBg(pressed = true, isRed = isRed))
            addState(intArrayOf(), createBg(pressed = false, isRed = isRed))
        }
    }

    /**
     * Buat background button dengan support gradient.
     *
     * Priority:
     * - Kalau [isRed] → pakai gradient merah built-in
     * - Kalau [config.btnGradientColors] tidak null → pakai gradient custom
     * - Kalau null → fallback ke solid color seperti sebelumnya
     */
    private fun createBg(pressed: Boolean, isRed: Boolean = false) = GradientDrawable().apply {
        val corner = sdpF(config.cornerRadiusDimenRes)
        val stroke = sdp(SdpR.dimen._1sdp)
        cornerRadius = corner

        when {
            // ─── Tombol STOP / DELETE — gradient merah ───
            isRed -> {
                colors = if (pressed) {
                    intArrayOf(Color.parseColor("#FF4444"), Color.parseColor("#AA0000"))
                } else {
                    intArrayOf(Color.parseColor("#FF3333"), Color.parseColor("#CC0000"))
                }
                orientation = GradientDrawable.Orientation.TOP_BOTTOM
                setStroke(stroke, Color.parseColor("#FF6666"))
            }

            // ─── Tombol normal — pakai gradient dari config jika ada ───
            pressed && config.btnPressedGradientColors != null -> {
                colors      = config.btnPressedGradientColors
                orientation = config.btnGradientOrientation
                setStroke(stroke, config.strokeColor)
            }
            !pressed && config.btnGradientColors != null -> {
                colors      = config.btnGradientColors
                orientation = config.btnGradientOrientation
                setStroke(stroke, config.strokeColor)
            }

            // ─── Fallback solid color (jika gradient null) ───
            else -> {
                setColor(if (pressed) config.btnPressedColor else config.btnColor)
                setStroke(stroke, config.strokeColor)
            }
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.activeNetwork?.let {
            cm.getNetworkCapabilities(it)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } ?: false
    }

    fun setNoteButtonVisible(isVisible: Boolean) {
        if (::btnNote.isInitialized) btnNote.visibility = if (isVisible) VISIBLE else GONE
    }

    fun setVolumeButtonVisible(isVisible: Boolean) {
        if (::btnVolume.isInitialized) btnVolume.visibility = if (isVisible) VISIBLE else GONE
    }

    fun setInstrumentType(type: String) { this.instrumentType = type }
}
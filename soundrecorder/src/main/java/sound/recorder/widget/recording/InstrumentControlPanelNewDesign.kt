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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sound.recorder.widget.R
import sound.recorder.widget.builder.ZaifSDKBuilder
import sound.recorder.widget.builder.ZaifSDKConfig
import sound.recorder.widget.music.InstrumentDialogHelper
import sound.recorder.widget.music.MusicListDialogHelper
import sound.recorder.widget.recording.database.RecordedMusicSegment
import java.io.File
import sound.recorder.widget.music.MusicPlayerManager
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
    private var isPremium = false

    // ─── TIMER RECORDING ───
    // Dipanggil setiap detik saat recording berjalan.
    // Parameter: elapsedMs (Long), formattedTime (String) contoh "01:23"
    var onRecordingTick: ((elapsedMs: Long, formattedTime: String) -> Unit)? = null

    // Dipanggil saat recording selesai (save atau cancel).
    // isSaved = true  → user klik Save
    // isSaved = false → user klik Cancel / discard
    var onRecordingStopped: ((isSaved: Boolean) -> Unit)? = null

    private var recordingStartTime = 0L
    private var recordingStartedAtWall = 0L
    private var draftTick = 0
    private var pendingRecording: PendingRecording? = null
    // Lagu dari daftar musik yang berbunyi selama rekaman (dicatat) & diputar ulang bersama rekaman
    private val musicTracker = MusicRecordTracker(context)
    private val musicPlayer = RecordingMusicPlayer(context)
    /** Rekaman dasar saat rekam lapis (overdub); null = rekaman biasa. */
    private var overdub: OverdubSession? = null
    /** true selama ketukan hasil putar ulang dikirim ke host. */
    private var dispatchingPlayback = false
    private val timerHandler = Handler(Looper.getMainLooper())
    private var panelScopeJob = SupervisorJob()
    private var panelScope = CoroutineScope(panelScopeJob + Dispatchers.Main.immediate)
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (!isRecording) return
            val elapsed = System.currentTimeMillis() - recordingStartTime
            val formatted = formatDuration(elapsed)
            onRecordingTick?.invoke(elapsed, formatted)
            // Draft tiap 5 detik → rekaman bisa dipulihkan kalau proses app mati mendadak
            if (++draftTick % 5 == 0) writeDraftNow()
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
        // Saat rekam lapis, host bisa saja memanggil recordEvent dari onPlaybackEvent → itu bukan ketukan user
        dispatchingPlayback = true
        try {
            listener?.onPlaybackEvent(event)
        } finally {
            dispatchingPlayback = false
        }
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
        loadZaifConfigAsync()
        if (::audioEngine.isInitialized) audioEngine.release()
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
        // Rekaman dari sesi sebelumnya yang terputus (app mati saat merekam) → masukkan ke daftar
        RecordingAutosave.recoverIfAny(context)
    }

    fun setUnlockedStatus(isPremium: Boolean, musicUnlocked: Boolean, listRecordUnlocked: Boolean) {
        this.isPremium = isPremium
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
            recorderManager.stopPlayback(releaseHeld = true)
            audioEngine.stopPlayingAudio()
            musicPlayer.stop()
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
        if (useMic && ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onRequestAudioPermissionMic?.invoke()
            return
        }
        overdub = null
        isRecording = true
        isMicMode = useMic
        isEarphoneWhenRecording = if (useMic) audioEngine.isEarphonePlugged() else false
        listener?.onMuteControl(false)
        if (useMic) {
            audioEngine.startMicRecording {
                if (!isRecording) return@startMicRecording
                isRecording = false
                musicTracker.cancel()
                timerHandler.removeCallbacks(timerRunnable)
                blinkManager.resetRecordBtn()
                listener?.onMuteControl(false)
                listener?.onRecordStatusChanged(false)
                setToast("Recording failed")
            }
        }
        recorderManager.startRecording()
        // Mic lewat speaker sudah menangkap suara lagu → jangan dicatat (kalau diputar lagi jadi dobel)
        if (!useMic || isEarphoneWhenRecording) musicTracker.start(recorderManager.recordingStartElapsed)
        listener?.onRecordStatusChanged(true)
        blinkManager.startRecordBlink()
        // Mulai timer tick
        recordingStartTime = System.currentTimeMillis()
        recordingStartedAtWall = recordingStartTime
        draftTick = 0
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

        if (overdub != null) stopOverdubPlayback()
        val rawEvents = recorderManager.stopRecording()
        // Mode mic: samakan 0 ms ketukan dengan awal file audio
        val events = if (isMicMode) recorderManager.alignToMicStart(rawEvents, audioEngine.micStartedAtElapsed) else rawEvents
        val music = alignedMusic(musicTracker.stop())
        val pending = buildPending(events, music)
        overdub = null
        // Rekam lapis tanpa ketukan baru = tidak ada yang ditambahkan → sama dengan batal
        if (events.isNotEmpty()) {
            pendingRecording = pending
            // Draft final: kalau app mati saat dialog simpan masih tampil, rekaman tetap bisa dipulihkan
            writeDraftOf(pending)
            activeDialog = InstrumentDialogHelper.showSaveRecordDialog(
                context = context,
                onSave = { name ->
                    // User pilih SAVE
                    pendingRecording = null
                    onRecordingStopped?.invoke(true)
                    savePending(pending, name)
                },
                onCancel = {
                    // User pilih CANCEL / discard
                    pendingRecording = null
                    RecordingAutosave.clearDraft(context)
                    // File mic rekaman yang dibuang tidak dirujuk database mana pun → hapus agar tidak menumpuk.
                    // Rekam lapis: audio milik rekaman dasar → JANGAN dihapus.
                    if (!pending.audioShared) RecordingAutosave.discardAudio(context, pending.audioPath)
                    onRecordingStopped?.invoke(false)
                }
            ).apply {
                setOnDismissListener {
                    // Ditutup tanpa Save/Batal (Back, tap di luar, panel dilepas) → simpan otomatis, jangan hilang
                    if (pendingRecording === pending) {
                        pendingRecording = null
                        onRecordingStopped?.invoke(true)
                        savePending(pending, null)
                    }
                }
            }
        } else {
            // Tidak ada event sama sekali → langsung anggap cancel
            RecordingAutosave.clearDraft(context)
            if (isMicMode) RecordingAutosave.discardAudio(context, audioEngine.currentAudioFile?.absolutePath)
            onRecordingStopped?.invoke(false)
        }
    }

    // ─── AUTOSAVE ───
    /** Rekaman yang sudah di-stop tapi belum dipilih Save/Batal. */
    private class PendingRecording(
        val events: List<RecordedTap>,
        val audioPath: String?,
        val isEarphoneRecording: Boolean,
        val startedAtWall: Long,
        val music: List<RecordedMusicSegment>,
        /** true = audioPath milik rekaman dasar (rekam lapis) → disalin saat disimpan, tidak pernah dihapus. */
        val audioShared: Boolean = false,
        /** Nama jika user tidak mengisi (null = nama otomatis). */
        val defaultName: String? = null
    )

    /** Rekaman dasar untuk rekam lapis. */
    private class OverdubSession(
        /** Ketukan dasar yang diputar & ikut disimpan. Kosong jika sudah terekam di audio mic (lewat speaker). */
        val baseTaps: List<RecordedTap>,
        val music: List<RecordedMusicSegment>,
        val audioPath: String?,
        /** Nilai isEarphoneRecording hasil: true = suara pad tidak ada di audio → ketukan diputar saat playback. */
        val padsNotInAudio: Boolean,
        val name: String
    )

    /** Hasil rekaman (biasa atau lapis) dari ketukan baru yang sudah di-align. */
    private fun buildPending(newTaps: List<RecordedTap>, music: List<RecordedMusicSegment>): PendingRecording {
        val od = overdub ?: return PendingRecording(
            events = newTaps,
            audioPath = if (isMicMode) audioEngine.currentAudioFile?.absolutePath else null,
            isEarphoneRecording = isEarphoneWhenRecording,
            startedAtWall = recordingStartedAtWall,
            music = music
        )
        return PendingRecording(
            events = (od.baseTaps + newTaps).sortedBy { it.timestamp },
            audioPath = od.audioPath,
            isEarphoneRecording = od.padsNotInAudio,
            startedAtWall = recordingStartedAtWall,
            music = od.music,
            audioShared = od.audioPath != null,
            defaultName = od.name
        )
    }

    private fun writeDraftOf(p: PendingRecording) {
        RecordingAutosave.writeDraft(
            context, p.startedAtWall, instrumentType, p.audioPath, p.isEarphoneRecording, p.events, p.music,
            p.audioShared, p.defaultName
        )
    }

    // ─── REKAM LAPIS (OVERDUB) ───
    /** Rekaman dasar diputar, ketukan baru direkam di atasnya → disimpan sebagai rekaman BARU (dasar tidak berubah). */
    private fun startOverdub(base: RecordingEntity) {
        if (isRecording) return
        stopPlayback()
        activeDialog = InstrumentDialogHelper.showCountdownDialog(context) { beginOverdub(base) }
    }

    private fun beginOverdub(base: RecordingEntity) {
        panelScope.launch {
            val taps = recorderManager.parseJson(base.eventsJson)
            val music = withContext(Dispatchers.Default) { RecordedMusicJson.parse(base.musicJson) }
            val audio = base.audioPath?.takeIf { path ->
                withContext(Dispatchers.IO) { File(path).let { it.exists() && it.length() > 0 } }
            }
            // Mic lewat speaker: suara pad sudah ada di audio → tidak diputar ulang (pad user tidak perlu di-mute)
            val padsInAudio = audio != null && !base.isEarphoneRecording
            val session = OverdubSession(
                baseTaps = if (padsInAudio) emptyList() else taps,
                music = music,
                audioPath = audio,
                padsNotInAudio = if (padsInAudio) true else base.isEarphoneRecording,
                name = context.getString(R.string.record_overdub_name, base.name)
            )
            overdub = session

            val startWithAudio = {
                if (audio != null) {
                    audioEngine.startPlayingAudioSync(audio, {
                        // Audio gagal sebelum mulai → rekam lapis dibatalkan
                        if (!isRecording && overdub === session) {
                            overdub = null
                            musicPlayer.stop()
                            setToast("Recording failed")
                        }
                    }) { beginOverdubLayer(session) }
                } else beginOverdubLayer(session)
            }
            if (music.isEmpty()) startWithAudio() else {
                if (MusicPlayerManager.isPlaying) MusicPlayerManager.pause()
                musicPlayer.prepare(music) { startWithAudio() }
            }
        }
    }

    /** Dipanggil tepat saat rekaman dasar mulai berbunyi → jam rekam & putar ulang dimulai bersamaan. */
    private fun beginOverdubLayer(session: OverdubSession) {
        if (overdub !== session || isRecording) {
            // Dibatalkan / diganti rekaman biasa selama persiapan → hentikan audio & lagu dasar
            if (overdub == null || overdub === session) {
                audioEngine.stopPlayingAudio()
                musicPlayer.stop()
                if (overdub === session) overdub = null
            }
            return
        }
        isRecording = true
        isMicMode = false
        isEarphoneWhenRecording = false
        listener?.onMuteControl(false)
        recorderManager.startRecording()
        if (session.music.isNotEmpty()) musicPlayer.start {}
        if (session.baseTaps.isNotEmpty()) recorderManager.play(session.baseTaps) { post { invalidate() } }
        listener?.onRecordStatusChanged(true)
        blinkManager.startRecordBlink()
        recordingStartTime = System.currentTimeMillis()
        recordingStartedAtWall = recordingStartTime
        draftTick = 0
        timerHandler.post(timerRunnable)
        setToast(context.getString(R.string.record_overdub_hint))
    }

    private fun stopOverdubPlayback() {
        recorderManager.stopPlayback(releaseHeld = true)
        audioEngine.stopPlayingAudio()
        musicPlayer.stop()
    }

    /** Mode mic: potongan lagu digeser sama seperti ketukan agar 0 ms = awal audio mic. */
    private fun alignedMusic(segments: List<RecordedMusicSegment>): List<RecordedMusicSegment> {
        val micStart = audioEngine.micStartedAtElapsed
        if (!isMicMode || micStart <= 0L || segments.isEmpty()) return segments
        return MusicRecordTracker.align(segments, micStart - recorderManager.recordingStartElapsed)
    }

    /** @param name null → nama otomatis. Pakai applicationContext: aman walau panel sudah dilepas. */
    private fun savePending(p: PendingRecording, name: String?) {
        val app = context.applicationContext
        RecordingAutosave.save(
            app, name ?: p.defaultName, p.startedAtWall, instrumentType, p.events, p.audioPath, p.isEarphoneRecording,
            p.music, p.audioShared
        ) { saved, error ->
            val msg = if (saved) app.getString(R.string.record_saved) else "Error saving: $error"
            Toast.makeText(app, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun writeDraftNow() {
        val snapshot = recorderManager.snapshotEvents()
        val events = if (isMicMode) recorderManager.alignToMicStart(snapshot, audioEngine.micStartedAtElapsed) else snapshot
        // Rekam lapis tanpa ketukan baru: jangan buat draft (kalau dipulihkan hanya jadi salinan rekaman dasar)
        if (overdub != null && events.isEmpty()) return
        writeDraftOf(buildPending(events, alignedMusic(musicTracker.snapshot())))
    }

    /** Panel dilepas saat masih merekam: hentikan dengan benar lalu simpan otomatis. */
    private fun autosaveActiveRecording() {
        isRecording = false
        timerHandler.removeCallbacks(timerRunnable)
        if (isMicMode) audioEngine.stopMicRecording()
        if (overdub != null) stopOverdubPlayback()
        val rawEvents = recorderManager.stopRecording()
        val events = if (isMicMode) recorderManager.alignToMicStart(rawEvents, audioEngine.micStartedAtElapsed) else rawEvents
        val music = alignedMusic(musicTracker.stop())
        val pending = buildPending(events, music)
        overdub = null
        if (events.isEmpty()) {
            RecordingAutosave.clearDraft(context)
            if (isMicMode) RecordingAutosave.discardAudio(context, audioEngine.currentAudioFile?.absolutePath)
            return
        }
        savePending(pending, null)
    }

    fun recordEvent(index: Int, metadata: String) {
        if (isRecording && !dispatchingPlayback) recorderManager.onNoteEvent(index, metadata)
    }

    fun setPromotButtonVisible(isVisible: Boolean) {
        showPromotButton = isVisible
    }

    // ─── PLAYBACK ───
    private fun openRecordList() {
        // Export rekaman wajib nonton rewarded ad (tipe "export"), kecuali user premium
        val exportReward: ((onRewarded: () -> Unit) -> Unit)? =
            if (isPremium || adRequestListener == null) null
            else { onRewarded ->
                if (isNetworkAvailable()) adRequestListener?.onShowRewardedAd("export", onRewarded)
                else setToast(context.getString(R.string.no_internet_connection))
            }
        RecordingListDialogHelper.show(
            context, instrumentType, showPromotButton, exportReward,
            onOverdub = { base -> startOverdub(base) }
        ) { entity ->
            if (entity.eventsJson.isNotEmpty()) {
                btnStop.visibility = VISIBLE
                blinkManager.startStopBlink()

                panelScope.launch {
                    val events = recorderManager.parseJson(entity.eventsJson)
                    val music = withContext(Dispatchers.Default) { RecordedMusicJson.parse(entity.musicJson) }
                    val hasAudio = !entity.audioPath.isNullOrEmpty()

                    // Selesai setelah ketukan/audio DAN lagu (jika ada) selesai
                    var remaining = if (music.isEmpty()) 1 else 2
                    var musicStarted = false
                    val onFinish: () -> Unit = {
                        if (--remaining == 0) {
                            post { blinkManager.stopStopBlink(); btnStop.visibility = GONE; listener?.onMuteControl(false); invalidate() }
                        }
                    }
                    val startMusic = {
                        if (music.isNotEmpty()) {
                            musicStarted = true
                            musicPlayer.start(onFinish)
                        }
                    }

                    val startPlayback = {
                        if (hasAudio) {
                            listener?.onMuteControl(!entity.isEarphoneRecording)
                            val onAudioFinish: () -> Unit = {
                                // Audio gagal diputar sebelum mulai → lagu tidak pernah jalan, jangan tunggu
                                if (music.isNotEmpty() && !musicStarted) {
                                    musicPlayer.stop()
                                    remaining = 1
                                }
                                onFinish()
                            }
                            // Ketukan (dan lagu) dimulai saat audio benar-benar mulai, bukan sebelum prepareAsync selesai
                            audioEngine.startPlayingAudioSync(entity.audioPath!!, onAudioFinish) {
                                startMusic()
                                recorderManager.play(events) { post { invalidate() } }
                            }
                        } else {
                            listener?.onMuteControl(false)
                            startMusic()
                            recorderManager.play(events) { onFinish() }
                        }
                    }

                    if (music.isEmpty()) {
                        startPlayback()
                    } else {
                        // Lagu di daftar musik dijeda agar tidak bertabrakan dengan lagu rekaman
                        if (MusicPlayerManager.isPlaying) MusicPlayerManager.pause()
                        // Lagu disiapkan & di-seek dulu, baru semua dimulai bersamaan → sinkron
                        musicPlayer.prepare(music) { startPlayback() }
                    }
                }
            }
        }
    }

    // ─── STOP PLAYBACK ONLY (dipanggil saat tutorial/learn mulai) ───
    fun stopPlayback() {
        recorderManager.stopPlayback(releaseHeld = true)
        audioEngine.stopPlayingAudio()
        musicPlayer.stop()
        blinkManager.stopStopBlink()
        if (::btnStop.isInitialized) btnStop.visibility = GONE
        listener?.onMuteControl(false)
    }

    // ─── LIFECYCLE ───
    fun releaseAndStop() {
        if (isRecording) autosaveActiveRecording()
        // Dialog simpan masih terbuka → simpan otomatis sekarang (OnDismissListener jalan asinkron,
        // jadi jangan diandalkan saat panel dilepas). Callback host tidak dipanggil: view host sedang dihancurkan.
        pendingRecording?.let { pending ->
            pendingRecording = null
            savePending(pending, null)
        }
        isRecording = false
        isMicMode = false
        isEarphoneWhenRecording = false
        musicTracker.cancel()
        musicPlayer.stop()
        overdub = null
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
        panelScopeJob.cancel()
        super.onDetachedFromWindow()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!panelScopeJob.isActive) {
            panelScopeJob = SupervisorJob()
            panelScope = CoroutineScope(panelScopeJob + Dispatchers.Main.immediate)
        }
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

    private fun loadZaifConfigAsync() {
        panelScope.launch(Dispatchers.IO) {
            val loaded = ZaifSDKBuilder.load(context)
            withContext(Dispatchers.Main) {
                if (isAttachedToWindow) {
                    zaifSDKConfig = loaded
                    refreshStatusLabels()
                }
            }
        }
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

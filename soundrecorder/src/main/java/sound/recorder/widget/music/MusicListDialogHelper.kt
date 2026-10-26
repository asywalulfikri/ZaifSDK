package sound.recorder.widget.music

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Environment
import android.os.Looper
import android.provider.MediaStore
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ContextThemeWrapper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*
import com.intuit.sdp.R as SdpR
import com.intuit.ssp.R as SspR
import sound.recorder.widget.R
import sound.recorder.widget.builder.ZaifSDKBuilder
import sound.recorder.widget.builder.ZaifSDKConfig
import java.util.concurrent.TimeUnit

object MusicListDialogHelper {

    private const val PREFS_NAME       = "music_player_prefs"
    private const val KEY_MUSIC_VOLUME = "music_volume"
    private const val DEFAULT_VOLUME   = 0.7f
    var zaifSDKConfig : ZaifSDKConfig? =null

    interface MusicStatusListener {
        fun onMusicPlay(track: MusicPlayerManager.MusicTrack)
        fun onMusicPause(track: MusicPlayerManager.MusicTrack?)
        fun onMusicStop()
        fun onMusicComplete()
        fun onMusicProgress(current: Int, max: Int) {}
    }
    var statusListener: MusicStatusListener? = null

    val isMusicPlaying: Boolean get() = MusicPlayerManager.isPlaying
    val isMusicPaused: Boolean  get() = MusicPlayerManager.isPaused
    val isMusicActive: Boolean  get() = MusicPlayerManager.isPlaying || MusicPlayerManager.isPaused
    val currentTrack: MusicPlayerManager.MusicTrack? get() = MusicPlayerManager.getCurrentTrack()

    private val rawTracks = mutableListOf<MusicPlayerManager.MusicTrack>()

    private var activeDownloadId = -1L
    private var downloadingSongId = ""

    // Firestore Pagination & Caching
    private var lastVisibleDoc: com.google.firebase.firestore.DocumentSnapshot? = null
    private var isLoadingOnline = false
    private var isLastPageOnline = false
    private var lastLoadTime = 0L
    private val onlineCache = mutableListOf<FirestoreSong>()
    private const val CACHE_DURATION = 60 * 60 * 1000L // 1 Jam

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var pollJob: Job? = null

    private val globalListener = object : MusicPlayerManager.PlayerListener {
        override fun onPlay(track: MusicPlayerManager.MusicTrack) {
            Log.d("MusicListDialogHelper", "globalListener: onPlay track=${track.title}")
            statusListener?.onMusicPlay(track)
        }
        override fun onPause() {
            Log.d("MusicListDialogHelper", "globalListener: onPause")
            statusListener?.onMusicPause(currentTrack)
        }
        override fun onStop() {
            Log.d("MusicListDialogHelper", "globalListener: onStop")
            statusListener?.onMusicStop()
        }
        override fun onProgress(current: Int, max: Int) {
            statusListener?.onMusicProgress(current, max)
        }
        override fun onComplete() {
            Log.d("MusicListDialogHelper", "globalListener: onComplete")
            statusListener?.onMusicComplete()
        }
    }

    init {
        MusicPlayerManager.addListener(globalListener)
    }

    private const val COLOR_BG_DARK     = "#0A0E1A"
    private const val COLOR_BG_MEDIUM   = "#1A1F3A"
    private const val COLOR_BG_LIGHT    = "#252B47"
    private const val COLOR_ACCENT      = "#6C63FF"
    private const val COLOR_TEXT_BRIGHT = "#FFFFFF"
    private const val COLOR_TEXT_DIM    = "#8B93B8"
    private const val COLOR_ACCENT_LIGHT = "#9D8CFF"
    private const val COLOR_BG_TOP      = "#161B36"
    private const val COLOR_ROW         = "#131833"
    private const val COLOR_DANGER      = "#FF6B6B"
    private const val COLOR_SUCCESS     = "#4ADE80"

    private fun accentGradient(context: Context, radiusDimen: Int) =
        GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.parseColor(COLOR_ACCENT_LIGHT), Color.parseColor(COLOR_ACCENT))
        ).apply { cornerRadius = context.sdp(radiusDimen).toFloat() }

    private fun roundedBg(context: Context, color: Int, radiusDimen: Int, strokeColor: Int? = null) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = context.sdp(radiusDimen).toFloat()
            strokeColor?.let { setStroke(context.sdp(SdpR.dimen._1sdp), it) }
        }

    private fun ripple(content: android.graphics.drawable.Drawable?, mask: android.graphics.drawable.Drawable? = content) =
        RippleDrawable(ColorStateList.valueOf(Color.parseColor("#26FFFFFF")), content, mask)

    private fun tintedIcon(context: Context, res: Int, color: Int) = ImageView(context).apply {
        setImageResource(res)
        imageTintList = ColorStateList.valueOf(color)
        scaleType = ImageView.ScaleType.FIT_CENTER
    }

    /** Tombol bulat dengan ikon (dipakai di player card). */
    private fun roundIconButton(context: Context, viewId: Int, iconRes: Int, bg: GradientDrawable, iconColor: Int) =
        ImageButton(context).apply {
            id = viewId
            bg.shape = GradientDrawable.OVAL
            background = ripple(bg)
            setImageResource(iconRes)
            setColorFilter(iconColor)
            scaleType = ImageView.ScaleType.FIT_CENTER
            val p = context.sdp(SdpR.dimen._8sdp)
            setPadding(p, p, p, p)
        }

    /** Kartu baris lagu: wadah RecyclerView berisi kartu rounded dengan ripple. */
    private fun rowCard(ctx: Context): Pair<FrameLayout, LinearLayout> {
        val outer = FrameLayout(ctx).apply {
            layoutParams = RecyclerView.LayoutParams(-1, -2)
            setPadding(ctx.sdp(SdpR.dimen._12sdp), ctx.sdp(SdpR.dimen._3sdp), ctx.sdp(SdpR.dimen._12sdp), ctx.sdp(SdpR.dimen._3sdp))
        }
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val p = ctx.sdp(SdpR.dimen._8sdp)
            setPadding(p, p, ctx.sdp(SdpR.dimen._10sdp), p)
        }
        outer.addView(card, FrameLayout.LayoutParams(-1, -2))
        return outer to card
    }

    /** Kotak ikon kiri baris: ikon not musik, atau equalizer saat lagu itu sedang aktif. */
    private fun rowIconTile(ctx: Context): Triple<FrameLayout, ImageView, EqualizerBarsView> {
        val size = ctx.sdp(SdpR.dimen._34sdp)
        val note = tintedIcon(ctx, R.drawable.zaif_rec_ic_music, Color.parseColor(COLOR_ACCENT_LIGHT))
        val eq = EqualizerBarsView(ctx).apply {
            barColor = Color.WHITE
            mutedColor = Color.WHITE
            level = 0.85f
            visibility = View.GONE
        }
        val tile = FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(size, size)
            val icon = ctx.sdp(SdpR.dimen._16sdp)
            addView(note, FrameLayout.LayoutParams(icon, icon, Gravity.CENTER))
            addView(eq, FrameLayout.LayoutParams(ctx.sdp(SdpR.dimen._16sdp), ctx.sdp(SdpR.dimen._14sdp), Gravity.CENTER))
        }
        return Triple(tile, note, eq)
    }

    data class FirestoreSong(
        val id: String = "",
        val title: String = "",
        val link_download: String = "",
        val appId: List<String> = emptyList()
    )

    private fun Context.sdp(id: Int)    = resources.getDimensionPixelSize(id)
    private fun Context.sspSp(id: Int)  = resources.getDimension(id) / resources.displayMetrics.scaledDensity

    @SuppressLint("UseKtx")
    private fun saveMusicVolume(context: Context, volume: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putFloat(KEY_MUSIC_VOLUME, volume).apply()
    }

    private fun loadMusicVolume(context: Context): Float =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_MUSIC_VOLUME, DEFAULT_VOLUME)

    fun registerRawTracks(tracks: List<MusicPlayerManager.MusicTrack>) {
        rawTracks.clear()
        rawTracks.addAll(tracks)
    }

    private fun resolveThemedContext(context: Context): Context {
        if (context is Activity && !context.isFinishing && !context.isDestroyed) {
            return context
        }
        // Pastikan menggunakan theme yang valid untuk mencegah crash nativeApplyStyle
        return ContextThemeWrapper(context, androidx.appcompat.R.style.Theme_AppCompat_DayNight_NoActionBar)
    }

    @SuppressLint("UseKtx", "ClickableViewAccessibility")
    fun show(context: Context, isDownload: Boolean = false): AlertDialog {
        val themedContext = resolveThemedContext(context)

        zaifSDKConfig = ZaifSDKBuilder.load(context)

        val savedVolume = loadMusicVolume(themedContext)
        MusicPlayerManager.setVolume(savedVolume, savedVolume)

        val displayHeight = themedContext.resources.displayMetrics.heightPixels
        val rootContainer = FrameLayout(themedContext).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            minimumHeight = displayHeight
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor(COLOR_BG_TOP), Color.parseColor(COLOR_BG_DARK), Color.parseColor(COLOR_BG_DARK))
            )
        }

        val mainLayout = LinearLayout(themedContext).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(-1, -1)
        }

        // ─── HEADER ───
        val headerContainer = LinearLayout(themedContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                themedContext.sdp(SdpR.dimen._12sdp),
                themedContext.sdp(SdpR.dimen._10sdp),
                themedContext.sdp(SdpR.dimen._12sdp),
                themedContext.sdp(SdpR.dimen._8sdp)
            )
        }

        val headerTileSize = themedContext.sdp(SdpR.dimen._32sdp)
        val headerTile = FrameLayout(themedContext).apply {
            background = accentGradient(themedContext, SdpR.dimen._10sdp)
            elevation = themedContext.sdp(SdpR.dimen._3sdp).toFloat()
            val icon = themedContext.sdp(SdpR.dimen._18sdp)
            addView(
                tintedIcon(themedContext, R.drawable.zaif_rec_ic_queue_music, Color.WHITE),
                FrameLayout.LayoutParams(icon, icon, Gravity.CENTER)
            )
        }

        val titleView = TextView(themedContext).apply {
            text = themedContext.getString(R.string.list_music)
            setTextColor(Color.parseColor(COLOR_TEXT_BRIGHT))
            textSize = themedContext.sspSp(SspR.dimen._15ssp)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(themedContext.sdp(SdpR.dimen._10sdp), 0, themedContext.sdp(SdpR.dimen._8sdp), 0)
        }

        val closeBtnSize = themedContext.sdp(SdpR.dimen._30sdp)
        val closeBtn = FrameLayout(themedContext).apply {
            background = ripple(GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(COLOR_BG_MEDIUM))
            })
            contentDescription = themedContext.getString(R.string.close)
            val icon = themedContext.sdp(SdpR.dimen._14sdp)
            addView(
                tintedIcon(themedContext, R.drawable.zaif_rec_ic_close, Color.parseColor(COLOR_TEXT_DIM)),
                FrameLayout.LayoutParams(icon, icon, Gravity.CENTER)
            )
        }

        headerContainer.addView(headerTile, LinearLayout.LayoutParams(headerTileSize, headerTileSize))
        headerContainer.addView(titleView, LinearLayout.LayoutParams(0, -2, 1f))
        headerContainer.addView(closeBtn, LinearLayout.LayoutParams(closeBtnSize, closeBtnSize))
        mainLayout.addView(headerContainer)

        // ─── TAB STRIP (hanya jika isDownload = true) ───
        var tabLocal: TextView? = null
        var tabOnline: TextView? = null
        if (isDownload) {
            fun makeTabButton(label: String): TextView = TextView(themedContext).apply {
                text = label
                textSize = themedContext.sspSp(SspR.dimen._10ssp)
                gravity = Gravity.CENTER
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, themedContext.sdp(SdpR.dimen._28sdp), 1f)
            }
            tabLocal  = makeTabButton(context.getString(R.string.local_song))
            tabOnline = makeTabButton(context.getString(R.string.online_song))
            // Segmented control: satu pill gelap, tab aktif diisi gradien aksen
            val tabStrip = LinearLayout(themedContext).apply {
                orientation = LinearLayout.HORIZONTAL
                val p = themedContext.sdp(SdpR.dimen._3sdp)
                setPadding(p, p, p, p)
                background = roundedBg(themedContext, Color.parseColor(COLOR_BG_MEDIUM), SdpR.dimen._20sdp)
            }
            tabStrip.addView(tabLocal)
            tabStrip.addView(tabOnline)
            mainLayout.addView(tabStrip, LinearLayout.LayoutParams(-1, -2).apply {
                setMargins(
                    themedContext.sdp(SdpR.dimen._12sdp), 0,
                    themedContext.sdp(SdpR.dimen._12sdp), themedContext.sdp(SdpR.dimen._8sdp)
                )
            })
        }

        // ─── SEARCH BAR ───
        val searchContainer = FrameLayout(themedContext).apply {
            setPadding(
                themedContext.sdp(SdpR.dimen._12sdp),
                0,
                themedContext.sdp(SdpR.dimen._12sdp),
                themedContext.sdp(SdpR.dimen._6sdp)
            )
        }

        val searchField = EditText(themedContext).apply {
            hint = themedContext.getString(R.string.search_song_accompaniment)
            setHintTextColor(Color.parseColor(COLOR_TEXT_DIM))
            setTextColor(Color.WHITE)
            textSize = themedContext.sspSp(SspR.dimen._10ssp)
            maxLines = 1
            isSingleLine = true
            setPadding(
                themedContext.sdp(SdpR.dimen._12sdp),
                themedContext.sdp(SdpR.dimen._8sdp),
                themedContext.sdp(SdpR.dimen._12sdp),
                themedContext.sdp(SdpR.dimen._8sdp)
            )
            ContextCompat.getDrawable(themedContext, R.drawable.zaif_rec_ic_search)?.mutate()?.let { d ->
                val s = themedContext.sdp(SdpR.dimen._14sdp)
                d.setBounds(0, 0, s, s)
                d.setTint(Color.parseColor(COLOR_TEXT_DIM))
                setCompoundDrawablesRelative(d, null, null, null)
                compoundDrawablePadding = themedContext.sdp(SdpR.dimen._8sdp)
            }
            background = roundedBg(
                themedContext, Color.parseColor(COLOR_BG_MEDIUM), SdpR.dimen._20sdp, Color.parseColor(COLOR_BG_LIGHT)
            )
        }
        searchContainer.addView(searchField)
        mainLayout.addView(searchContainer)

        // ─── LOCAL LIST ───
        val localRecyclerView = RecyclerView(themedContext).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
            layoutManager = LinearLayoutManager(themedContext)
            isVerticalScrollBarEnabled = false
            clipToPadding = false
        }
        mainLayout.addView(localRecyclerView)

        // ─── ONLINE LIST (hanya jika isDownload = true) ───
        var onlineRecyclerView: RecyclerView? = null
        var onlineLoadingLayout: LinearLayout? = null
        var onlineLoadingText: TextView? = null
        if (isDownload) {
            onlineRecyclerView = RecyclerView(themedContext).apply {
                layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
                layoutManager = LinearLayoutManager(themedContext)
                isVerticalScrollBarEnabled = false
                visibility = View.GONE
                clipToPadding = false
            }

            onlineLoadingLayout = LinearLayout(themedContext).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
                visibility = View.GONE

                addView(ProgressBar(themedContext).apply {
                    indeterminateTintList = ColorStateList.valueOf(Color.parseColor(COLOR_ACCENT))
                    layoutParams = LinearLayout.LayoutParams(
                        themedContext.sdp(SdpR.dimen._28sdp),
                        themedContext.sdp(SdpR.dimen._28sdp)
                    )
                })

                onlineLoadingText = TextView(themedContext).apply {
                    text = context.getString(R.string.loading_audio)
                    setTextColor(Color.parseColor(COLOR_TEXT_DIM))
                    textSize = themedContext.sspSp(SspR.dimen._10ssp)
                    setPadding(0, themedContext.sdp(SdpR.dimen._8sdp), 0, 0)
                }
                addView(onlineLoadingText)
            }
            mainLayout.addView(onlineRecyclerView)
            mainLayout.addView(onlineLoadingLayout)
        }

        rootContainer.addView(mainLayout)

        // ─── FLOATING PLAYER CARD ───
        val playerCard = buildElegantPlayerCard(themedContext, savedVolume)
        val playerMargin = themedContext.sdp(SdpR.dimen._12sdp)
        val playerBottom = themedContext.sdp(SdpR.dimen._8sdp)
        rootContainer.addView(playerCard, FrameLayout.LayoutParams(-1, -2).apply {
            gravity = Gravity.BOTTOM
            setMargins(playerMargin, 0, playerMargin, playerBottom)
        })

        val uiListener = object : MusicPlayerManager.PlayerListener {
            override fun onPlay(track: MusicPlayerManager.MusicTrack) {
                val vol = loadMusicVolume(themedContext)
                MusicPlayerManager.setVolume(vol, vol)
                playerCard.visibility = View.VISIBLE
                playerCard.findViewById<TextView>(101)?.text = track.title
                playerCard.findViewById<ImageButton>(102)
                    ?.setImageResource(R.drawable.zaif_rec_ic_pause)
                playerCard.findViewById<EqualizerBarsView>(106)?.continuous = true
                // Refresh tampilan: equalizer di baris lagu aktif ikut bergerak
                localRecyclerView.adapter?.notifyDataSetChanged()
            }
            override fun onPause() {
                playerCard.findViewById<ImageButton>(102)
                    ?.setImageResource(R.drawable.zaif_rec_ic_play)
                playerCard.findViewById<EqualizerBarsView>(106)?.continuous = false
                localRecyclerView.adapter?.notifyDataSetChanged()
            }
            override fun onStop() {
                if (MusicPlayerManager.getCurrentTrack() == null) playerCard.visibility = View.GONE
                localRecyclerView.adapter?.notifyDataSetChanged()
                onlineRecyclerView?.adapter?.notifyDataSetChanged()
            }
            override fun onProgress(current: Int, max: Int) {
                playerCard.findViewById<MusicSeekBar>(103)?.apply {
                    this.max = max
                    this.progress = current
                }
            }
            override fun onComplete() {
                playerCard.visibility = View.GONE
                localRecyclerView.adapter?.notifyDataSetChanged()
                onlineRecyclerView?.adapter?.notifyDataSetChanged()
            }
        }
        MusicPlayerManager.addListener(uiListener)

        val dialog = AlertDialog.Builder(themedContext, R.style.FullScreenDialogTheme)
            .setView(rootContainer)
            .create()

        closeBtn.setOnClickListener { dialog.dismiss() }

        ViewCompat.setOnApplyWindowInsetsListener(rootContainer) { view, insets ->
            // Bar tersembunyi → inset-nya 0; tetap jaga konten dari notch / poni kamera
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // ─── LOCAL DATA & ADAPTER ───
        val allTracks = mutableListOf<MusicPlayerManager.MusicTrack>()
        val localAdapter = MusicAdapter { track ->
            MusicPlayerManager.play(themedContext, track)
            localRecyclerView.adapter?.notifyDataSetChanged()
        }
        localRecyclerView.adapter = localAdapter

        fun renderLocalList(query: String) {
            val filtered = allTracks.filter { it.title.orEmpty().contains(query, true) }
            localAdapter.updateData(filtered)
        }

        fun setToast(message : String){
            Toast.makeText(context, "$message.", Toast.LENGTH_SHORT).show()
        }

        var onlineAdapter: OnlineMusicAdapter? = null

        fun refreshLocalTracks() {
            appScope.launch {
                val deviceTracks = withContext(Dispatchers.IO) {
                    loadDeviceTracks(themedContext)
                }
                allTracks.clear()
                allTracks.addAll(rawTracks + deviceTracks)
                withContext(Dispatchers.Main) {
                    renderLocalList(searchField.text?.toString().orEmpty())
                    val titles = allTracks.map { it.title.trim().lowercase() }.toSet()
                    onlineAdapter?.setDownloadedTitles(titles)
                }
            }
        }

        var isOnlineTab = false
        var renderOnlineListFn: ((String) -> Unit)? = null

        // ─── ONLINE DATA & ADAPTER (hanya jika isDownload = true) ───
        if (isDownload) {
            val dm = themedContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

            fun startPoll(position: Int, downloadId: Long, songTitle: String) {
                pollJob?.cancel()
                pollJob = appScope.launch {
                    val startTime = System.currentTimeMillis()
                    while (isActive) {
                        val result = withContext(Dispatchers.IO) {
                            try {
                                dm.query(DownloadManager.Query().setFilterById(downloadId))?.use { c ->
                                    if (c.moveToFirst()) {
                                        val statusIdx = c.getColumnIndex(DownloadManager.COLUMN_STATUS)
                                        val bytesIdx = c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                                        val totalIdx = c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                                        val reasonIdx = c.getColumnIndex(DownloadManager.COLUMN_REASON)

                                        val status = if (statusIdx >= 0) c.getInt(statusIdx) else -1
                                        val bytes = if (bytesIdx >= 0) c.getLong(bytesIdx) else 0L
                                        val total = if (totalIdx >= 0) c.getLong(totalIdx) else 0L
                                        val reason = if (reasonIdx >= 0) c.getInt(reasonIdx) else -1
                                        return@withContext Triple(true, status, Triple(bytes, total, reason))
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("MusicListDialogHelper", "Download query crash: ${e.message}")
                                // Fallback for 'local_filename' issue on some Android versions
                                if (e is IllegalArgumentException && e.message?.contains("local_filename") == true) {
                                    try {
                                        val projection = arrayOf(
                                            DownloadManager.COLUMN_STATUS,
                                            DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR,
                                            DownloadManager.COLUMN_TOTAL_SIZE_BYTES,
                                            DownloadManager.COLUMN_REASON
                                        )
                                        themedContext.contentResolver.query(
                                            Uri.parse("content://downloads/my_downloads"),
                                            projection,
                                            "_id = ?",
                                            arrayOf(downloadId.toString()),
                                            null
                                        )?.use { c ->
                                            if (c.moveToFirst()) {
                                                return@withContext Triple(true, c.getInt(0), Triple(c.getLong(1), c.getLong(2), c.getInt(3)))
                                            }
                                        }
                                    } catch (e2: Exception) {
                                        Log.e("MusicListDialogHelper", "Fallback query failed: ${e2.message}")
                                    }
                                }
                            }
                            Triple(false, -1, Triple(0L, 0L, -1))
                        }

                        val (hasData, status, data) = result
                        val (bytes, total, reason) = data

                        if (!hasData) {
                            delay(1000L)
                            continue
                        }

                        // Timeout 30 detik jika tidak ada progress
                        if (System.currentTimeMillis() - startTime > 30000 && bytes <= 0) {
                            withContext(Dispatchers.Main) {
                                try { dm.remove(downloadId) } catch (_: Exception) {}
                                activeDownloadId = -1L
                                downloadingSongId = ""
                                onlineAdapter?.clearDownloadState(position)
                                Toast.makeText(themedContext, themedContext.getString(R.string.download_failed), Toast.LENGTH_SHORT).show()
                            }
                            return@launch
                        }

                        val progress = if (total > 0) ((bytes * 100) / total).toInt() else 0
                        
                        withContext(Dispatchers.Main) {
                            onlineAdapter?.setDownloadState(position, progress)
                            if (status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED) {
                                activeDownloadId = -1L
                                downloadingSongId = ""
                                onlineAdapter?.clearDownloadState(position)
                                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                    onlineAdapter?.addDownloadedTitle(songTitle)
                                    refreshLocalTracks()
                                } else {
                                    val errorMsg = when (reason) {
                                        DownloadManager.ERROR_CANNOT_RESUME -> "Cannot resume"
                                        DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Storage not found"
                                        DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "File already exists"
                                        DownloadManager.ERROR_FILE_ERROR -> "Storage error"
                                        DownloadManager.ERROR_HTTP_DATA_ERROR -> "Network data error"
                                        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Storage full"
                                        DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "Too many redirects"
                                        DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "HTTP Error $reason"
                                        else -> "Error code: $reason"
                                    }
                                    Toast.makeText(themedContext, "${themedContext.getString(R.string.download_failed)}: $errorMsg", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }

                        if (status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED) return@launch
                        delay(500L)
                    }
                }
            }

            fun renderOnlineList(query: String) {
                val filtered = onlineCache.filter { it.title.contains(query, true) }
                onlineAdapter?.updateData(filtered)
            }
            renderOnlineListFn = ::renderOnlineList

            onlineAdapter = OnlineMusicAdapter(
                onDownloadClick = { song, position ->
                    if (!isInternetAvailable(themedContext)) {
                        Toast.makeText(themedContext, context.getString(R.string.no_internet_connection), Toast.LENGTH_SHORT).show()
                        return@OnlineMusicAdapter
                    }
                    if (activeDownloadId != -1L) {
                        Toast.makeText(themedContext, context.getString(R.string.finish_download_first), Toast.LENGTH_SHORT).show()
                        return@OnlineMusicAdapter
                    }
                    val id = downloadSong(themedContext, song)
                    if (id == -1L) return@OnlineMusicAdapter
                    activeDownloadId = id
                    downloadingSongId = song.id
                    startPoll(position, id, song.title)
                },
                onCancelClick = { position ->
                    if (activeDownloadId != -1L) {
                        try { dm.remove(activeDownloadId) } catch (_: Exception) {}
                        pollJob?.cancel()
                        activeDownloadId = -1L
                        downloadingSongId = ""
                        onlineAdapter?.clearDownloadState(position)
                        Toast.makeText(themedContext, context.getString(R.string.cancel), Toast.LENGTH_SHORT).show()
                    }
                },
                onPlayClick = { title ->
                    val track = allTracks.firstOrNull {
                        it.title.trim().lowercase() == title.trim().lowercase()
                    }
                    if (track != null) {
                        MusicPlayerManager.play(themedContext, track)
                        onlineAdapter?.notifyDataSetChanged()
                    }
                }
            )
            onlineRecyclerView?.adapter = onlineAdapter

            fun styleTab(tab: TextView?, selected: Boolean) {
                tab ?: return
                tab.setTextColor(if (selected) Color.WHITE else Color.parseColor(COLOR_TEXT_DIM))
                tab.background = if (selected) accentGradient(themedContext, SdpR.dimen._20sdp).apply {
                    orientation = GradientDrawable.Orientation.LEFT_RIGHT
                } else null
                tab.elevation = if (selected) themedContext.sdp(SdpR.dimen._2sdp).toFloat() else 0f
            }

            fun activateTabLocal() {
                isOnlineTab = false
                styleTab(tabLocal, true)
                styleTab(tabOnline, false)
                localRecyclerView.visibility = View.VISIBLE
                onlineRecyclerView?.visibility = View.GONE
                onlineLoadingLayout?.visibility = View.GONE
            }

            fun loadFirestoreIfNeeded(isLoadMore: Boolean = false) {
                val now = System.currentTimeMillis()
                val isCacheValid = (now - lastLoadTime) < CACHE_DURATION

                if (!isLoadMore && isCacheValid && onlineCache.isNotEmpty()) {
                    onlineRecyclerView?.visibility = View.VISIBLE
                    onlineLoadingLayout?.visibility = View.GONE
                    renderOnlineList(searchField.text?.toString().orEmpty())
                    if (allTracks.isNotEmpty()) {
                        val titles = allTracks.map { it.title.trim().lowercase() }.toSet()
                        onlineAdapter?.setDownloadedTitles(titles)
                    }
                    return
                }

                if (isLoadingOnline || (isLastPageOnline && isLoadMore)) return

                // Offline fallback: if no internet but cache exists, use it
                if (!isInternetAvailable(themedContext) && onlineCache.isNotEmpty()) {
                    onlineRecyclerView?.visibility = View.VISIBLE
                    onlineLoadingLayout?.visibility = View.GONE
                    renderOnlineList(searchField.text?.toString().orEmpty())
                    if (allTracks.isNotEmpty()) {
                        val titles = allTracks.map { it.title.trim().lowercase() }.toSet()
                        onlineAdapter?.setDownloadedTitles(titles)
                    }
                    return
                }

                isLoadingOnline = true

                if (!isLoadMore) {
                    onlineLoadingText?.text = context.getString(R.string.loading_audio)
                    onlineLoadingLayout?.visibility = View.VISIBLE
                    onlineLoadingLayout?.getChildAt(0)?.visibility = View.VISIBLE
                    onlineRecyclerView?.visibility = View.GONE
                }

                // Safety check for Firebase initialization
                if (FirebaseApp.getApps(context).isEmpty()) {
                    try {
                        FirebaseApp.initializeApp(context)
                    } catch (e: Exception) {
                        isLoadingOnline = false
                        onlineLoadingLayout?.visibility = View.GONE
                        Toast.makeText(context, "Firebase not initialized", Toast.LENGTH_SHORT).show()
                        return
                    }

                    // Check again after attempt
                    if (FirebaseApp.getApps(context).isEmpty()) {
                        isLoadingOnline = false
                        onlineLoadingLayout?.visibility = View.GONE
                        return
                    }
                }

                var collection = "song_" + zaifSDKConfig?.applicationId
                if (zaifSDKConfig?.applicationId == "gendang.elektronik.beat"||zaifSDKConfig?.applicationId == "kendang.koplo.didikempot") {
                    collection = "song"
                }

                var query = FirebaseFirestore.getInstance().collection(collection)
                    .whereArrayContains("appId", zaifSDKConfig?.applicationId ?: "")
                    .limit(100)

                lastVisibleDoc?.let {
                    query = query.startAfter(it)
                }

                query.get()
                    .addOnSuccessListener { snapshot ->
                        isLoadingOnline = false
                        if (snapshot.isEmpty) {
                            isLastPageOnline = true
                            if (!isLoadMore) {
                                if (onlineCache.isEmpty()) {
                                    onlineLoadingLayout?.visibility = View.VISIBLE
                                    onlineLoadingLayout?.getChildAt(0)?.visibility = View.GONE
                                    onlineLoadingText?.text = context.getString(R.string.data_empty)
                                } else {
                                    onlineLoadingLayout?.visibility = View.GONE
                                    onlineRecyclerView?.visibility = View.VISIBLE
                                }
                            }
                            return@addOnSuccessListener
                        }

                        if (!isLoadMore) {
                            onlineCache.clear()
                            lastLoadTime = System.currentTimeMillis()
                            isLastPageOnline = false
                            lastVisibleDoc = null
                        }

                        val newSongs = snapshot.documents.mapNotNull { doc ->
                            FirestoreSong(
                                id = doc.id,
                                title = doc.getString("title") ?: "",
                                link_download = doc.getString("link_download") ?: "",
                                appId = doc.get("appId") as? List<String> ?: emptyList()
                            )
                        }
                        onlineCache.addAll(newSongs)
                        lastVisibleDoc = snapshot.documents[snapshot.size() - 1]

                        if (snapshot.size() < 100) {
                            isLastPageOnline = true
                        }

                        onlineLoadingLayout?.visibility = View.GONE
                        onlineRecyclerView?.visibility = View.VISIBLE
                        renderOnlineList(searchField.text?.toString().orEmpty())

                        if (allTracks.isNotEmpty()) {
                            val titles = allTracks.map { it.title.trim().lowercase() }.toSet()
                            onlineAdapter?.setDownloadedTitles(titles)
                        }

                        if (activeDownloadId != -1L) {
                            val pos = onlineCache.indexOfFirst { it.id == downloadingSongId }
                            if (pos >= 0) {
                                startPoll(pos, activeDownloadId, onlineCache[pos].title)
                            } else {
                                activeDownloadId = -1L
                                downloadingSongId = ""
                            }
                        }
                    }
                    .addOnFailureListener {
                        isLoadingOnline = false
                        onlineLoadingText?.text = context.getString(R.string.try_again)
                        onlineLoadingLayout?.visibility = View.VISIBLE
                        onlineLoadingLayout?.getChildAt(0)?.visibility = View.GONE
                        onlineRecyclerView?.visibility = View.GONE
                    }
            }

            onlineRecyclerView?.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                    val visibleItemCount = layoutManager.childCount
                    val totalItemCount = layoutManager.itemCount
                    val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                    if (!isLoadingOnline && !isLastPageOnline) {
                        if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount
                            && firstVisibleItemPosition >= 0
                            && totalItemCount >= 100
                        ) {
                            loadFirestoreIfNeeded(true)
                        }
                    }
                }
            })

            fun activateTabOnline() {
                isOnlineTab = true
                styleTab(tabOnline, true)
                styleTab(tabLocal, false)
                localRecyclerView.visibility = View.GONE
                loadFirestoreIfNeeded()
            }

            tabLocal?.setOnClickListener { activateTabLocal() }
            tabOnline?.setOnClickListener { activateTabOnline() }
            activateTabLocal()

            dialog.setOnDismissListener {
                pollJob?.cancel()
                MusicPlayerManager.removeListener(uiListener)
                appScope.coroutineContext.cancelChildren()
            }
        } else {
            dialog.setOnDismissListener {
                MusicPlayerManager.removeListener(uiListener)
                appScope.coroutineContext.cancelChildren()
            }
        }

        searchField.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString().orEmpty()
                if (isOnlineTab) renderOnlineListFn?.invoke(q) else renderLocalList(q)
            }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })

        if (isMusicActive) {
            playerCard.visibility = View.VISIBLE
            updatePlayerUI(playerCard, themedContext)
        } else {
            playerCard.visibility = View.GONE
        }

        dialog.show()
        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            decorView.setPadding(0, 0, 0, 0)

            // Immersive: sembunyikan navigation bar (tombol back/home) & status bar;
            // swipe dari tepi layar memunculkannya sementara. Tombol back fisik/gesture tetap menutup dialog.
            WindowCompat.setDecorFitsSystemWindows(this, false)
            WindowCompat.getInsetsController(this, decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }

        playerCard.viewTreeObserver.addOnGlobalLayoutListener {
            val bottomPad = if (playerCard.visibility == View.VISIBLE) {
                playerCard.height + playerBottom
            } else {
                0
            }
            localRecyclerView.setPadding(0, 0, 0, bottomPad)
            onlineRecyclerView?.setPadding(0, 0, 0, bottomPad)
        }

        rootContainer.post {
            refreshLocalTracks()
        }

        return dialog
    }

    @Suppress("DEPRECATION")
    private fun isInternetAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return false
            val caps    = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            cm.activeNetworkInfo?.isConnected == true
        }
    }

    private fun downloadSong(context: Context, song: FirestoreSong): Long {
        if (song.link_download.isBlank()) {
            Toast.makeText(context, context.getString(R.string.failed_get_data), Toast.LENGTH_SHORT).show()
            return -1L
        }
        return try {
            var downloadUrl = song.link_download.trim()

            // Konversi link Google Drive "view/share" ke link "direct download"
            if (downloadUrl.contains("drive.google.com")) {
                val fileId = when {
                    downloadUrl.contains("/file/d/") -> downloadUrl.substringAfter("/file/d/").substringBefore("/")
                    downloadUrl.contains("id=") -> downloadUrl.substringAfter("id=").substringBefore("&")
                    else -> null
                }
                if (fileId != null) {
                    downloadUrl = "https://drive.google.com/uc?export=download&id=$fileId"
                }
            }

            // Sanitasi nama file dari karakter ilegal
            val sanitizedTitle = song.title.ifBlank { song.id }
                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val fileName = "$sanitizedTitle.mp3"

            val request = DownloadManager.Request(Uri.parse(downloadUrl))
                .setTitle(song.title)
                .setDescription(context.getString(R.string.process_download))
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, fileName)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
                .setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
                .addRequestHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36")
                .addRequestHeader("Accept", "audio/*, */*")
                .addRequestHeader("Referer", downloadUrl)
                .addRequestHeader("Connection", "keep-alive")

            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
        } catch (e: Exception) {
            Toast.makeText(context, "Gagal mengunduh: ${e.message}", Toast.LENGTH_SHORT).show()
            -1L
        }
    }

    // ─── LOCAL MUSIC ADAPTER ───────────────────────────────────────────────────

    private class MusicAdapter(
        private val onTrackClick: (MusicPlayerManager.MusicTrack) -> Unit
    ) : RecyclerView.Adapter<MusicAdapter.VH>() {

        private var items = listOf<MusicPlayerManager.MusicTrack>()

        fun updateData(newItems: List<MusicPlayerManager.MusicTrack>) {
            items = newItems
            notifyDataSetChanged()
        }

        class VH(
            view: View,
            val card: LinearLayout,
            val iconBox: FrameLayout,
            val noteIcon: ImageView,
            val eq: EqualizerBarsView,
            val titleTv: TextView,
            val metaTv: TextView,
            val playIcon: ImageView
        ) : RecyclerView.ViewHolder(view)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val ctx = parent.context
            val (outer, card) = rowCard(ctx)
            val (iconBox, noteIcon, eq) = rowIconTile(ctx)

            val textStack = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(ctx.sdp(SdpR.dimen._10sdp), 0, ctx.sdp(SdpR.dimen._8sdp), 0)
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            }
            val titleTv = TextView(ctx).apply {
                textSize = ctx.sspSp(SspR.dimen._11ssp)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            val metaTv = TextView(ctx).apply {
                textSize = ctx.sspSp(SspR.dimen._8ssp)
                setTextColor(Color.parseColor(COLOR_TEXT_DIM))
                letterSpacing = 0.04f
                setPadding(0, ctx.sdp(SdpR.dimen._1sdp), 0, 0)
            }
            textStack.addView(titleTv)
            textStack.addView(metaTv)

            val playSize = ctx.sdp(SdpR.dimen._26sdp)
            val playIcon = tintedIcon(ctx, R.drawable.zaif_rec_ic_play, Color.WHITE).apply {
                val p = ctx.sdp(SdpR.dimen._6sdp)
                setPadding(p, p, p, p)
                layoutParams = LinearLayout.LayoutParams(playSize, playSize)
            }

            card.addView(iconBox)
            card.addView(textStack)
            card.addView(playIcon)

            return VH(outer, card, iconBox, noteIcon, eq, titleTv, metaTv, playIcon)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val track = items[position]
            val ctx = holder.itemView.context
            val isPlaying = MusicPlayerManager.getCurrentTrack()?.title == track.title

            holder.card.background = ripple(
                if (isPlaying) roundedBg(ctx, Color.parseColor("#2A6C63FF"), SdpR.dimen._12sdp, Color.parseColor("#806C63FF"))
                else roundedBg(ctx, Color.parseColor(COLOR_ROW), SdpR.dimen._12sdp)
            )
            holder.iconBox.background =
                if (isPlaying) accentGradient(ctx, SdpR.dimen._10sdp)
                else roundedBg(ctx, Color.parseColor(COLOR_BG_LIGHT), SdpR.dimen._10sdp)
            holder.noteIcon.visibility = if (isPlaying) View.GONE else View.VISIBLE
            holder.eq.visibility = if (isPlaying) View.VISIBLE else View.GONE
            holder.eq.continuous = isPlaying && MusicPlayerManager.isPlaying

            holder.titleTv.apply {
                text = track.title
                setTextColor(
                    if (isPlaying) Color.parseColor(COLOR_ACCENT_LIGHT)
                    else Color.parseColor(COLOR_TEXT_BRIGHT)
                )
                typeface = Typeface.create(
                    "sans-serif-medium",
                    if (isPlaying) Typeface.BOLD else Typeface.NORMAL
                )
            }

            val source = holder.metaTv.context.getString(
                if (track.isRaw) R.string.music_source_asset else R.string.music_source_storage
            )
            holder.metaTv.text = "$source • ${formatMs(track.duration)}"

            // Lagu aktif sudah ditandai equalizer; lainnya tampilkan tombol play bulat
            holder.playIcon.visibility = if (isPlaying) View.INVISIBLE else View.VISIBLE
            holder.playIcon.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(COLOR_BG_LIGHT))
            }
            holder.playIcon.imageTintList = ColorStateList.valueOf(Color.parseColor(COLOR_ACCENT_LIGHT))

            holder.card.setOnClickListener { onTrackClick(track) }
        }

        override fun getItemCount() = items.size

        private fun Context.sdp(id: Int) = resources.getDimensionPixelSize(id)
        private fun Context.sspSp(id: Int) = resources.getDimension(id) / resources.displayMetrics.scaledDensity
    }

    // ─── ONLINE MUSIC ADAPTER ──────────────────────────────────────────────────

    private class OnlineMusicAdapter(
        private val onDownloadClick: (FirestoreSong, Int) -> Unit,
        private val onCancelClick: (Int) -> Unit,
        private val onPlayClick: (String) -> Unit
    ) : RecyclerView.Adapter<OnlineMusicAdapter.VH>() {

        private var items = listOf<FirestoreSong>()
        private var downloadingPosition = -1
        private var downloadProgress = 0
        private var downloadedTitles = mutableSetOf<String>()

        fun updateData(newItems: List<FirestoreSong>) {
            items = newItems
            notifyDataSetChanged()
        }

        fun setDownloadedTitles(titles: Set<String>) {
            downloadedTitles = titles.toMutableSet()
            notifyDataSetChanged()
        }

        fun addDownloadedTitle(title: String) {
            downloadedTitles.add(title.trim().lowercase())
            notifyDataSetChanged()
        }

        fun setDownloadState(position: Int, progress: Int) {
            downloadingPosition = position
            downloadProgress = progress
            notifyItemChanged(position)
        }

        fun clearDownloadState(position: Int) {
            downloadingPosition = -1
            downloadProgress = 0
            notifyItemChanged(position)
        }

        class VH(
            view: View,
            val card: LinearLayout,
            val iconBox: FrameLayout,
            val noteIcon: ImageView,
            val titleTv: TextView,
            val downloadBtn: View,
            val progressLayout: LinearLayout,
            val progressBar: ProgressBar,
            val progressTv: TextView,
            val cancelTv: TextView,
            val downloadedTv: TextView
        ) : RecyclerView.ViewHolder(view)

        @SuppressLint("UseKtx")
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val ctx = parent.context
            val (outer, card) = rowCard(ctx)
            card.background = roundedBg(ctx, Color.parseColor(COLOR_ROW), SdpR.dimen._12sdp)
            val (iconBox, noteIcon, _) = rowIconTile(ctx)
            iconBox.background = roundedBg(ctx, Color.parseColor(COLOR_BG_LIGHT), SdpR.dimen._10sdp)

            val titleTv = TextView(ctx).apply {
                textSize = ctx.sspSp(SspR.dimen._11ssp)
                setTextColor(Color.parseColor(COLOR_TEXT_BRIGHT))
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply {
                    setMargins(ctx.sdp(SdpR.dimen._10sdp), 0, ctx.sdp(SdpR.dimen._8sdp), 0)
                }
            }

            // Tombol download: pill gradien aksen dengan ikon
            val downloadBtn = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = ripple(accentGradient(ctx, SdpR.dimen._14sdp))
                setPadding(ctx.sdp(SdpR.dimen._8sdp), ctx.sdp(SdpR.dimen._4sdp), ctx.sdp(SdpR.dimen._10sdp), ctx.sdp(SdpR.dimen._4sdp))
                val icon = ctx.sdp(SdpR.dimen._12sdp)
                addView(tintedIcon(ctx, R.drawable.zaif_rec_ic_download, Color.WHITE), LinearLayout.LayoutParams(icon, icon))
                addView(TextView(ctx).apply {
                    text = context.getString(R.string.download)
                    textSize = ctx.sspSp(SspR.dimen._9ssp)
                    setTextColor(Color.WHITE)
                    typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                    setPadding(ctx.sdp(SdpR.dimen._4sdp), 0, 0, 0)
                })
            }

            // Progress layout (shown while downloading)
            val progressLayout = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                visibility = View.GONE
                layoutParams = LinearLayout.LayoutParams(ctx.sdp(SdpR.dimen._70sdp), -2)
            }
            val progressBar = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
                layoutParams = LinearLayout.LayoutParams(-1, ctx.sdp(SdpR.dimen._4sdp))
                max = 100
                progress = 0
                progressTintList = ColorStateList.valueOf(Color.parseColor(COLOR_ACCENT_LIGHT))
                progressBackgroundTintList = ColorStateList.valueOf(Color.parseColor(COLOR_BG_LIGHT))
            }
            val bottomRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = ctx.sdp(SdpR.dimen._3sdp) }
            }
            val progressTv = TextView(ctx).apply {
                text = "0%"
                textSize = ctx.sspSp(SspR.dimen._8ssp)
                setTextColor(Color.parseColor(COLOR_ACCENT_LIGHT))
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.START
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            }
            val cancelTv = TextView(ctx).apply {
                text = context.getString(R.string.cancel)
                textSize = ctx.sspSp(SspR.dimen._8ssp)
                setTextColor(Color.parseColor(COLOR_DANGER))
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                gravity = Gravity.END
            }
            bottomRow.addView(progressTv)
            bottomRow.addView(cancelTv)
            progressLayout.addView(progressBar)
            progressLayout.addView(bottomRow)

            // Sudah diunduh: pill hijau lembut
            val downloadedTv = TextView(ctx).apply {
                text = "✓ " + context.getString(R.string.downloaded)
                textSize = ctx.sspSp(SspR.dimen._8ssp)
                setTextColor(Color.parseColor(COLOR_SUCCESS))
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                gravity = Gravity.CENTER
                background = roundedBg(ctx, Color.parseColor("#1F4ADE80"), SdpR.dimen._14sdp)
                setPadding(ctx.sdp(SdpR.dimen._8sdp), ctx.sdp(SdpR.dimen._4sdp), ctx.sdp(SdpR.dimen._8sdp), ctx.sdp(SdpR.dimen._4sdp))
                visibility = View.GONE
            }

            card.addView(iconBox)
            card.addView(titleTv)
            card.addView(downloadBtn)
            card.addView(progressLayout)
            card.addView(downloadedTv)

            return VH(outer, card, iconBox, noteIcon, titleTv, downloadBtn, progressLayout, progressBar, progressTv, cancelTv, downloadedTv)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val song           = items[position]
            val ctx            = holder.itemView.context

            val isDownloading  = downloadingPosition == position
            val isDownloaded   = song.title.trim().lowercase() in downloadedTitles

            holder.titleTv.text = song.title

            holder.downloadBtn.visibility    = if (!isDownloaded && !isDownloading) View.VISIBLE else View.GONE
            holder.progressLayout.visibility = if (isDownloading) View.VISIBLE else View.GONE
            holder.downloadedTv.visibility   = if (isDownloaded && !isDownloading) View.VISIBLE else View.GONE

            // Lagu yang sudah ada di perangkat: kotak ikon hijau supaya mudah dikenali
            holder.iconBox.background = roundedBg(
                ctx, Color.parseColor(if (isDownloaded) "#1F4ADE80" else COLOR_BG_LIGHT), SdpR.dimen._10sdp
            )
            holder.noteIcon.imageTintList = ColorStateList.valueOf(
                Color.parseColor(if (isDownloaded) COLOR_SUCCESS else COLOR_ACCENT_LIGHT)
            )

            if (isDownloading) {
                holder.progressBar.progress = downloadProgress
                holder.progressTv.text      = "$downloadProgress%"
            }
            holder.downloadBtn.setOnClickListener { onDownloadClick(song, position) }
            holder.cancelTv.setOnClickListener   { onCancelClick(position) }

            if (isDownloaded && !isDownloading) {
                holder.card.background = ripple(roundedBg(ctx, Color.parseColor(COLOR_ROW), SdpR.dimen._12sdp))
                holder.card.setOnClickListener { onPlayClick(song.title) }
            } else {
                holder.card.background = roundedBg(ctx, Color.parseColor(COLOR_ROW), SdpR.dimen._12sdp)
                holder.card.setOnClickListener(null)
                holder.card.isClickable = false
            }
        }

        override fun getItemCount() = items.size

        private fun Context.sdp(id: Int) = resources.getDimensionPixelSize(id)
        private fun Context.sspSp(id: Int) = resources.getDimension(id) / resources.displayMetrics.scaledDensity
    }

    // ─── PLAYER CARD ──────────────────────────────────────────────────────────

    @SuppressLint("UseKtx", "ClickableViewAccessibility")
    private fun buildElegantPlayerCard(context: Context, initialVolume: Float): LinearLayout {
        return LinearLayout(context).apply {
            id = 100
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#2B2560"), Color.parseColor(COLOR_BG_MEDIUM))
            ).apply {
                cornerRadius = context.sdp(SdpR.dimen._16sdp).toFloat()
                setStroke(context.sdp(SdpR.dimen._1sdp), Color.parseColor("#666C63FF"))
            }
            elevation = context.sdp(SdpR.dimen._8sdp).toFloat()

            val topRow = LinearLayout(context).apply {
                setPadding(
                    context.sdp(SdpR.dimen._10sdp),
                    context.sdp(SdpR.dimen._10sdp),
                    context.sdp(SdpR.dimen._10sdp),
                    0
                )
                gravity = Gravity.CENTER_VERTICAL
            }

            // Kotak "now playing": equalizer bergerak selama lagu diputar
            val tileSize = context.sdp(SdpR.dimen._34sdp)
            val nowPlayingTile = FrameLayout(context).apply {
                background = accentGradient(context, SdpR.dimen._10sdp)
                layoutParams = LinearLayout.LayoutParams(tileSize, tileSize)
                addView(EqualizerBarsView(context).apply {
                    id = 106
                    barColor = Color.WHITE
                    level = 0.85f
                    continuous = MusicPlayerManager.isPlaying
                }, FrameLayout.LayoutParams(context.sdp(SdpR.dimen._16sdp), context.sdp(SdpR.dimen._14sdp), Gravity.CENTER))
            }

            val titleTv = TextView(context).apply {
                id = 101
                textSize = context.sspSp(SspR.dimen._11ssp)
                setTextColor(Color.parseColor(COLOR_TEXT_BRIGHT))
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply {
                    setMargins(context.sdp(SdpR.dimen._10sdp), 0, context.sdp(SdpR.dimen._6sdp), 0)
                }
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.MARQUEE
                marqueeRepeatLimit = -1
                isSelected = true
            }

            val stopSize = context.sdp(SdpR.dimen._30sdp)
            val stopBtn = roundIconButton(
                context, 105, R.drawable.zaif_rec_ic_stop,
                GradientDrawable().apply { setColor(Color.parseColor("#26FF6B6B")) },
                Color.parseColor(COLOR_DANGER)
            ).apply {
                layoutParams = LinearLayout.LayoutParams(stopSize, stopSize).apply {
                    setMargins(0, 0, context.sdp(SdpR.dimen._8sdp), 0)
                }
                setOnClickListener {
                    MusicPlayerManager.stop()
                }
            }

            val playSize = context.sdp(SdpR.dimen._36sdp)
            val playBtn = roundIconButton(
                context, 102, R.drawable.zaif_rec_ic_play,
                accentGradient(context, SdpR.dimen._18sdp), Color.WHITE
            ).apply {
                layoutParams = LinearLayout.LayoutParams(playSize, playSize)
                elevation = context.sdp(SdpR.dimen._3sdp).toFloat()
                setOnClickListener {
                    if (MusicPlayerManager.isPlaying) {
                        MusicPlayerManager.pause()
                        setImageResource(R.drawable.zaif_rec_ic_play)
                    } else {
                        MusicPlayerManager.resume()
                        setImageResource(R.drawable.zaif_rec_ic_pause)
                    }
                }
            }

            topRow.addView(nowPlayingTile)
            topRow.addView(titleTv)
            topRow.addView(stopBtn)
            topRow.addView(playBtn)
            addView(topRow)

            val controlsRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(
                    context.sdp(SdpR.dimen._10sdp),
                    context.sdp(SdpR.dimen._6sdp),
                    context.sdp(SdpR.dimen._10sdp),
                    context.sdp(SdpR.dimen._10sdp)
                )
            }

            val musicSeekBar = MusicSeekBar(context).apply {
                id = 103
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    context.sdp(SdpR.dimen._24sdp),
                    2.5f
                )
                progressStartColor = Color.parseColor("#00C9FF")
                progressEndColor   = Color.parseColor("#92FE9D")
                glowColor          = Color.parseColor("#00C9FF")
                thumbBorderColor   = Color.parseColor("#00C9FF")
                trackColor         = Color.parseColor("#1E2340")
                showGlow           = true
                showThumb          = true
                listener = object : MusicSeekBar.OnProgressChangeListener {
                    override fun onProgressChanged(progress: Int, fromUser: Boolean) {
                        if (fromUser) MusicPlayerManager.seekTo(progress)
                    }
                    override fun onStartTrackingTouch() {
                        parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    override fun onStopTrackingTouch() {
                        parent?.requestDisallowInterceptTouchEvent(false)
                    }
                }
            }

            val volIconSize   = context.sdp(SdpR.dimen._16sdp)
            val volIconMargin = context.sdp(SdpR.dimen._8sdp)
            val volIcon = ImageView(context).apply {
                setImageResource(R.drawable.zaif_rec_ic_volume_up)
                setColorFilter(Color.parseColor(COLOR_TEXT_DIM))
                layoutParams = LinearLayout.LayoutParams(volIconSize, volIconSize).apply {
                    setMargins(volIconMargin, 0, volIconMargin, 0)
                }
            }

            val volBar = DJSeekBar(context).apply {
                id = 104
                max = 100
                progress = (initialVolume * 100).toInt()
                layoutParams = LinearLayout.LayoutParams(0, context.sdp(SdpR.dimen._20sdp), 1.2f)
                progressStartColor = Color.parseColor("#A78BFA")
                progressEndColor   = Color.parseColor("#FF6B9D")
                glowColor          = Color.parseColor("#A78BFA")
                trackColor         = Color.parseColor("#2A2F52")
                showGlow           = true
                listener = object : DJSeekBar.OnProgressChangeListener {
                    override fun onProgressChanged(progress: Int, fromUser: Boolean) {
                        if (fromUser) {
                            val vol = progress / 100f
                            MusicPlayerManager.setVolume(vol, vol)
                            saveMusicVolume(context, vol)
                        }
                    }
                    override fun onStartTrackingTouch() {
                        parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    override fun onStopTrackingTouch() {
                        parent?.requestDisallowInterceptTouchEvent(false)
                    }
                }
            }

            controlsRow.addView(musicSeekBar)
            controlsRow.addView(volIcon)
            controlsRow.addView(volBar)
            addView(controlsRow)

            setOnTouchListener { _, _ -> true }
        }
    }

    private fun updatePlayerUI(card: LinearLayout, context: Context) {
        val track = MusicPlayerManager.getCurrentTrack()
        if (track != null) {
            card.findViewById<TextView>(101)?.text = track.title
            card.findViewById<ImageButton>(102)?.setImageResource(
                if (MusicPlayerManager.isPlaying) R.drawable.zaif_rec_ic_pause
                else R.drawable.zaif_rec_ic_play
            )
            card.findViewById<EqualizerBarsView>(106)?.continuous = MusicPlayerManager.isPlaying
            card.findViewById<DJSeekBar>(104)?.progress = (loadMusicVolume(context) * 100).toInt()
        }
    }

    private fun loadDeviceTracks(context: Context): List<MusicPlayerManager.MusicTrack> {
        val tracks = mutableListOf<MusicPlayerManager.MusicTrack>()
        val permission = if (Build.VERSION.SDK_INT >= 33)
            Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED)
            return tracks

        val projection = arrayOf(
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media._ID
        )
        val candidateUris = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL))
            }
            add(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
        }

        for (uri in candidateUris.distinct()) {
            try {
                context.contentResolver.query(
                    uri, projection,
                    "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, null
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val titleIndex = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
                        val durIndex   = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
                        val idIndex    = cursor.getColumnIndex(MediaStore.Audio.Media._ID)

                        val title = if (titleIndex >= 0) cursor.getString(titleIndex) ?: "Unknown" else "Unknown"
                        val dur   = if (durIndex >= 0) cursor.getLong(durIndex) else 0L
                        val id    = if (idIndex >= 0) cursor.getLong(idIndex) else continue

                        tracks.add(
                            MusicPlayerManager.MusicTrack(
                                title, dur, false, 0,
                                Uri.withAppendedPath(uri, id.toString())
                            )
                        )
                    }
                }
                return tracks
            } catch (e: IllegalArgumentException) {
                Log.w("MusicListDialogHelper", "Unsupported MediaStore volume: $uri", e)
                tracks.clear()
            } catch (e: SecurityException) {
                Log.w("MusicListDialogHelper", "MediaStore permission denied", e)
                return tracks
            }
        }
        return tracks
    }

    private fun formatMs(ms: Long): String {
        val min = TimeUnit.MILLISECONDS.toMinutes(ms)
        val sec = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
        return "%d:%02d".format(min, sec)
    }
}

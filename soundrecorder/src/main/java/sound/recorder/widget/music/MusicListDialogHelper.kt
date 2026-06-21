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
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ContextThemeWrapper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*
import com.intuit.sdp.R as SdpR
import com.intuit.ssp.R as SspR
import sound.recorder.widget.R
import sound.recorder.widget.builder.ZaifSDKBuilder
import sound.recorder.widget.builder.ZaifSDKConfig
import sound.recorder.widget.recording.RecordingListDialogHelper
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

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private const val COLOR_BG_DARK     = "#0A0E1A"
    private const val COLOR_BG_MEDIUM   = "#1A1F3A"
    private const val COLOR_BG_LIGHT    = "#252B47"
    private const val COLOR_ACCENT      = "#6C63FF"
    private const val COLOR_TEXT_BRIGHT = "#FFFFFF"
    private const val COLOR_TEXT_DIM    = "#8B93B8"

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
    fun show(context: Context, isDownload: Boolean = false) {
        val themedContext = resolveThemedContext(context)

        zaifSDKConfig = ZaifSDKBuilder.load(context)

        val savedVolume = loadMusicVolume(themedContext)
        MusicPlayerManager.setVolume(savedVolume, savedVolume)

        val displayHeight = themedContext.resources.displayMetrics.heightPixels
        val rootContainer = FrameLayout(themedContext).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            minimumHeight = displayHeight
            setBackgroundColor(Color.parseColor(COLOR_BG_DARK))
        }

        val mainLayout = LinearLayout(themedContext).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(-1, -1)
        }

        // ─── HEADER ───
        val headerContainer = RelativeLayout(themedContext).apply {
            setPadding(
                themedContext.sdp(SdpR.dimen._12sdp),
                themedContext.sdp(SdpR.dimen._8sdp),
                themedContext.sdp(SdpR.dimen._12sdp),
                themedContext.sdp(SdpR.dimen._4sdp)
            )
        }

        val titleView = TextView(themedContext).apply {
            text = themedContext.getString(R.string.list_music).uppercase()
            setTextColor(Color.parseColor(COLOR_ACCENT))
            textSize = themedContext.sspSp(SspR.dimen._11ssp)
            letterSpacing = 0.1f
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        }

        val closeBtnSize = themedContext.sdp(SdpR.dimen._24sdp)
        val closeBtn = FrameLayout(themedContext).apply {
            layoutParams = RelativeLayout.LayoutParams(closeBtnSize, closeBtnSize).apply {
                addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
                addRule(RelativeLayout.CENTER_VERTICAL)
            }
            background = RippleDrawable(
                ColorStateList.valueOf(Color.parseColor("#20FFFFFF")), null, null
            )
            addView(TextView(themedContext).apply {
                text = "✕"
                setTextColor(Color.parseColor(COLOR_TEXT_DIM))
                textSize = themedContext.sspSp(SspR.dimen._14ssp)
                gravity = Gravity.CENTER
            })
        }

        headerContainer.addView(titleView)
        headerContainer.addView(closeBtn)
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
                layoutParams = LinearLayout.LayoutParams(0, themedContext.sdp(SdpR.dimen._24sdp), 1f)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor(COLOR_BG_MEDIUM))
                    cornerRadius = themedContext.sdp(SdpR.dimen._12sdp).toFloat()
                }
            }
            tabLocal  = makeTabButton(context.getString(R.string.local_song))
            tabOnline = makeTabButton(context.getString(R.string.online_song))
            val tabStrip = LinearLayout(themedContext).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(
                    themedContext.sdp(SdpR.dimen._12sdp), 0,
                    themedContext.sdp(SdpR.dimen._12sdp), themedContext.sdp(SdpR.dimen._4sdp)
                )
            }
            tabStrip.addView(tabLocal)
            tabStrip.addView(View(themedContext).apply {
                layoutParams = LinearLayout.LayoutParams(themedContext.sdp(SdpR.dimen._8sdp), 0)
            })
            tabStrip.addView(tabOnline)
            mainLayout.addView(tabStrip)
        }

        // ─── SEARCH BAR ───
        val searchContainer = FrameLayout(themedContext).apply {
            setPadding(
                themedContext.sdp(SdpR.dimen._12sdp),
                themedContext.sdp(SdpR.dimen._2sdp),
                themedContext.sdp(SdpR.dimen._12sdp),
                themedContext.sdp(SdpR.dimen._4sdp)
            )
        }

        val searchField = EditText(themedContext).apply {
            hint = themedContext.getString(R.string.search_song_accompaniment)
            setHintTextColor(Color.parseColor(COLOR_TEXT_DIM))
            setTextColor(Color.WHITE)
            textSize = themedContext.sspSp(SspR.dimen._10ssp)
            setPadding(
                themedContext.sdp(SdpR.dimen._10sdp),
                themedContext.sdp(SdpR.dimen._6sdp),
                themedContext.sdp(SdpR.dimen._10sdp),
                themedContext.sdp(SdpR.dimen._6sdp)
            )
            background = GradientDrawable().apply {
                setColor(Color.parseColor(COLOR_BG_MEDIUM))
                setStroke(themedContext.sdp(SdpR.dimen._1sdp), Color.parseColor(COLOR_BG_LIGHT))
                cornerRadius = themedContext.sdp(SdpR.dimen._16sdp).toFloat()
            }
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

        val dialog = AlertDialog.Builder(themedContext, R.style.FullScreenDialogTheme)
            .setView(rootContainer)
            .create()

        closeBtn.setOnClickListener { dialog.dismiss() }

        ViewCompat.setOnApplyWindowInsetsListener(rootContainer) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
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
            val allFirestoreSongs = mutableListOf<FirestoreSong>()
            val downloadHandler = Handler(Looper.getMainLooper())
            val dm = themedContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

            fun startPoll(position: Int, downloadId: Long, songTitle: String) {
                val startTime = System.currentTimeMillis()
                val poll = object : Runnable {
                    override fun run() {
                        val cursor = dm.query(DownloadManager.Query().setFilterById(downloadId))
                        if (!cursor.moveToFirst()) { cursor.close(); return }
                        val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        val bytesIdx  = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                        val totalIdx  = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                        val status    = if (statusIdx >= 0) cursor.getInt(statusIdx) else -1
                        val bytes     = if (bytesIdx  >= 0) cursor.getLong(bytesIdx) else 0L
                        val total     = if (totalIdx  >= 0) cursor.getLong(totalIdx) else 0L
                        cursor.close()

                        // Timeout 30 detik jika tidak ada progress (ga mulai-mulai)
                        if (System.currentTimeMillis() - startTime > 30000 && bytes <= 0) {
                            dm.remove(downloadId)
                            activeDownloadId = -1L
                            downloadingSongId = ""
                            onlineAdapter?.clearDownloadState(position)
                            Toast.makeText(themedContext, context.getString(R.string.download_failed), Toast.LENGTH_SHORT).show()
                            return
                        }

                        val progress = if (total > 0) ((bytes * 100) / total).toInt() else 0
                        onlineAdapter?.setDownloadState(position, progress)
                        if (status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED) {
                            activeDownloadId = -1L
                            downloadingSongId = ""
                            onlineAdapter?.clearDownloadState(position)
                            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                onlineAdapter?.addDownloadedTitle(songTitle)
                                refreshLocalTracks()
                            } else {
                                val reasonIdx = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                                val reason = if (reasonIdx >= 0) cursor.getInt(reasonIdx) else -1
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
                                Toast.makeText(themedContext, "${context.getString(R.string.download_failed)}: $errorMsg", Toast.LENGTH_SHORT).show()
                            }
                            return
                        }
                        downloadHandler.postDelayed(this, 500)
                    }
                }
                downloadHandler.post(poll)
            }

            fun renderOnlineList(query: String) {
                val filtered = allFirestoreSongs.filter { it.title.contains(query, true) }
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
                        dm.remove(activeDownloadId)
                        downloadHandler.removeCallbacksAndMessages(null)
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

            var isOnlineLoaded = false

            fun activateTabLocal() {
                isOnlineTab = false
                tabLocal?.setTextColor(Color.WHITE)
                tabLocal?.background = GradientDrawable().apply {
                    setColor(Color.parseColor(COLOR_ACCENT))
                    cornerRadius = themedContext.sdp(SdpR.dimen._20sdp).toFloat()
                }
                tabOnline?.setTextColor(Color.parseColor(COLOR_TEXT_DIM))
                tabOnline?.background = GradientDrawable().apply {
                    setColor(Color.parseColor(COLOR_BG_MEDIUM))
                    cornerRadius = themedContext.sdp(SdpR.dimen._20sdp).toFloat()
                }
                localRecyclerView.visibility = View.VISIBLE
                onlineRecyclerView?.visibility = View.GONE
                onlineLoadingLayout?.visibility = View.GONE
            }

            fun loadFirestoreIfNeeded() {
                if (isOnlineLoaded) {
                    onlineRecyclerView?.visibility = View.VISIBLE
                    onlineLoadingLayout?.visibility = View.GONE
                    renderOnlineList(searchField.text?.toString().orEmpty())
                    if (allTracks.isNotEmpty()) {
                        val titles = allTracks.map { it.title.trim().lowercase() }.toSet()
                        onlineAdapter?.setDownloadedTitles(titles)
                    }
                    return
                }
                onlineLoadingText?.text = context.getString(R.string.loading_audio)
                onlineLoadingLayout?.visibility = View.VISIBLE
                onlineLoadingLayout?.getChildAt(0)?.visibility = View.VISIBLE
                onlineRecyclerView?.visibility = View.GONE

                var collection = "song_"+zaifSDKConfig?.applicationId

                if(zaifSDKConfig?.applicationId=="gendang.elektronik.beat"){
                    collection = "song"
                }

                FirebaseFirestore.getInstance().collection(collection)
                    .whereArrayContains("appId", zaifSDKConfig?.applicationId ?: "")
                    .get()
                    .addOnSuccessListener { snapshot ->
                        allFirestoreSongs.clear()
                        for (doc in snapshot.documents) {
                            val song = FirestoreSong(
                                id = doc.id,
                                title = doc.getString("title") ?: "",
                                link_download = doc.getString("link_download") ?: "",
                                appId = doc.get("appId") as? List<String> ?: emptyList()
                            )
                            allFirestoreSongs.add(song)
                        }
                        isOnlineLoaded = true
                        onlineLoadingLayout?.visibility = View.GONE
                        onlineRecyclerView?.visibility = View.VISIBLE
                        renderOnlineList(searchField.text?.toString().orEmpty())
                        if (allTracks.isNotEmpty()) {
                            val titles = allTracks.map { it.title.trim().lowercase() }.toSet()
                            onlineAdapter?.setDownloadedTitles(titles)
                        }
                        if (activeDownloadId != -1L) {
                            val pos = allFirestoreSongs.indexOfFirst { it.id == downloadingSongId }
                            if (pos >= 0) {
                                val cursor = dm.query(DownloadManager.Query().setFilterById(activeDownloadId))
                                val stillRunning = cursor.moveToFirst().also { cursor.close() }
                                if (stillRunning) {
                                    onlineAdapter?.setDownloadState(pos, 0)
                                    startPoll(pos, activeDownloadId, allFirestoreSongs[pos].title)
                                } else {
                                    activeDownloadId = -1L
                                    downloadingSongId = ""
                                }
                            } else {
                                activeDownloadId = -1L
                                downloadingSongId = ""
                            }
                        }
                    }
                    .addOnFailureListener {
                        onlineLoadingText?.text = context.getString(R.string.try_again)
                        onlineLoadingLayout?.visibility = View.VISIBLE
                        onlineLoadingLayout?.getChildAt(0)?.visibility = View.GONE
                        onlineRecyclerView?.visibility = View.GONE
                    }
            }

            fun activateTabOnline() {
                isOnlineTab = true
                tabOnline?.setTextColor(Color.WHITE)
                tabOnline?.background = GradientDrawable().apply {
                    setColor(Color.parseColor(COLOR_ACCENT))
                    cornerRadius = themedContext.sdp(SdpR.dimen._20sdp).toFloat()
                }
                tabLocal?.setTextColor(Color.parseColor(COLOR_TEXT_DIM))
                tabLocal?.background = GradientDrawable().apply {
                    setColor(Color.parseColor(COLOR_BG_MEDIUM))
                    cornerRadius = themedContext.sdp(SdpR.dimen._20sdp).toFloat()
                }
                localRecyclerView.visibility = View.GONE
                loadFirestoreIfNeeded()
            }

            tabLocal?.setOnClickListener { activateTabLocal() }
            tabOnline?.setOnClickListener { activateTabOnline() }
            activateTabLocal()

            dialog.setOnDismissListener {
                downloadHandler.removeCallbacksAndMessages(null)
                MusicPlayerManager.setListener(null)
                appScope.coroutineContext.cancelChildren()
            }
        } else {
            dialog.setOnDismissListener {
                MusicPlayerManager.setListener(null)
                appScope.coroutineContext.cancelChildren()
            }
        }

        MusicPlayerManager.setListener(object : MusicPlayerManager.PlayerListener {
            override fun onPlay(track: MusicPlayerManager.MusicTrack) {
                val vol = loadMusicVolume(themedContext)
                MusicPlayerManager.setVolume(vol, vol)
                playerCard.visibility = View.VISIBLE
                playerCard.findViewById<TextView>(101)?.text = track.title
                playerCard.findViewById<ImageButton>(102)
                    ?.setImageResource(android.R.drawable.ic_media_pause)
                statusListener?.onMusicPlay(track)
            }
            override fun onPause() {
                playerCard.findViewById<ImageButton>(102)
                    ?.setImageResource(android.R.drawable.ic_media_play)
                statusListener?.onMusicPause(currentTrack)
            }
            override fun onStop() {
                if (MusicPlayerManager.getCurrentTrack() == null) playerCard.visibility = View.GONE
                statusListener?.onMusicStop()
            }
            override fun onProgress(current: Int, max: Int) {
                playerCard.findViewById<MusicSeekBar>(103)?.apply {
                    this.max = max
                    this.progress = current
                }
                statusListener?.onMusicProgress(current, max)
            }
            override fun onComplete() {
                playerCard.visibility = View.GONE
                statusListener?.onMusicComplete()
            }
        })

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

        class VH(view: View) : RecyclerView.ViewHolder(view)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val themedContext = parent.context
            val itemRow = LinearLayout(themedContext).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(
                    themedContext.sdp(SdpR.dimen._12sdp),
                    themedContext.sdp(SdpR.dimen._6sdp),
                    themedContext.sdp(SdpR.dimen._12sdp),
                    themedContext.sdp(SdpR.dimen._6sdp)
                )
                gravity = Gravity.CENTER_VERTICAL
                background = RippleDrawable(
                    ColorStateList.valueOf(Color.parseColor("#15FFFFFF")), null, null
                )
                layoutParams = ViewGroup.LayoutParams(-1, -2)
            }

            val iconBoxSize = themedContext.sdp(SdpR.dimen._28sdp)
            val iconBox = FrameLayout(themedContext).apply {
                id = View.generateViewId()
                layoutParams = LinearLayout.LayoutParams(iconBoxSize, iconBoxSize)
            }
            iconBox.addView(TextView(themedContext).apply {
                id = View.generateViewId()
                text = "♪"
                textSize = themedContext.sspSp(SspR.dimen._11ssp)
                gravity = Gravity.CENTER
            })

            val textStack = LinearLayout(themedContext).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(themedContext.sdp(SdpR.dimen._8sdp), 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            }

            textStack.addView(TextView(themedContext).apply {
                id = View.generateViewId()
                textSize = themedContext.sspSp(SspR.dimen._11ssp)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })

            textStack.addView(TextView(themedContext).apply {
                id = View.generateViewId()
                textSize = themedContext.sspSp(SspR.dimen._8ssp)
            })

            itemRow.addView(iconBox)
            itemRow.addView(textStack)

            val container = LinearLayout(themedContext).apply {
                orientation = LinearLayout.VERTICAL
                addView(itemRow)
                addView(View(themedContext).apply {
                    layoutParams = LinearLayout.LayoutParams(-1, 1).apply {
                        setMargins(themedContext.sdp(SdpR.dimen._16sdp), 0, themedContext.sdp(SdpR.dimen._16sdp), 0)
                    }
                    setBackgroundColor(Color.parseColor("#156C63FF"))
                })
            }

            return VH(container)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val track = items[position]
            val themedContext = holder.itemView.context
            val isPlaying = MusicPlayerManager.getCurrentTrack()?.title == track.title

            val container = holder.itemView as LinearLayout
            val itemRow   = container.getChildAt(0) as LinearLayout
            val iconBox   = itemRow.getChildAt(0) as FrameLayout
            val iconTv    = iconBox.getChildAt(0) as TextView
            val textStack = itemRow.getChildAt(1) as LinearLayout
            val titleTv   = textStack.getChildAt(0) as TextView
            val metaTv    = textStack.getChildAt(1) as TextView

            iconBox.background = GradientDrawable().apply {
                setColor(
                    if (isPlaying) Color.parseColor(COLOR_ACCENT)
                    else Color.parseColor(COLOR_BG_MEDIUM)
                )
                cornerRadius = themedContext.sdp(SdpR.dimen._8sdp).toFloat()
            }
            iconTv.setTextColor(if (isPlaying) Color.WHITE else Color.parseColor(COLOR_ACCENT))

            titleTv.apply {
                text = track.title.orEmpty().uppercase()
                setTextColor(
                    if (isPlaying) Color.parseColor(COLOR_ACCENT)
                    else Color.parseColor(COLOR_TEXT_BRIGHT)
                )
                typeface = Typeface.create(
                    "sans-serif-medium",
                    if (isPlaying) Typeface.BOLD else Typeface.NORMAL
                )
            }

            metaTv.apply {
                text = if (track.isRaw) "ASSET • ${formatMs(track.duration)}"
                else "STORAGE • ${formatMs(track.duration)}"
                setTextColor(Color.parseColor(COLOR_TEXT_DIM))
            }

            itemRow.setOnClickListener { onTrackClick(track) }
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

        class VH(view: View) : RecyclerView.ViewHolder(view)

        @SuppressLint("UseKtx")
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val ctx = parent.context

            val itemRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(
                    ctx.sdp(SdpR.dimen._12sdp),
                    ctx.sdp(SdpR.dimen._6sdp),
                    ctx.sdp(SdpR.dimen._12sdp),
                    ctx.sdp(SdpR.dimen._6sdp)
                )
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = ViewGroup.LayoutParams(-1, -2)
            }

            val iconBoxSize = ctx.sdp(SdpR.dimen._28sdp)
            val iconBox = FrameLayout(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(iconBoxSize, iconBoxSize)
            }
            iconBox.addView(TextView(ctx).apply {
                text = "♪"
                textSize = ctx.sspSp(SspR.dimen._11ssp)
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor(COLOR_ACCENT))
            })
            iconBox.background = GradientDrawable().apply {
                setColor(Color.parseColor(COLOR_BG_MEDIUM))
                cornerRadius = ctx.sdp(SdpR.dimen._6sdp).toFloat()
            }

            val titleTv = TextView(ctx).apply {
                textSize = ctx.sspSp(SspR.dimen._11ssp)
                setTextColor(Color.parseColor(COLOR_TEXT_BRIGHT))
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply {
                    setMargins(ctx.sdp(SdpR.dimen._8sdp), 0, ctx.sdp(SdpR.dimen._6sdp), 0)
                }
            }

            // Download button ("Download" text)
            val downloadBtn = TextView(ctx).apply {
                text = context.getString(R.string.download)
                textSize = ctx.sspSp(SspR.dimen._9ssp)
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor(COLOR_ACCENT))
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                background = GradientDrawable().apply {
                    setColor(Color.TRANSPARENT)
                    setStroke(ctx.sdp(SdpR.dimen._1sdp), Color.parseColor(COLOR_ACCENT))
                    cornerRadius = ctx.sdp(SdpR.dimen._10sdp).toFloat()
                }
                setPadding(
                    ctx.sdp(SdpR.dimen._6sdp),
                    ctx.sdp(SdpR.dimen._3sdp),
                    ctx.sdp(SdpR.dimen._6sdp),
                    ctx.sdp(SdpR.dimen._3sdp)
                )
            }

            // Progress layout (shown while downloading)
            val progressLayout = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                visibility = View.GONE
                layoutParams = LinearLayout.LayoutParams(ctx.sdp(SdpR.dimen._60sdp), -2)
            }
            val progressBar = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
                layoutParams = LinearLayout.LayoutParams(-1, ctx.sdp(SdpR.dimen._4sdp))
                max = 100
                progress = 0
            }
            val bottomRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(-1, -2)
            }
            val progressTv = TextView(ctx).apply {
                text = "0%"
                textSize = ctx.sspSp(SspR.dimen._8ssp)
                setTextColor(Color.parseColor(COLOR_ACCENT))
                gravity = Gravity.START
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            }
            val cancelTv = TextView(ctx).apply {
                text = context.getString(R.string.cancel)
                textSize = ctx.sspSp(SspR.dimen._8ssp)
                setTextColor(Color.parseColor("#FF6B6B"))
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                gravity = Gravity.END
            }
            bottomRow.addView(progressTv)
            bottomRow.addView(cancelTv)
            progressLayout.addView(progressBar)
            progressLayout.addView(bottomRow)

            val downloadedTv = TextView(ctx).apply {
                text = "✓ " + context.getString(R.string.downloaded)
                textSize = ctx.sspSp(SspR.dimen._8ssp)
                setTextColor(Color.parseColor("#4CAF50"))
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                gravity = Gravity.CENTER
                visibility = View.GONE
            }

            itemRow.addView(iconBox)
            itemRow.addView(titleTv)
            itemRow.addView(downloadBtn)
            itemRow.addView(progressLayout)
            itemRow.addView(downloadedTv)

            val container = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                addView(itemRow)
                addView(View(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(-1, 1).apply {
                        setMargins(ctx.sdp(SdpR.dimen._16sdp), 0, ctx.sdp(SdpR.dimen._16sdp), 0)
                    }
                    setBackgroundColor(Color.parseColor("#156C63FF"))
                })
            }

            return VH(container)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val song           = items[position]
            val container      = holder.itemView as LinearLayout
            val itemRow        = container.getChildAt(0) as LinearLayout
            val titleTv        = itemRow.getChildAt(1) as TextView
            val downloadBtn    = itemRow.getChildAt(2) as TextView
            val progressLayout = itemRow.getChildAt(3) as LinearLayout
            val progressBar    = progressLayout.getChildAt(0) as ProgressBar
            val bottomRow      = progressLayout.getChildAt(1) as LinearLayout
            val progressTv     = bottomRow.getChildAt(0) as TextView
            val cancelTv       = bottomRow.getChildAt(1) as TextView
            val downloadedTv   = itemRow.getChildAt(4) as TextView

            val isDownloading  = downloadingPosition == position
            val isDownloaded   = song.title.trim().lowercase() in downloadedTitles

            titleTv.text = song.title.uppercase()

            downloadBtn.visibility    = if (!isDownloaded && !isDownloading) View.VISIBLE else View.GONE
            progressLayout.visibility = if (isDownloading) View.VISIBLE else View.GONE
            downloadedTv.visibility   = if (isDownloaded && !isDownloading) View.VISIBLE else View.GONE

            if (isDownloading) {
                progressBar.progress = downloadProgress
                progressTv.text      = "$downloadProgress%"
            }
            downloadBtn.setOnClickListener { onDownloadClick(song, position) }
            cancelTv.setOnClickListener   { onCancelClick(position) }

            if (isDownloaded && !isDownloading) {
                itemRow.setOnClickListener { onPlayClick(song.title) }
            } else {
                itemRow.setOnClickListener(null)
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
            background = GradientDrawable().apply {
                setColor(Color.parseColor(COLOR_BG_MEDIUM))
                cornerRadius = context.sdp(SdpR.dimen._12sdp).toFloat()
                setStroke(context.sdp(SdpR.dimen._1sdp), Color.parseColor(COLOR_ACCENT))
            }
            elevation = context.sdp(SdpR.dimen._4sdp).toFloat()

            val topRow = LinearLayout(context).apply {
                setPadding(
                    context.sdp(SdpR.dimen._12sdp),
                    context.sdp(SdpR.dimen._6sdp),
                    context.sdp(SdpR.dimen._12sdp),
                    0
                )
                gravity = Gravity.CENTER_VERTICAL
            }

            val titleTv = TextView(context).apply {
                id = 101
                textSize = context.sspSp(SspR.dimen._11ssp)
                setTextColor(Color.parseColor(COLOR_TEXT_BRIGHT))
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }

            val btnSize = context.sdp(SdpR.dimen._28sdp)
            val btnPad  = context.sdp(SdpR.dimen._4sdp)
            val playBtn = ImageButton(context).apply {
                id = 102
                background = null
                setColorFilter(Color.parseColor(COLOR_ACCENT))
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = LinearLayout.LayoutParams(btnSize, btnSize)
                setPadding(btnPad, btnPad, btnPad, btnPad)
                setOnClickListener {
                    if (MusicPlayerManager.isPlaying) {
                        MusicPlayerManager.pause()
                        setImageResource(android.R.drawable.ic_media_play)
                    } else {
                        MusicPlayerManager.resume()
                        setImageResource(android.R.drawable.ic_media_pause)
                    }
                }
            }

            topRow.addView(titleTv)
            topRow.addView(playBtn)
            addView(topRow)

            val controlsRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(
                    context.sdp(SdpR.dimen._12sdp),
                    context.sdp(SdpR.dimen._4sdp),
                    context.sdp(SdpR.dimen._12sdp),
                    context.sdp(SdpR.dimen._8sdp)
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
                setImageResource(android.R.drawable.ic_lock_silent_mode_off)
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
                if (MusicPlayerManager.isPlaying) android.R.drawable.ic_media_pause
                else android.R.drawable.ic_media_play
            )
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

        val uri        = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media._ID
        )
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
    }

    private fun formatMs(ms: Long): String {
        val min = TimeUnit.MILLISECONDS.toMinutes(ms)
        val sec = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
        return "%d:%02d".format(min, sec)
    }
}
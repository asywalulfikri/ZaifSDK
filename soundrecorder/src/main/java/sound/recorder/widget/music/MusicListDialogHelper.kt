package sound.recorder.widget.music

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ContextThemeWrapper   // ← FIX IMPORT
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.intuit.sdp.R as SdpR
import com.intuit.ssp.R as SspR
import sound.recorder.widget.R
import java.util.concurrent.TimeUnit

object MusicListDialogHelper {

    private const val PREFS_NAME       = "music_player_prefs"
    private const val KEY_MUSIC_VOLUME = "music_volume"
    private const val DEFAULT_VOLUME   = 0.7f

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

    private const val COLOR_BG_DARK     = "#0A0E1A"
    private const val COLOR_BG_MEDIUM   = "#1A1F3A"
    private const val COLOR_BG_LIGHT    = "#252B47"
    private const val COLOR_ACCENT      = "#6C63FF"
    private const val COLOR_TEXT_BRIGHT = "#FFFFFF"
    private const val COLOR_TEXT_DIM    = "#8B93B8"

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

    // ── FIX: resolve context yang aman dengan theme Material ─────────────────
    private fun resolveThemedContext(context: Context): Context {
        // Jika sudah Activity yang valid dan tidak destroyed, pakai langsung
        if (context is Activity && !context.isFinishing && !context.isDestroyed) {
            return context
        }
        // Wrap dengan theme AppCompat agar TextView bisa load ColorStateList
        return ContextThemeWrapper(context, androidx.appcompat.R.style.Theme_AppCompat_DayNight)
    }

    @SuppressLint("UseKtx", "ClickableViewAccessibility")
    fun show(context: Context) {
        // ── FIX: pakai themedContext untuk semua View ─────────────────────────
        val themedContext = resolveThemedContext(context)

        val savedVolume = loadMusicVolume(themedContext)
        MusicPlayerManager.setVolume(savedVolume, savedVolume)

        val rootContainer = FrameLayout(themedContext).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            setBackgroundColor(Color.parseColor(COLOR_BG_DARK))
        }

        val mainLayout = LinearLayout(themedContext).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(-1, -1)
        }

        // ─── HEADER ───
        val headerContainer = RelativeLayout(themedContext).apply {
            setPadding(
                themedContext.sdp(SdpR.dimen._16sdp),
                themedContext.sdp(SdpR.dimen._16sdp),
                themedContext.sdp(SdpR.dimen._16sdp),
                themedContext.sdp(SdpR.dimen._8sdp)
            )
        }

        val titleView = TextView(themedContext).apply {
            text = themedContext.getString(R.string.list_music).uppercase()
            setTextColor(Color.parseColor(COLOR_ACCENT))
            textSize = themedContext.sspSp(SspR.dimen._14ssp)
            letterSpacing = 0.1f
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        }

        val closeBtnSize = themedContext.sdp(SdpR.dimen._32sdp)
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

        // ─── SEARCH BAR ───
        val searchContainer = FrameLayout(themedContext).apply {
            setPadding(
                themedContext.sdp(SdpR.dimen._16sdp),
                themedContext.sdp(SdpR.dimen._8sdp),
                themedContext.sdp(SdpR.dimen._16sdp),
                themedContext.sdp(SdpR.dimen._8sdp)
            )
        }

        val searchField = EditText(themedContext).apply {
            hint = themedContext.getString(R.string.search_song_accompaniment)
            setHintTextColor(Color.parseColor(COLOR_TEXT_DIM))
            setTextColor(Color.WHITE)
            textSize = themedContext.sspSp(SspR.dimen._12ssp)
            setPadding(
                themedContext.sdp(SdpR.dimen._12sdp),
                themedContext.sdp(SdpR.dimen._10sdp),
                themedContext.sdp(SdpR.dimen._12sdp),
                themedContext.sdp(SdpR.dimen._10sdp)
            )
            background = GradientDrawable().apply {
                setColor(Color.parseColor(COLOR_BG_MEDIUM))
                setStroke(themedContext.sdp(SdpR.dimen._1sdp), Color.parseColor(COLOR_BG_LIGHT))
                cornerRadius = themedContext.sdp(SdpR.dimen._20sdp).toFloat()
            }
        }
        searchContainer.addView(searchField)
        mainLayout.addView(searchContainer)

        // ─── LIST ───
        val scrollView = ScrollView(themedContext).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        val listLayout = LinearLayout(themedContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, themedContext.sdp(SdpR.dimen._100sdp))
        }
        scrollView.addView(listLayout)
        mainLayout.addView(scrollView)
        rootContainer.addView(mainLayout)

        // ─── FLOATING PLAYER CARD ───
        val playerCard = buildElegantPlayerCard(themedContext, savedVolume)
        val playerMargin = themedContext.sdp(SdpR.dimen._12sdp)
        val playerBottom = themedContext.sdp(SdpR.dimen._16sdp)
        rootContainer.addView(playerCard, FrameLayout.LayoutParams(-1, -2).apply {
            gravity = Gravity.BOTTOM
            setMargins(playerMargin, 0, playerMargin, playerBottom)
        })

        // ── FIX: Dialog pakai themedContext, bukan raw context ───────────────
        // Theme_AppCompat_Dialog_Alert memiliki semua color resource yang dibutuhkan TextView
        val dialog = AlertDialog.Builder(themedContext, R.style.FullScreenDialogTheme)
            .setView(rootContainer)
            .create()

        // Fallback jika R.style.FullScreenDialogTheme belum ada di styles.xml
        // Gunakan: AlertDialog.Builder(themedContext, androidx.appcompat.R.style.Theme_AppCompat_Dialog)

        closeBtn.setOnClickListener { dialog.dismiss() }

        ViewCompat.setOnApplyWindowInsetsListener(rootContainer) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // ─── PLAYER LISTENER ───
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

        val allTracks = rawTracks + loadDeviceTracks(themedContext)

        fun renderList(query: String) {
            listLayout.removeAllViews()
            val filtered = allTracks.filter { it.title.contains(query, true) }

            filtered.forEach { track ->
                val isPlaying = MusicPlayerManager.getCurrentTrack()?.title == track.title

                val itemRow = LinearLayout(themedContext).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(
                        themedContext.sdp(SdpR.dimen._16sdp),
                        themedContext.sdp(SdpR.dimen._10sdp),
                        themedContext.sdp(SdpR.dimen._16sdp),
                        themedContext.sdp(SdpR.dimen._10sdp)
                    )
                    gravity = Gravity.CENTER_VERTICAL
                    background = RippleDrawable(
                        ColorStateList.valueOf(Color.parseColor("#15FFFFFF")), null, null
                    )
                }

                val iconBoxSize   = themedContext.sdp(SdpR.dimen._36sdp)
                val iconBoxCorner = themedContext.sdp(SdpR.dimen._8sdp).toFloat()
                val iconBox = FrameLayout(themedContext).apply {
                    layoutParams = LinearLayout.LayoutParams(iconBoxSize, iconBoxSize)
                    background = GradientDrawable().apply {
                        setColor(
                            if (isPlaying) Color.parseColor(COLOR_ACCENT)
                            else Color.parseColor(COLOR_BG_MEDIUM)
                        )
                        cornerRadius = iconBoxCorner
                    }
                    addView(TextView(themedContext).apply {
                        text = "♪"
                        setTextColor(if (isPlaying) Color.WHITE else Color.parseColor(COLOR_ACCENT))
                        textSize = themedContext.sspSp(SspR.dimen._12ssp)
                        gravity = Gravity.CENTER
                    })
                }

                val textStack = LinearLayout(themedContext).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(themedContext.sdp(SdpR.dimen._10sdp), 0, 0, 0)
                    layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                }

                textStack.addView(TextView(themedContext).apply {
                    text = track.title.uppercase()
                    textSize = themedContext.sspSp(SspR.dimen._12ssp)
                    setTextColor(
                        if (isPlaying) Color.parseColor(COLOR_ACCENT)
                        else Color.parseColor(COLOR_TEXT_BRIGHT)
                    )
                    typeface = Typeface.create(
                        "sans-serif-medium",
                        if (isPlaying) Typeface.BOLD else Typeface.NORMAL
                    )
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                })

                textStack.addView(TextView(themedContext).apply {
                    text = if (track.isRaw) "ASSET • ${formatMs(track.duration)}"
                    else "STORAGE • ${formatMs(track.duration)}"
                    textSize = themedContext.sspSp(SspR.dimen._9ssp)
                    setTextColor(Color.parseColor(COLOR_TEXT_DIM))
                })

                itemRow.addView(iconBox)
                itemRow.addView(textStack)
                itemRow.setOnClickListener {
                    MusicPlayerManager.play(themedContext, track)
                    renderList(searchField.text.toString())
                }

                listLayout.addView(itemRow)

                listLayout.addView(View(themedContext).apply {
                    layoutParams = LinearLayout.LayoutParams(-1, 1).apply {
                        setMargins(themedContext.sdp(SdpR.dimen._16sdp), 0, themedContext.sdp(SdpR.dimen._16sdp), 0)
                    }
                    setBackgroundColor(Color.parseColor("#156C63FF"))
                })
            }
        }

        searchField.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { renderList(s.toString()) }
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
        renderList("")
    }

    @SuppressLint("UseKtx", "ClickableViewAccessibility")
    private fun buildElegantPlayerCard(context: Context, initialVolume: Float): LinearLayout {
        return LinearLayout(context).apply {
            id = 100
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor(COLOR_BG_MEDIUM))
                cornerRadius = context.sdp(SdpR.dimen._16sdp).toFloat()
                setStroke(context.sdp(SdpR.dimen._1sdp), Color.parseColor(COLOR_ACCENT))
            }
            elevation = context.sdp(SdpR.dimen._8sdp).toFloat()

            val topRow = LinearLayout(context).apply {
                setPadding(
                    context.sdp(SdpR.dimen._16sdp),
                    context.sdp(SdpR.dimen._12sdp),
                    context.sdp(SdpR.dimen._16sdp),
                    0
                )
                gravity = Gravity.CENTER_VERTICAL
            }

            val titleTv = TextView(context).apply {
                id = 101
                textSize = context.sspSp(SspR.dimen._12ssp)
                setTextColor(Color.parseColor(COLOR_TEXT_BRIGHT))
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }

            val btnSize = context.sdp(SdpR.dimen._36sdp)
            val btnPad  = context.sdp(SdpR.dimen._6sdp)
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
                    context.sdp(SdpR.dimen._8sdp),
                    context.sdp(SdpR.dimen._12sdp),
                    context.sdp(SdpR.dimen._12sdp)
                )
            }

            val musicSeekBar = MusicSeekBar(context).apply {
                id = 103
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    context.sdp(SdpR.dimen._30sdp),
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
                val title = cursor.getString(0) ?: "Unknown"
                val dur   = cursor.getLong(1)
                val id    = cursor.getLong(2)
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
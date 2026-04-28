package sound.recorder.widget.music

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.intuit.sdp.R as SdpR
import com.intuit.ssp.R as SspR
import sound.recorder.widget.R
import java.util.concurrent.TimeUnit

object MusicListDialogHelper {

    private const val PREFS_NAME    = "music_player_prefs"
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

    val isMusicPlaying: Boolean  get() = MusicPlayerManager.isPlaying
    val isMusicPaused: Boolean   get() = MusicPlayerManager.isPaused
    val isMusicActive: Boolean   get() = MusicPlayerManager.isPlaying || MusicPlayerManager.isPaused
    val currentTrack: MusicPlayerManager.MusicTrack? get() = MusicPlayerManager.getCurrentTrack()

    private val rawTracks = mutableListOf<MusicPlayerManager.MusicTrack>()

    private const val COLOR_BG_DARK     = "#0A0E1A"
    private const val COLOR_BG_MEDIUM   = "#1A1F3A"
    private const val COLOR_BG_LIGHT    = "#252B47"
    private const val COLOR_ACCENT      = "#6C63FF"
    private const val COLOR_TEXT_BRIGHT = "#FFFFFF"
    private const val COLOR_TEXT_DIM    = "#8B93B8"

    // ─── Helper shorthand untuk sdp/ssp ───
    private fun Context.sdp(id: Int)  = resources.getDimensionPixelSize(id)
    private fun Context.ssp(id: Int)  = resources.getDimension(id)
    private fun Context.sspSp(id: Int) = resources.getDimension(id) / resources.displayMetrics.scaledDensity

    @SuppressLint("UseKtx")
    private fun saveMusicVolume(context: Context, volume: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putFloat(KEY_MUSIC_VOLUME, volume).apply()
    }

    private fun loadMusicVolume(context: Context): Float {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_MUSIC_VOLUME, DEFAULT_VOLUME)
    }

    fun registerRawTracks(tracks: List<MusicPlayerManager.MusicTrack>) {
        rawTracks.clear()
        rawTracks.addAll(tracks)
    }

    @SuppressLint("UseKtx")
    fun show(context: Context) {
        val savedVolume = loadMusicVolume(context)
        MusicPlayerManager.setVolume(savedVolume, savedVolume)

        val rootContainer = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            setBackgroundColor(Color.parseColor(COLOR_BG_DARK))
        }

        val mainLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(-1, -1)
        }

        // ─── HEADER ───
        val headerContainer = RelativeLayout(context).apply {
            val padH = context.sdp(SdpR.dimen._16sdp)
            val padT = context.sdp(SdpR.dimen._16sdp)
            val padB = context.sdp(SdpR.dimen._8sdp)
            setPadding(padH, padT, padH, padB)
        }

        val titleView = TextView(context).apply {
            text = context.getString(R.string.list_music).uppercase()
            setTextColor(Color.parseColor(COLOR_ACCENT))
            textSize = context.sspSp(SspR.dimen._14ssp)
            letterSpacing = 0.1f
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        }

        val closeBtnSize = context.sdp(SdpR.dimen._32sdp)
        val closeBtn = FrameLayout(context).apply {
            layoutParams = RelativeLayout.LayoutParams(closeBtnSize, closeBtnSize).apply {
                addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
                addRule(RelativeLayout.CENTER_VERTICAL)
            }
            background = RippleDrawable(
                ColorStateList.valueOf(Color.parseColor("#20FFFFFF")), null, null
            )
            addView(TextView(context).apply {
                text = "✕"
                setTextColor(Color.parseColor(COLOR_TEXT_DIM))
                textSize = context.sspSp(SspR.dimen._14ssp)
                gravity = Gravity.CENTER
            })
        }

        headerContainer.addView(titleView)
        headerContainer.addView(closeBtn)
        mainLayout.addView(headerContainer)

        // ─── SEARCH BAR ───
        val searchPadH = context.sdp(SdpR.dimen._16sdp)
        val searchPadV = context.sdp(SdpR.dimen._8sdp)
        val searchContainer = FrameLayout(context).apply {
            setPadding(searchPadH, searchPadV, searchPadH, searchPadV)
        }

        val searchInnerPadH = context.sdp(SdpR.dimen._12sdp)
        val searchInnerPadV = context.sdp(SdpR.dimen._10sdp)
        val searchCorner    = context.sdp(SdpR.dimen._20sdp).toFloat()
        val searchField = EditText(context).apply {
            hint = context.getString(R.string.search_song_accompaniment)
            setHintTextColor(Color.parseColor(COLOR_TEXT_DIM))
            setTextColor(Color.WHITE)
            textSize = context.sspSp(SspR.dimen._12ssp)
            setPadding(searchInnerPadH, searchInnerPadV, searchInnerPadH, searchInnerPadV)
            background = GradientDrawable().apply {
                setColor(Color.parseColor(COLOR_BG_MEDIUM))
                setStroke(
                    context.sdp(SdpR.dimen._1sdp),
                    Color.parseColor(COLOR_BG_LIGHT)
                )
                cornerRadius = searchCorner
            }
        }
        searchContainer.addView(searchField)
        mainLayout.addView(searchContainer)

        // ─── LIST ───
        val scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        val listLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val bottomPad = context.sdp(SdpR.dimen._100sdp)
            setPadding(0, 0, 0, bottomPad)
        }
        scrollView.addView(listLayout)
        mainLayout.addView(scrollView)
        rootContainer.addView(mainLayout)

        // ─── FLOATING PLAYER CARD ───
        val playerCard = buildElegantPlayerCard(context, savedVolume)
        val playerMargin = context.sdp(SdpR.dimen._12sdp)
        val playerBottom = context.sdp(SdpR.dimen._16sdp)
        val playerParams = FrameLayout.LayoutParams(-1, -2).apply {
            gravity = Gravity.BOTTOM
            setMargins(playerMargin, 0, playerMargin, playerBottom)
        }
        rootContainer.addView(playerCard, playerParams)

        val dialog = AlertDialog.Builder(
            context, android.R.style.Theme_Black_NoTitleBar_Fullscreen
        ).setView(rootContainer).create()

        closeBtn.setOnClickListener { dialog.dismiss() }

        ViewCompat.setOnApplyWindowInsetsListener(rootContainer) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                systemBars.left, systemBars.top,
                systemBars.right, systemBars.bottom
            )
            insets
        }

        // ─── PLAYER LISTENER ───
        MusicPlayerManager.setListener(object : MusicPlayerManager.PlayerListener {
            override fun onPlay(track: MusicPlayerManager.MusicTrack) {
                val vol = loadMusicVolume(context)
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
                playerCard.findViewById<SeekBar>(103)?.apply {
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

        val allTracks = rawTracks + loadDeviceTracks(context)

        fun renderList(query: String) {
            listLayout.removeAllViews()
            val filtered = allTracks.filter { it.title.contains(query, true) }

            filtered.forEach { track ->
                val isPlaying = MusicPlayerManager.getCurrentTrack()?.title == track.title

                val itemPadH = context.sdp(SdpR.dimen._16sdp)
                val itemPadV = context.sdp(SdpR.dimen._10sdp)
                val itemRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(itemPadH, itemPadV, itemPadH, itemPadV)
                    gravity = Gravity.CENTER_VERTICAL
                    background = RippleDrawable(
                        ColorStateList.valueOf(Color.parseColor("#15FFFFFF")), null, null
                    )
                }

                // Icon box
                val iconBoxSize   = context.sdp(SdpR.dimen._36sdp)
                val iconBoxCorner = context.sdp(SdpR.dimen._8sdp).toFloat()
                val iconBox = FrameLayout(context).apply {
                    layoutParams = LinearLayout.LayoutParams(iconBoxSize, iconBoxSize)
                    background = GradientDrawable().apply {
                        setColor(
                            if (isPlaying) Color.parseColor(COLOR_ACCENT)
                            else Color.parseColor(COLOR_BG_MEDIUM)
                        )
                        cornerRadius = iconBoxCorner
                    }
                    addView(TextView(context).apply {
                        text = "♪"
                        setTextColor(
                            if (isPlaying) Color.WHITE
                            else Color.parseColor(COLOR_ACCENT)
                        )
                        textSize = context.sspSp(SspR.dimen._12ssp)
                        gravity = Gravity.CENTER
                    })
                }

                // Text stack
                val textPadStart = context.sdp(SdpR.dimen._10sdp)
                val textStack = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(textPadStart, 0, 0, 0)
                    layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                }

                textStack.addView(TextView(context).apply {
                    text = track.title.uppercase()
                    textSize = context.sspSp(SspR.dimen._12ssp)
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

                textStack.addView(TextView(context).apply {
                    text = if (track.isRaw) "ASSET • ${formatMs(track.duration)}"
                    else "STORAGE • ${formatMs(track.duration)}"
                    textSize = context.sspSp(SspR.dimen._9ssp)
                    setTextColor(Color.parseColor(COLOR_TEXT_DIM))
                })

                itemRow.addView(iconBox)
                itemRow.addView(textStack)
                itemRow.setOnClickListener {
                    MusicPlayerManager.play(context, track)
                    renderList(searchField.text.toString())
                }

                listLayout.addView(itemRow)

                // Divider
                val divMargin = context.sdp(SdpR.dimen._16sdp)
                listLayout.addView(View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(-1, 1).apply {
                        setMargins(divMargin, 0, divMargin, 0)
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
            updatePlayerUI(playerCard, context)
        } else {
            playerCard.visibility = View.GONE
        }

        dialog.show()
        renderList("")
    }

    @SuppressLint("UseKtx")
    private fun buildElegantPlayerCard(context: Context, initialVolume: Float): LinearLayout {
        return LinearLayout(context).apply {
            id = 100
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor(COLOR_BG_MEDIUM))
                cornerRadius = context.sdp(SdpR.dimen._16sdp).toFloat()
                setStroke(
                    context.sdp(SdpR.dimen._1sdp),
                    Color.parseColor(COLOR_ACCENT)
                )
            }
            elevation = context.sdp(SdpR.dimen._8sdp).toFloat()

            // ─── TOP ROW ───
            val topPadH = context.sdp(SdpR.dimen._16sdp)
            val topPadT = context.sdp(SdpR.dimen._12sdp)
            val topRow = LinearLayout(context).apply {
                setPadding(topPadH, topPadT, topPadH, 0)
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

            // ─── CONTROLS ROW ───
            val ctrlPadH  = context.sdp(SdpR.dimen._12sdp)
            val ctrlPadV  = context.sdp(SdpR.dimen._8sdp)
            val ctrlPadB  = context.sdp(SdpR.dimen._12sdp)
            val seekH     = context.sdp(SdpR.dimen._4sdp)
            val controlsRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(ctrlPadH, ctrlPadV, ctrlPadH, ctrlPadB)
            }

            val musicSeekBar = SeekBar(context).apply {
                id = 103
                progressTintList = ColorStateList.valueOf(Color.parseColor(COLOR_ACCENT))
                thumbTintList    = ColorStateList.valueOf(Color.parseColor(COLOR_ACCENT))
                thumb = ColorDrawable(Color.TRANSPARENT)
                layoutParams = LinearLayout.LayoutParams(0, seekH, 2.5f)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                        if (fromUser) MusicPlayerManager.seekTo(p)
                    }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                })
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

            val volBar = SeekBar(context).apply {
                id = 104
                max = 100
                progress = (initialVolume * 100).toInt()
                progressTintList = ColorStateList.valueOf(Color.parseColor(COLOR_ACCENT))
                thumbTintList    = ColorStateList.valueOf(Color.parseColor(COLOR_ACCENT))
                layoutParams = LinearLayout.LayoutParams(0, seekH, 1.2f)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                        if (fromUser) {
                            val vol = p / 100f
                            MusicPlayerManager.setVolume(vol, vol)
                            saveMusicVolume(context, vol)
                        }
                    }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                })
            }

            controlsRow.addView(musicSeekBar)
            controlsRow.addView(volIcon)
            controlsRow.addView(volBar)
            addView(controlsRow)
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
            val savedVol = loadMusicVolume(context)
            card.findViewById<SeekBar>(104)?.progress = (savedVol * 100).toInt()
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
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
import java.util.concurrent.TimeUnit
import sound.recorder.widget.R

object MusicListDialogHelper {

    private const val PREFS_NAME = "music_player_prefs"
    private const val KEY_MUSIC_VOLUME = "music_volume"
    private const val DEFAULT_VOLUME = 0.7f

    interface MusicStatusListener {
        fun onMusicPlay(track: MusicPlayerManager.MusicTrack)
        fun onMusicPause(track: MusicPlayerManager.MusicTrack?)
        fun onMusicStop()
        fun onMusicComplete()
        fun onMusicProgress(current: Int, max: Int) {}
    }

    var statusListener: MusicStatusListener? = null

    val isMusicPlaying: Boolean get() = MusicPlayerManager.isPlaying
    val isMusicPaused: Boolean get() = MusicPlayerManager.isPaused
    val isMusicActive: Boolean get() = MusicPlayerManager.isPlaying || MusicPlayerManager.isPaused
    val currentTrack: MusicPlayerManager.MusicTrack? get() = MusicPlayerManager.getCurrentTrack()

    private val rawTracks = mutableListOf<MusicPlayerManager.MusicTrack>()

    // Universal color scheme untuk semua alat musik
    private const val COLOR_BG_DARK     = "#0A0E1A"  // Navy gelap
    private const val COLOR_BG_MEDIUM   = "#1A1F3A"  // Navy medium
    private const val COLOR_BG_LIGHT    = "#252B47"  // Navy light
    private const val COLOR_ACCENT      = "#6C63FF"  // Purple-blue vibrant
    private const val COLOR_TEXT_BRIGHT = "#FFFFFF"  // Pure white
    private const val COLOR_TEXT_DIM    = "#8B93B8"  // Purple-gray muted

    @SuppressLint("UseKtx")
    private fun saveMusicVolume(context: Context, volume: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_MUSIC_VOLUME, volume)
            .apply()
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

        // Header
        val headerContainer = RelativeLayout(context).apply {
            setPadding(45, 60, 45, 20)
        }

        val titleView = TextView(context).apply {
            text = context.getString(R.string.list_music).uppercase()
            setTextColor(Color.parseColor(COLOR_ACCENT))
            textSize = 18f
            letterSpacing = 0.1f
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        }

        val closeBtn = FrameLayout(context).apply {
            val size = 90
            layoutParams = RelativeLayout.LayoutParams(size, size).apply {
                addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
            }
            background = RippleDrawable(ColorStateList.valueOf(Color.parseColor("#20FFFFFF")), null, null)
            addView(TextView(context).apply {
                text = "✕"
                setTextColor(Color.parseColor(COLOR_TEXT_DIM))
                textSize = 22f
                gravity = Gravity.CENTER
            })
        }

        headerContainer.addView(titleView)
        headerContainer.addView(closeBtn)
        mainLayout.addView(headerContainer)

        // Search Bar
        val searchContainer = FrameLayout(context).apply {
            setPadding(45, 20, 45, 40)
        }
        val searchField = EditText(context).apply {
            hint = context.getString(R.string.search_song_accompaniment)
            setHintTextColor(Color.parseColor(COLOR_TEXT_DIM))
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(40, 30, 40, 30)
            background = GradientDrawable().apply {
                setColor(Color.parseColor(COLOR_BG_MEDIUM))
                setStroke(2, Color.parseColor(COLOR_BG_LIGHT))
                cornerRadius = 20f
            }
        }
        searchContainer.addView(searchField)
        mainLayout.addView(searchContainer)

        // List Area
        val scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }

        val listLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 350)
        }
        scrollView.addView(listLayout)
        mainLayout.addView(scrollView)
        rootContainer.addView(mainLayout)

        // Floating Player Card
        val playerCard = buildElegantPlayerCard(context, savedVolume)
        val playerParams = FrameLayout.LayoutParams(-1, -2).apply {
            gravity = Gravity.BOTTOM
            setMargins(30, 0, 30, 50)
        }
        rootContainer.addView(playerCard, playerParams)

        val dialog = AlertDialog.Builder(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            .setView(rootContainer)
            .create()

        closeBtn.setOnClickListener { dialog.dismiss() }

        // Handle system insets untuk edge-to-edge display
        ViewCompat.setOnApplyWindowInsetsListener(rootContainer) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            view.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                systemBars.bottom
            )

            insets
        }

        // Logic Player & List
        MusicPlayerManager.setListener(object : MusicPlayerManager.PlayerListener {
            override fun onPlay(track: MusicPlayerManager.MusicTrack) {
                val vol = loadMusicVolume(context)
                MusicPlayerManager.setVolume(vol, vol)
                playerCard.visibility = View.VISIBLE
                playerCard.findViewById<TextView>(101)?.text = track.title
                playerCard.findViewById<ImageButton>(102)?.setImageResource(android.R.drawable.ic_media_pause)
                statusListener?.onMusicPlay(track)
            }
            override fun onPause() {
                playerCard.findViewById<ImageButton>(102)?.setImageResource(android.R.drawable.ic_media_play)
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

        val allTracks = (rawTracks + loadDeviceTracks(context))

        fun renderList(query: String) {
            listLayout.removeAllViews()
            val filtered = allTracks.filter { it.title.contains(query, true) }

            filtered.forEach { track ->
                val isPlaying = MusicPlayerManager.getCurrentTrack()?.title == track.title
                val itemRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(45, 30, 45, 30)
                    gravity = Gravity.CENTER_VERTICAL
                    background = RippleDrawable(ColorStateList.valueOf(Color.parseColor("#15FFFFFF")), null, null)
                }

                val iconBox = FrameLayout(context).apply {
                    layoutParams = LinearLayout.LayoutParams(100, 100)
                    background = GradientDrawable().apply {
                        setColor(if (isPlaying) Color.parseColor(COLOR_ACCENT) else Color.parseColor(COLOR_BG_MEDIUM))
                        cornerRadius = 20f
                    }
                    addView(TextView(context).apply {
                        text = "♪"
                        setTextColor(if (isPlaying) Color.WHITE else Color.parseColor(COLOR_ACCENT))
                        textSize = 16f
                        gravity = Gravity.CENTER
                    })
                }

                val textStack = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(35, 0, 0, 0)
                    layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                }

                textStack.addView(TextView(context).apply {
                    text = track.title.uppercase()
                    textSize = 14f
                    setTextColor(if (isPlaying) Color.parseColor(COLOR_ACCENT) else Color.parseColor(COLOR_TEXT_BRIGHT))
                    typeface = Typeface.create("sans-serif-medium", if (isPlaying) Typeface.BOLD else Typeface.NORMAL)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                })

                textStack.addView(TextView(context).apply {
                    text = if (track.isRaw) "ASSET • ${formatMs(track.duration)}" else "STORAGE • ${formatMs(track.duration)}"
                    textSize = 11f
                    setTextColor(Color.parseColor(COLOR_TEXT_DIM))
                })

                itemRow.addView(iconBox)
                itemRow.addView(textStack)
                itemRow.setOnClickListener {
                    MusicPlayerManager.play(context, track)
                    renderList(searchField.text.toString())
                }

                listLayout.addView(itemRow)
                listLayout.addView(View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(-1, 1).apply { setMargins(45, 0, 45, 0) }
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
                cornerRadius = 40f
                setStroke(2, Color.parseColor(COLOR_ACCENT))
            }
            elevation = 20f

            val topRow = LinearLayout(context).apply {
                setPadding(45, 30, 45, 0)
                gravity = Gravity.CENTER_VERTICAL
            }

            val titleTv = TextView(context).apply {
                id = 101
                textSize = 14f
                setTextColor(Color.parseColor(COLOR_TEXT_BRIGHT))
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }

            val playBtn = ImageButton(context).apply {
                id = 102
                background = null
                setColorFilter(Color.parseColor(COLOR_ACCENT))
                scaleType = ImageView.ScaleType.FIT_CENTER
                val btnSize = (50 * context.resources.displayMetrics.density).toInt()
                layoutParams = LinearLayout.LayoutParams(btnSize, btnSize)
                setPadding(10, 10, 10, 10)
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
                setPadding(25, 10, 45, 30)
            }

            val musicSeekBar = SeekBar(context).apply {
                id = 103
                progressTintList = ColorStateList.valueOf(Color.parseColor(COLOR_ACCENT))
                thumbTintList = ColorStateList.valueOf(Color.parseColor(COLOR_ACCENT))
                thumb = ColorDrawable(Color.TRANSPARENT)
                layoutParams = LinearLayout.LayoutParams(0, 80, 2.5f)
                setPadding(30, 0, 30, 0)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                        if (fromUser) MusicPlayerManager.seekTo(p)
                    }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                })
            }

            val volIcon = ImageView(context).apply {
                setImageResource(android.R.drawable.ic_lock_silent_mode_off)
                setColorFilter(Color.parseColor(COLOR_TEXT_DIM))
                layoutParams = LinearLayout.LayoutParams(35, 35).apply { setMargins(15, 0, 10, 0) }
            }

            val volBar = SeekBar(context).apply {
                id = 104
                max = 100
                progress = (initialVolume * 100).toInt()
                progressTintList = ColorStateList.valueOf(Color.parseColor(COLOR_ACCENT))
                thumbTintList = ColorStateList.valueOf(Color.parseColor(COLOR_ACCENT))
                layoutParams = LinearLayout.LayoutParams(0, 80, 1.2f)
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
        if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) return tracks

        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media._ID)
        context.contentResolver.query(uri, projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val title = cursor.getString(0) ?: "Unknown"
                val dur = cursor.getLong(1)
                val id = cursor.getLong(2)
                tracks.add(MusicPlayerManager.MusicTrack(title, dur, false, 0, Uri.withAppendedPath(uri, id.toString())))
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
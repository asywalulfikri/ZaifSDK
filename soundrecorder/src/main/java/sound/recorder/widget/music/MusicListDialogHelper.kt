package sound.recorder.widget.music

import android.Manifest
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
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import java.util.concurrent.TimeUnit
import sound.recorder.widget.R
import kotlin.collections.plus

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

    // ── Palette Tema Gradient Modern & Elegan ─────────────────────────────────
    // Background Utama: Gradasi Deep Purple ke Midnight Blue
    private val BG_GRADIENT_COLORS = intArrayOf(
        Color.parseColor("#0f0c29"),
        Color.parseColor("#302b63"),
        Color.parseColor("#24243e")
    )

    // Background Card Player: Hitam transparan (Glassmorphism effect)
    private val CARD_GRADIENT_COLORS = intArrayOf(
        Color.parseColor("#D9000000"), // 85% Black
        Color.parseColor("#991a1a2e")  // 60% Dark Blue
    )

    // Background Icon Play: Gradasi Api / Emas menyala
    private val ICON_PLAYING_GRADIENT = intArrayOf(
        Color.parseColor("#f12711"),
        Color.parseColor("#f5af19")
    )

    // Background Icon Default: Putih pudar transparan
    private val ICON_IDLE_GRADIENT = intArrayOf(
        Color.parseColor("#26FFFFFF"),
        Color.parseColor("#0DFFFFFF")
    )

    private const val COLOR_TEXT_PRIMARY   = "#FFFFFF"   // Putih Bersih
    private const val COLOR_TEXT_SECONDARY = "#A0A0B0"   // Abu-abu kebiruan terang
    private const val COLOR_ACCENT         = "#f5af19"   // Emas terang untuk highlight
    private const val COLOR_DIVIDER        = "#26FFFFFF" // Garis batas tipis

    // ── SharedPreferences helpers ─────────────────────────────────────────────

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

    fun show(context: Context) {
        val savedVolume = loadMusicVolume(context)
        MusicPlayerManager.setVolume(savedVolume, savedVolume)

        // Container paling luar dengan Gradient Dinamis
        val rootContainer = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                BG_GRADIENT_COLORS
            )
        }

        // Layout Utama (Header + Search + ScrollView)
        val mainLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(-1, -1)
        }

        // ── Header ────────────────────────────────────────────────────────────
        val headerContainer = RelativeLayout(context).apply {
            setPadding(45, 50, 45, 20)
        }

        val titleView = TextView(context).apply {
            text = context.getString(R.string.list_music).uppercase()
            setTextColor(Color.parseColor(COLOR_TEXT_PRIMARY))
            textSize = 17f
            letterSpacing = 0.1f
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        }

        val closeBtn = FrameLayout(context).apply {
            val size = 80
            layoutParams = RelativeLayout.LayoutParams(size, size).apply {
                addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
            }
            background = RippleDrawable(ColorStateList.valueOf(Color.parseColor("#33FFFFFF")), null, null)
            addView(TextView(context).apply {
                text = "✕"
                setTextColor(Color.parseColor(COLOR_TEXT_SECONDARY))
                textSize = 18f
                gravity = Gravity.CENTER
            })
        }

        headerContainer.addView(titleView)
        headerContainer.addView(closeBtn)
        mainLayout.addView(headerContainer)

        // ── Search Bar ────────────────────────────────────────────────────────
        val searchContainer = FrameLayout(context).apply {
            setPadding(45, 10, 45, 30)
        }
        val searchField = EditText(context).apply {
            hint = context.getString(R.string.search_song_accompaniment)
            setHintTextColor(Color.parseColor(COLOR_TEXT_SECONDARY))
            setTextColor(Color.parseColor(COLOR_TEXT_PRIMARY))
            textSize = 14f
            setPadding(45, 30, 45, 30)

            // Search Bar dengan efek semi-transparan
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#33000000"), Color.parseColor("#1A000000"))
            ).apply {
                setStroke(2, Color.parseColor(COLOR_DIVIDER))
                cornerRadius = 25f
            }
        }
        searchContainer.addView(searchField)
        mainLayout.addView(searchContainer)

        // ── List Area ─────────────────────────────────────────────────────────
        val scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }

        val listLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 300)
        }
        scrollView.addView(listLayout)
        mainLayout.addView(scrollView)
        rootContainer.addView(mainLayout)

        // ── Floating Player ───────────────────────────────────────────────────
        val playerCard = buildElegantPlayerCard(context, savedVolume)
        val playerParams = FrameLayout.LayoutParams(-1, -2).apply {
            gravity = Gravity.BOTTOM
            setMargins(35, 0, 35, 45)
        }
        rootContainer.addView(playerCard, playerParams)

        val dialog = AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Light_NoActionBar_Fullscreen)
            .setView(rootContainer)
            .create()

        closeBtn.setOnClickListener { dialog.dismiss() }

        if (isMusicActive) {
            playerCard.visibility = View.VISIBLE
            updatePlayerUI(playerCard, context)
        } else {
            playerCard.visibility = View.GONE
        }

        // ── Listener Setup ─────────────────────────────────────
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
                if (MusicPlayerManager.getCurrentTrack() == null) {
                    playerCard.visibility = View.GONE
                }
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
                    background = RippleDrawable(
                        ColorStateList.valueOf(Color.parseColor("#26FFFFFF")), null, null
                    )
                }

                // Kotak Icon dengan Gradient
                val iconBox = FrameLayout(context).apply {
                    layoutParams = LinearLayout.LayoutParams(95, 95)
                    background = GradientDrawable(
                        GradientDrawable.Orientation.BL_TR,
                        if (isPlaying) ICON_PLAYING_GRADIENT else ICON_IDLE_GRADIENT
                    ).apply {
                        cornerRadius = 25f
                    }
                    addView(TextView(context).apply {
                        text = "♪"
                        setTextColor(if (isPlaying) Color.WHITE else Color.parseColor(COLOR_TEXT_SECONDARY))
                        gravity = Gravity.CENTER
                        textSize = 16f
                    })
                }

                val textStack = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(40, 0, 0, 0)
                    layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                }

                textStack.addView(TextView(context).apply {
                    text = track.title.uppercase()
                    textSize = 13.5f
                    setTextColor(if (isPlaying) Color.parseColor(COLOR_ACCENT) else Color.parseColor(COLOR_TEXT_PRIMARY))
                    typeface = Typeface.create("sans-serif", if (isPlaying) Typeface.BOLD else Typeface.NORMAL)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                })

                textStack.addView(TextView(context).apply {
                    text = if (track.isRaw) "ASSET • ${formatMs(track.duration)}" else "STORAGE • ${formatMs(track.duration)}"
                    textSize = 10f
                    setTextColor(Color.parseColor(COLOR_TEXT_SECONDARY))
                    setPadding(0, 5, 0, 0)
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
                    setBackgroundColor(Color.parseColor(COLOR_DIVIDER))
                })
            }
        }

        searchField.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { renderList(s.toString()) }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })

        // Tampilkan dialog Full Screen
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            decorView.setPadding(0, 0, 0, 0)
            setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        renderList("")
    }

    private fun buildElegantPlayerCard(context: Context, initialVolume: Float): LinearLayout {
        return LinearLayout(context).apply {
            id = 100
            orientation = LinearLayout.VERTICAL

            // Efek Glassmorphism untuk Player Card
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                CARD_GRADIENT_COLORS
            ).apply {
                cornerRadius = 45f
                setStroke(2, Color.parseColor("#4DFFFFFF")) // Stroke putih tipis
            }
            elevation = 25f

            val topRow = LinearLayout(context).apply {
                setPadding(45, 30, 45, 0)
                gravity = Gravity.CENTER_VERTICAL
            }

            val titleTv = TextView(context).apply {
                id = 101
                textSize = 14f
                setTextColor(Color.parseColor(COLOR_TEXT_PRIMARY))
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }

            val playBtn = ImageButton(context).apply {
                id = 102
                background = RippleDrawable(ColorStateList.valueOf(Color.parseColor("#33FFFFFF")), null, null)
                setColorFilter(Color.parseColor(COLOR_ACCENT))
                scaleType = ImageView.ScaleType.FIT_CENTER
                val btnSize = (45 * context.resources.displayMetrics.density).toInt()
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
                setPadding(25, 5, 40, 30)
            }

            // ── SeekBar progress lagu ─────────────────────────────────────────
            val musicSeekBar = SeekBar(context).apply {
                id = 103
                progressTintList = ColorStateList.valueOf(Color.parseColor(COLOR_ACCENT))
                thumbTintList = ColorStateList.valueOf(Color.parseColor(COLOR_ACCENT))
                thumb = ColorDrawable(Color.TRANSPARENT)
                layoutParams = LinearLayout.LayoutParams(0, 70, 2.5f)
                setPadding(25, 0, 25, 0)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                        if (fromUser) MusicPlayerManager.seekTo(p)
                    }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                })
            }

            // ── Icon volume musik ─────────────────────────────────────────────
            val volIcon = ImageView(context).apply {
                setImageResource(android.R.drawable.ic_lock_silent_mode_off)
                setColorFilter(Color.parseColor(COLOR_TEXT_SECONDARY))
                layoutParams = LinearLayout.LayoutParams(30, 30).apply { setMargins(10, 0, 5, 0) }
            }

            // ── SeekBar volume MUSIK ──────────────────────────────────────────
            val volBar = SeekBar(context).apply {
                id = 104
                max = 100
                progress = (initialVolume * 100).toInt()
                progressTintList = ColorStateList.valueOf(Color.parseColor(COLOR_TEXT_PRIMARY))
                thumbTintList = ColorStateList.valueOf(Color.parseColor(COLOR_TEXT_PRIMARY))
                layoutParams = LinearLayout.LayoutParams(0, 70, 1.2f)
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
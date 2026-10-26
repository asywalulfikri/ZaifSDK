package sound.recorder.widget.music

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.ColorUtils
import sound.recorder.widget.R
import sound.recorder.widget.recording.RecordingDialogPalette
import sound.recorder.widget.recording.RecordingDialogStyle
import sound.recorder.widget.recording.RecordingDialogViews.applyWindow
import sound.recorder.widget.recording.RecordingDialogViews.body
import sound.recorder.widget.recording.RecordingDialogViews.circle
import sound.recorder.widget.recording.RecordingDialogViews.dialogRoot
import sound.recorder.widget.recording.RecordingDialogViews.footer
import sound.recorder.widget.recording.RecordingDialogViews.icon
import sound.recorder.widget.recording.RecordingDialogViews.iconButton
import sound.recorder.widget.recording.RecordingDialogViews.rounded
import sound.recorder.widget.recording.RecordingDialogViews.sdp
import sound.recorder.widget.recording.RecordingDialogViews.sdpF
import sound.recorder.widget.recording.RecordingDialogViews.textButton
import sound.recorder.widget.recording.RecordingDialogViews.title
import sound.recorder.widget.recording.RecordingDialogViews.vSpace
import sound.recorder.widget.util.Constant
import java.lang.ref.WeakReference
import java.util.Locale
import com.intuit.sdp.R as SdpR

/**
 * Dialog volume: Volume Lagu (MP3 latar) + Volume Instrumen (suara pad).
 * Gaya mengikuti [RecordingDialogStyle.default] (warna aksen dari tema aplikasi).
 * Ketuk ikon speaker untuk bisu / bunyikan lagi.
 */
object VolumeDialogHelper {

    private const val DEFAULT_UNMUTE_VOLUME = 0.5f

    // Cegah dialog dobel kalau tombol volume ditekan berkali-kali.
    // WeakReference: object ini hidup selama proses, jangan menahan Activity.
    private var showingRef: WeakReference<Dialog>? = null

    private fun isContextInvalid(context: Context): Boolean {
        val activity = context as? Activity ?: return false
        return activity.isFinishing || activity.isDestroyed
    }

    /** Versi lama (dipakai host app): volume lagu disimpan di prefs player, volume instrumen lewat callback. */
    fun showVolumeDialog(
        context: Context,
        currentVolumeAudio: Float,
        onAudioVolumeChanged: (Float) -> Unit
    ) {
        val musicPrefs = context.getSharedPreferences("music_player_prefs", Context.MODE_PRIVATE)
        val sharedPrefs = context.getSharedPreferences("recordingWidget", Context.MODE_PRIVATE)
        show(
            context = context,
            musicVolume = musicPrefs.getFloat("music_volume", 0.7f),
            audioVolume = currentVolumeAudio,
            onMusicChanged = { v ->
                MusicPlayerManager.setVolume(v, v)
                musicPrefs.edit().putFloat("music_volume", v).apply()
            },
            onAudioChanged = { v ->
                onAudioVolumeChanged(v)
                sharedPrefs.edit().putFloat(Constant.KeyShared.volumeAudio, v).apply()
            }
        )
    }

    /**
     * Tampilkan dialog volume. Callback dipanggil langsung saat slider digeser / speaker diketuk,
     * pemanggil yang menentukan cara menyimpan nilainya.
     */
    @JvmStatic
    @JvmOverloads
    fun show(
        context: Context,
        musicVolume: Float,
        audioVolume: Float,
        style: RecordingDialogStyle = RecordingDialogStyle.default,
        onMusicChanged: (Float) -> Unit,
        onAudioChanged: (Float) -> Unit
    ) {
        if (isContextInvalid(context)) return
        if (showingRef?.get()?.isShowing == true) return

        val c = RecordingDialogPalette(context, style)
        val dialog = AlertDialog.Builder(context).create()
        val root = dialogRoot(context, c)

        root.addView(FrameLayout(context).apply {
            addView(title(context, c, context.getString(R.string.volume)))
            val size = context.sdp(SdpR.dimen._28sdp)
            addView(
                iconButton(context, c, R.drawable.zaif_rec_ic_close, context.getString(R.string.close)) { dialog.dismiss() },
                FrameLayout.LayoutParams(size, size, Gravity.END or Gravity.CENTER_VERTICAL)
            )
        })
        root.addView(vSpace(context, SdpR.dimen._12sdp))

        val musicSection = volumeSection(
            context, c, R.drawable.zaif_rec_ic_queue_music, context.getString(R.string.song_volume), musicVolume, onMusicChanged
        )
        val audioSection = volumeSection(
            context, c, R.drawable.zaif_rec_ic_music, context.getString(R.string.volume_instrument), audioVolume, onAudioChanged
        )
        root.addView(musicSection)
        root.addView(vSpace(context, SdpR.dimen._8sdp))
        root.addView(audioSection)
        root.addView(vSpace(context, SdpR.dimen._8sdp))
        root.addView(body(context, c, context.getString(R.string.volume_mute_hint)).apply { textSize = 10f })

        root.addView(footer(context, textButton(context, c, context.getString(R.string.close)).apply {
            setOnClickListener { dialog.dismiss() }
        }), LinearLayout.LayoutParams(-1, -2))

        dialog.setView(root)
        try {
            dialog.show()
            showingRef = WeakReference(dialog)
            applyWindow(dialog, context, 0.88, 0.50)
            animateIn(context, musicSection, 60L)
            animateIn(context, audioSection, 140L)
        } catch (_: Exception) {
            // Activity di-destroy tepat sebelum dialog tampil — abaikan daripada crash.
        }
    }

    /** Kartu masuk dari bawah sambil memudar, bergiliran. */
    private fun animateIn(context: Context, view: View, delay: Long) {
        view.alpha = 0f
        view.translationY = context.sdpF(SdpR.dimen._16sdp)
        view.animate().alpha(1f).translationY(0f)
            .setStartDelay(delay).setDuration(320L)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    /** Efek "pop" kecil: membesar sebentar lalu kembali. */
    private fun pop(view: View, scale: Float = 1.18f) {
        view.animate().cancel()
        view.scaleX = scale
        view.scaleY = scale
        view.animate().scaleX(1f).scaleY(1f).setDuration(260L)
            .setInterpolator(OvershootInterpolator(3f))
            .start()
    }

    /** Satu bagian volume: ikon + judul + persen, lalu tombol speaker + slider. */
    private fun volumeSection(
        context: Context,
        c: RecordingDialogPalette,
        iconRes: Int,
        label: String,
        initial: Float,
        onChanged: (Float) -> Unit
    ): LinearLayout {
        val section = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(context, c.surfaceAlt, SdpR.dimen._10sdp)
            val p = context.sdp(SdpR.dimen._10sdp)
            setPadding(p, p, p, context.sdp(SdpR.dimen._4sdp))
        }

        val percentTv = TextView(context).apply {
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
        }
        val tileSize = context.sdp(SdpR.dimen._26sdp)
        val tileIconSize = context.sdp(SdpR.dimen._14sdp)
        val accentLight = ColorUtils.blendARGB(c.accent, Color.WHITE, 0.35f)
        val tileBg = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(accentLight, c.accent)).apply {
            cornerRadius = context.sdpF(SdpR.dimen._8sdp)
        }
        val tileIcon = icon(context, iconRes, c.onAccent)
        val tile = FrameLayout(context).apply {
            background = tileBg
            addView(tileIcon, FrameLayout.LayoutParams(tileIconSize, tileIconSize, Gravity.CENTER))
        }
        val eqBars = EqualizerBarsView(context).apply {
            barColor = c.accent
            mutedColor = c.outline
        }
        section.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(tile, LinearLayout.LayoutParams(tileSize, tileSize))
            addView(TextView(context).apply {
                text = label
                setTextColor(c.text)
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(context.sdp(SdpR.dimen._8sdp), 0, 0, 0)
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(eqBars, LinearLayout.LayoutParams(
                context.sdp(SdpR.dimen._20sdp), context.sdp(SdpR.dimen._14sdp)
            ).apply { marginEnd = context.sdp(SdpR.dimen._8sdp) })
            addView(percentTv)
        })

        val seekBar = SmoothSeekBar(context).apply {
            max = 100
            progress = (initial.coerceIn(0f, 1f) * 100).toInt()
            progressColor = c.accent
            progressStartColor = accentLight
            trackColor = c.outline
            thumbRingColor = c.accent
            glowOnDrag = true
        }
        val speakerIcon = ImageView(context)
        val speakerSize = context.sdp(SdpR.dimen._32sdp)
        val speakerBtn = FrameLayout(context).apply {
            background = RippleDrawable(ColorStateList.valueOf(c.ripple), null, circle(Color.WHITE))
            val s = context.sdp(SdpR.dimen._18sdp)
            addView(speakerIcon, FrameLayout.LayoutParams(s, s, Gravity.CENTER))
        }

        var lastAudible = if (initial > 0f) initial else DEFAULT_UNMUTE_VOLUME
        fun render(progress: Int) {
            val muted = progress == 0
            percentTv.text = String.format(Locale.getDefault(), "%d%%", progress)
            percentTv.setTextColor(if (muted) c.error else c.accent)
            speakerIcon.setImageResource(
                when {
                    muted -> R.drawable.zaif_rec_ic_volume_off
                    progress < 50 -> R.drawable.zaif_rec_ic_volume_down
                    else -> R.drawable.zaif_rec_ic_volume_up
                }
            )
            speakerIcon.imageTintList = ColorStateList.valueOf(if (muted) c.error else c.textSecondary)
            speakerBtn.contentDescription = "$label ${percentTv.text}"
            eqBars.level = progress / 100f
            if (muted) tileBg.colors = intArrayOf(c.outline, c.outline)
            else tileBg.colors = intArrayOf(accentLight, c.accent)
            tileIcon.imageTintList = ColorStateList.valueOf(if (muted) c.textSecondary else c.onAccent)
        }
        render(seekBar.progress)

        seekBar.listener = object : SmoothSeekBar.OnProgressChangeListener {
            override fun onProgressChanged(progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val v = progress / 100f
                if (v > 0f) lastAudible = v
                render(progress)
                eqBars.kick()
                onChanged(v)
            }
            override fun onStartTrackingTouch() { eqBars.kick() }
            override fun onStopTrackingTouch() {}
        }
        speakerBtn.setOnClickListener {
            // Bisu ↔ kembali ke volume terakhir
            val target = if (seekBar.progress > 0) 0f else lastAudible
            seekBar.progress = (target * 100).toInt()
            render(seekBar.progress)
            pop(speakerBtn)
            pop(percentTv, 1.25f)
            pop(tile, 1.12f)
            onChanged(target)
        }

        section.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(speakerBtn, LinearLayout.LayoutParams(speakerSize, speakerSize).apply {
                marginStart = -context.sdp(SdpR.dimen._4sdp)
            })
            addView(seekBar, LinearLayout.LayoutParams(0, -2, 1f))
        })
        return section
    }
}

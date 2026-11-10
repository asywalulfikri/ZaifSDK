package sound.recorder.widget.tutorial

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import sound.recorder.widget.R
import sound.recorder.widget.recording.database.RecordingEntity
import sound.recorder.widget.recording.export.RecordingVideoOption

/**
 * Pilihan VIDEO di dialog export untuk layar host yang BUKAN panel instrumen (mis. Gamelan Studio).
 *
 * Panel instrumen sudah mengurus ini sendiri lewat `controlPanel.videoExport`. Layar lain yang
 * membuat [RecordingEntity] sendiri memakai fungsi ini supaya aturannya sama persis: jatah gratis per
 * hari, iklan / koin / beli premium (`ZaifSDKBuilder.videoAdWithPurchase`), dan watermark.
 *
 * ```
 * RecordingExportDialog.show(context, rec, onRequestReward = reward,
 *     video = HostRecordingVideo.option(context, videoExport, isPremium, rec,
 *         onRequestReward = reward, play = { r, started, done -> ... }, stopPlayback = { ... }))
 * ```
 */
object HostRecordingVideo {
    private const val START_DELAY_MS = 400L

    // Hasil video diproses di luar layar host; tidak boleh ikut batal saat host ditutup.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * @param config sama seperti `controlPanel.videoExport`; `captureView` = layar yang direkam.
     * @param play putar [rec] di layar host. Panggil `onStarted` saat bunyi benar-benar mulai dan
     *   `onDone` setelah selesai (termasuk ekor suara).
     * @param stopPlayback hentikan putaran bila video dibatalkan.
     * @return null bila pilihan VIDEO tidak boleh tampil (isVideo mati, config null, Android < 6).
     */
    @JvmStatic
    @JvmOverloads
    fun option(
        context: Context,
        config: TutorialVideoExport?,
        isPremium: Boolean,
        rec: RecordingEntity,
        onPurchased: () -> Unit = {},
        onRequestReward: ((onRewarded: () -> Unit) -> Unit)? = null,
        play: (rec: RecordingEntity, onStarted: () -> Unit, onDone: () -> Unit) -> Unit,
        stopPlayback: () -> Unit
    ): RecordingVideoOption? {
        if (config == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
        val app = context.applicationContext
        return RecordingVideoExport.option(
            context, config, isPremium, rec.name, onPurchased, onRequestReward
        ) { style ->
            RecordingVideoRun.prepare(config)
            // Beri waktu dialog menutup agar layar terlihat sejak frame pertama.
            Handler(Looper.getMainLooper()).postDelayed({
                val run = RecordingVideoRun.start(context, config, rec, style) {
                    Toast.makeText(app, R.string.export_cancelled, Toast.LENGTH_SHORT).show()
                    stopPlayback()
                }
                if (run == null) {
                    Toast.makeText(app, R.string.tutorial_video_failed, Toast.LENGTH_SHORT).show()
                    return@postDelayed
                }
                Toast.makeText(app, R.string.tutorial_video_recording, Toast.LENGTH_SHORT).show()
                play(
                    rec,
                    { run.markStarted() },
                    { run.complete(scope) { message -> Toast.makeText(app, message, Toast.LENGTH_SHORT).show() } }
                )
            }, START_DELAY_MS)
        }
    }
}

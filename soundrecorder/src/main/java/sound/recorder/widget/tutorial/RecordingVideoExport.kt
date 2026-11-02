package sound.recorder.widget.tutorial

import android.view.View
import sound.recorder.widget.listener.MyAdsListener

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import sound.recorder.widget.R
import sound.recorder.widget.builder.ZaifSDKBuilder
import sound.recorder.widget.recording.database.RecordedTap
import sound.recorder.widget.recording.database.RecordingEntity
import sound.recorder.widget.recording.RecordedMusicJson
import sound.recorder.widget.recording.export.RecordingExporter
import sound.recorder.widget.recording.export.RecordingVideoOption
import sound.recorder.widget.rewards.PremiumPass

/** Aturan pilihan VIDEO di dialog export, dipakai export tutorial dan export rekaman. */
internal object RecordingVideoExport {

    /**
     * Null = pilihan VIDEO tidak tampil: isVideo mati, host belum memasang config, atau Android < 6.
     *
     * @param onPurchased dipanggil setelah pembelian berhasil, sebelum dialog custom video dibuka.
     */
    fun option(
        context: Context,
        config: TutorialVideoExport?,
        isPremium: Boolean,
        title: String,
        onPurchased: () -> Unit = {},
        start: (TutorialVideoStyle) -> Unit
    ): RecordingVideoOption? {
        if (config == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
        val sdkConfig = ZaifSDKBuilder.load(context) ?: return null
        if (!sdkConfig.isVideo) return null
        // Tidak boleh kosong: versi gratis selalu punya watermark, walau host lupa mengisi nama app.
        val appLabel = config.watermark?.takeIf { it.isNotBlank() }
            ?: sdkConfig.appName.ifBlank { context.applicationInfo.loadLabel(context.packageManager).toString() }
        val freeWatermark = context.getString(R.string.tutorial_video_free_watermark, appLabel)
        // isPremium dari host bisa ikut menghitung Premium Pass, yang didapat dari koin.
        val unlimited = config.hasPurchased?.invoke() ?: (isPremium && !PremiumPass.isActive(context))
        val canUse = unlimited || TutorialVideoQuota.hasFree(context)
        return RecordingVideoOption(canUse) { closeExport ->
            if (canUse) {
                closeExport()
                TutorialVideoOptionsDialog.show(context, title, if (unlimited) null else freeWatermark, start)
            } else {
                TutorialVideoPremiumDialog.show(context, config.onPremiumPurchased) {
                    closeExport()
                    onPurchased()
                    TutorialVideoOptionsDialog.show(context, title, null, start)
                }
            }
        }
    }
}

/** Satu rekaman video dari rekaman user: suara sama dengan export MP3 rekaman itu. */
@RequiresApi(Build.VERSION_CODES.M)
internal class RecordingVideoRun private constructor(
    private val context: Context,
    private val recorder: TutorialVideoRecorder,
    private val isFree: Boolean,
    private val indicator: VideoRecordingIndicator,
    private val title: String,
    /** View game saat video seluruh layar; null bila banner tidak disembunyikan. */
    private val fullScreenView: View? = null
) {
    private fun restoreBanner() {
        // View sudah lepas: user pindah layar, banner mengikuti layar yang baru.
        if (fullScreenView?.isAttachedToWindow == true) MyAdsListener.setBanner(true)
    }

    fun markStarted() = recorder.markPlaybackStart()

    fun cancel() {
        indicator.dismiss()
        recorder.cancel()
        restoreBanner()
    }

    fun complete(scope: CoroutineScope, onToast: (String) -> Unit) {
        recorder.stop()
        indicator.showSaving()
        restoreBanner()
        scope.launch {
            try {
                val file = recorder.finish()
                if (isFree) TutorialVideoQuota.consume(context)
                indicator.dismiss()
                RecordingVideoResultDialog.show(context, file, title)
            } catch (t: Throwable) {
                Log.e("TutorialVideo", "finish failed", t)
                onToast(context.getString(R.string.tutorial_video_failed))
            } finally {
                indicator.dismiss()
            }
        }
    }

    companion object {
        /** Panggil sebelum jeda mulai rekam, agar banner sudah hilang di frame pertama. */
        fun prepare(config: TutorialVideoExport?) {
            if (config?.fullScreen == true) MyAdsListener.setBanner(false)
        }

        private fun abort(config: TutorialVideoExport) {
            if (config.fullScreen) MyAdsListener.setBanner(true)
        }

        fun start(
            context: Context,
            config: TutorialVideoExport,
            rec: RecordingEntity,
            style: TutorialVideoStyle,
            onInterrupted: () -> Unit
        ): RecordingVideoRun? {
            val view = config.captureView()
            if (view == null || view.width == 0 || view.height == 0 || !view.isAttachedToWindow) {
                abort(config)
                return null
            }
            return try {
                val taps = Gson().fromJson<List<RecordedTap>>(
                    rec.eventsJson, object : TypeToken<List<RecordedTap>>() {}.type
                )
                val micPath = RecordingExporter.micPathOf(rec)
                val recorder = TutorialVideoRecorder(
                    context, if (config.fullScreen) view.rootView else view, rec.name, taps, rec.setName,
                    config.noteLabel, style,
                    micPath = micPath,
                    renderTaps = micPath == null || rec.isEarphoneRecording,
                    music = RecordedMusicJson.parse(rec.musicJson),
                    audioDurationMs = rec.durationMs.takeIf { it > 0 }
                )
                recorder.start()
                val indicator = VideoRecordingIndicator(view, rec.durationMs, onInterrupted).also {
                    it.hiddenWhileRecording = config.fullScreen
                    it.show()
                }
                RecordingVideoRun(
                    context, recorder, style.forcedWatermark != null, indicator, rec.name,
                    fullScreenView = view.takeIf { config.fullScreen }
                )
            } catch (t: Throwable) {
                Log.e("TutorialVideo", "start failed", t)
                abort(config)
                null
            }
        }
    }
}

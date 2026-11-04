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
import sound.recorder.widget.util.ZaifAnalytics

/**
 * Aturan pilihan VIDEO di dialog export, dipakai export tutorial dan export rekaman.
 *
 * Alur:
 * 1. Pilihan VIDEO tampil hanya bila `ZaifSDKBuilder.isVideo(true)` DAN host mengisi
 *    [TutorialVideoExport] (export rekaman: `controlPanel.videoExport`), Android 6+.
 * 2. Pembeli premium (`hasPurchased`) → tanpa batas, tanpa watermark. Premium Pass tidak dihitung.
 * 3. Selain itu → jatah gratis [TutorialVideoQuota.freePerDay] per hari, dengan watermark.
 *    App tanpa pembelian + isCoin: dialog custom video punya switch "hapus watermark" seharga
 *    [TutorialVideoQuota.removeWatermarkCoinCost] koin (30). Video ini TETAP memakai satu jatah gratis
 *    ([TutorialVideoStyle.usesFreeSlot]); setelah jatah habis harganya 50.
 * 4. Jatah habis → [TutorialVideoPremiumDialog]:
 *    - app TANPA pembelian (`ZaifSDKBuilder.hasPremiumPurchase(false)`) → tonton iklan ([onRequestReward])
 *      atau bayar `RecordingExporter.exportCoinCost` koin (bila isCoin); video tetap watermark.
 *      Pilihan lain: bayar `TutorialVideoQuota.noWatermarkCoinCost` koin (default 50) → tanpa watermark.
 *      Setelah dibuka dengan iklan, switch "hapus watermark" seharga 50; setelah bayar 10 koin, switch
 *      tinggal 40 (total tetap 50). Koin dikembalikan bila dialog custom ditutup atau rekaman batal/gagal;
 *    - app PUNYA pembelian (default) + `onPremiumPurchased` diisi → dialog Premium + tombol beli, TANPA iklan/koin;
 *    - app PUNYA pembelian tapi `onPremiumPurchased` null → hanya "coba lagi besok".
 *    Aturan "iklan/koin hanya untuk app tanpa pembelian" adalah keputusan produk, jangan diubah
 *    tanpa persetujuan pemilik app. Lihat docs/MISSIONS_PREMIUM_RULE.md.
 */
internal object RecordingVideoExport {

    /**
     * Null = pilihan VIDEO tidak tampil: isVideo mati, host belum memasang config, atau Android < 6.
     *
     * @param onPurchased dipanggil setelah pembelian berhasil, sebelum dialog custom video dibuka.
     * @param onRequestReward tampilkan rewarded ad lalu panggil `onRewarded`; dipakai untuk membuka
     *   video saat jatah gratis habis di app tanpa pembelian premium. Null = tanpa pilihan iklan.
     */
    fun option(
        context: Context,
        config: TutorialVideoExport?,
        isPremium: Boolean,
        title: String,
        onPurchased: () -> Unit = {},
        onRequestReward: ((onRewarded: () -> Unit) -> Unit)? = null,
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
        val hasFree = TutorialVideoQuota.hasFree(context)
        val canUse = unlimited || hasFree
        // Switch "hapus watermark pakai koin": hanya app tanpa pembelian + koin aktif.
        // Jatah gratis masih ada → removeWatermarkCoinCost (30); habis → noWatermarkCoinCost (50).
        val coinRemoveWatermark = !unlimited && sdkConfig.noPremiumPurchase && sdkConfig.isCoin
        val removeCost = when {
            !coinRemoveWatermark -> 0
            hasFree -> TutorialVideoQuota.removeWatermarkCoinCost
            else -> TutorialVideoQuota.noWatermarkCoinCost
        }
        return RecordingVideoOption(canUse) { closeExport ->
            if (canUse) {
                closeExport()
                TutorialVideoOptionsDialog.show(
                    context, title, if (unlimited) null else freeWatermark, config.captureView(), removeCost, onStart = start
                )
            } else {
                TutorialVideoPremiumDialog.show(
                    context, config.onPremiumPurchased, onRequestReward,
                    // Dibuka dengan iklan / koin: tetap versi gratis (watermark).
                    // Total tanpa watermark tetap 50: sudah bayar 10 → switch tinggal 40.
                    onUnlocked = { paid ->
                        closeExport()
                        TutorialVideoOptionsDialog.show(
                            context, title, freeWatermark, config.captureView(),
                            removeWatermarkCost = (removeCost - paid).coerceAtLeast(0),
                            prepaidCoins = paid, onStart = start
                        )
                    },
                    // Bayar koin lebih mahal: tanpa watermark, tidak memakai jatah gratis
                    onUnlockedNoWatermark = { paid ->
                        closeExport()
                        TutorialVideoOptionsDialog.show(
                            context, title, null, config.captureView(), prepaidCoins = paid, onStart = start
                        )
                    }
                ) {
                    closeExport()
                    onPurchased()
                    TutorialVideoOptionsDialog.show(context, title, null, config.captureView(), onStart = start)
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
    /** Koin yang dibayar untuk video ini; dikembalikan bila batal / gagal. */
    private val paidCoins: Int,
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
        TutorialVideoQuota.refund(context, paidCoins)
        ZaifAnalytics.exportFailed(context, ZaifAnalytics.FORMAT_VIDEO, ZaifAnalytics.SOURCE_RECORDING, ZaifAnalytics.REASON_CANCELLED)
    }

    fun complete(scope: CoroutineScope, onToast: (String) -> Unit) {
        recorder.stop()
        indicator.showSaving()
        restoreBanner()
        scope.launch {
            try {
                val file = recorder.finish()
                ZaifAnalytics.exportFinished(context, ZaifAnalytics.FORMAT_VIDEO, ZaifAnalytics.SOURCE_RECORDING)
                if (isFree) TutorialVideoQuota.consume(context)
                indicator.dismiss()
                RecordingVideoResultDialog.show(context, file, title)
            } catch (t: Throwable) {
                Log.e("TutorialVideo", "finish failed", t)
                ZaifAnalytics.exportFailed(context, ZaifAnalytics.FORMAT_VIDEO, ZaifAnalytics.SOURCE_RECORDING, t.javaClass.simpleName)
                TutorialVideoQuota.refund(context, paidCoins)
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
                TutorialVideoQuota.refund(context, style.paidCoins)
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
                ZaifAnalytics.exportStarted(context, ZaifAnalytics.FORMAT_VIDEO, ZaifAnalytics.SOURCE_RECORDING)
                val indicator = VideoRecordingIndicator(view, rec.durationMs, onInterrupted).also {
                    it.hiddenWhileRecording = config.fullScreen
                    it.show()
                }
                RecordingVideoRun(
                    context, recorder, style.usesFreeSlot, indicator, rec.name, style.paidCoins,
                    fullScreenView = view.takeIf { config.fullScreen }
                )
            } catch (t: Throwable) {
                Log.e("TutorialVideo", "start failed", t)
                ZaifAnalytics.exportFailed(context, ZaifAnalytics.FORMAT_VIDEO, ZaifAnalytics.SOURCE_RECORDING, t.javaClass.simpleName)
                abort(config)
                TutorialVideoQuota.refund(context, style.paidCoins)
                null
            }
        }
    }
}

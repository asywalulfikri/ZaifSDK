package sound.recorder.widget.tutorial

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.util.Log
import android.view.Choreographer
import android.view.Surface
import android.view.View
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sound.recorder.widget.recording.database.RecordedMusicSegment
import sound.recorder.widget.recording.database.RecordedTap
import sound.recorder.widget.recording.export.Mp3Renderer
import sound.recorder.widget.recording.export.RecordingExporter
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min

/**
 * Pengaturan export video untuk export tutorial dan export rekaman.
 *
 * @param captureView tampilan alat musik yang direkam (tanpa tombol/iklan). Null → export dibatalkan.
 * @param noteLabel label not untuk padIndex (mis. "5" atau "Sol"), ditampilkan di pojok video.
 * @param watermark nama yang tampil di watermark versi gratis. Null = memakai nama app.
 * @param onPremiumPurchased host menyimpan status premium dan mematikan iklan.
 *        Null = tombol beli tidak tampil di dialog premium.
 * @param hasPurchased true bila user membeli premium. Premium Pass tidak dihitung, karena video
 *        tanpa batas hanya untuk pembeli. Null = pembeli dianggap premium tanpa Premium Pass aktif.
 */
class TutorialVideoExport(
    val captureView: () -> View?,
    val noteLabel: (padIndex: Int) -> String = { (it + 1).toString() },
    val watermark: String? = null,
    val onPremiumPurchased: (() -> Unit)? = null,
    val hasPurchased: (() -> Boolean)? = null
)

/**
 * Merekam [view] secara real-time ke MP4 selama lagu diputar, lalu menambahkan suara yang
 * dirender offline dari not yang sama (bersih, tanpa mic).
 *
 * Alur: [start] → (host memutar lagu, [markPlaybackStart] tepat sebelum not pertama dijadwalkan)
 * → [stop] → [finish] di background menghasilkan file MP4 di cache export.
 */
@RequiresApi(Build.VERSION_CODES.M)
internal class TutorialVideoRecorder(
    private val context: Context,
    private val view: View,
    private val title: String,
    private val taps: List<RecordedTap>,
    private val setName: String,
    private val noteLabel: (Int) -> String,
    private val style: TutorialVideoStyle,
    private val micPath: String? = null,
    private val renderTaps: Boolean = true,
    private val music: List<RecordedMusicSegment> = emptyList(),
    /** Rekaman user memakai durasi aslinya; null = dihitung dari ketukan terakhir. */
    private val audioDurationMs: Long? = null
) {
    private companion object {
        const val TAG = "TutorialVideo"
        const val MAX_SIDE = 1280
        const val FPS = 30
        const val FRAME_INTERVAL_NS = 1_000_000_000L / FPS
        const val VIDEO_BITRATE = 4_000_000
        const val AUDIO_RATE = 44_100
        const val AUDIO_CHANNELS = 2
        const val AUDIO_BITRATE = 128_000
        const val TIMEOUT_US = 10_000L
    }

    private val width: Int
    private val height: Int
    private val scale: Float

    init {
        val vw = max(view.width, 1)
        val vh = max(view.height, 1)
        scale = min(1f, MAX_SIDE.toFloat() / max(vw, vh))
        // Kelipatan 16: encoder H.264 sebagian HP menolak ukuran ganjil/tidak rata.
        width = max(16, (vw * scale).toInt() / 16 * 16)
        height = max(16, (vh * scale).toInt() / 16 * 16)
    }

    private val tempVideo = File(context.cacheDir, "tutorial_video_tmp.mp4")
    private val encoder: MediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
    private val inputSurface: Surface
    private val muxer: MediaMuxer
    private val frame = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    private val frameCanvas = Canvas(frame)

    @Volatile private var running = false
    private var released = false
    @Volatile private var failure: Throwable? = null
    private var drainThread: Thread? = null
    private var lastFrameNs = 0L
    private var firstVideoPtsUs = -1L
    private var playStartNs = 0L

    // Not yang sedang tampil: tap terakhir yang sudah lewat (dicari maju, tap sudah urut).
    private val sortedTaps = taps.filter { it.metadata != RecordingExporter.OFF_METADATA }.sortedBy { it.timestamp }
    private var tapCursor = 0

    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD; textSize = height * 0.055f
    }
    private val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2B2340"); typeface = Typeface.DEFAULT_BOLD; textSize = height * 0.09f
        textAlign = Paint.Align.CENTER
    }
    private val watermarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 255, 255, 255); typeface = Typeface.DEFAULT_BOLD; textSize = height * 0.05f
        textAlign = Paint.Align.RIGHT
        setShadowLayer(height * 0.006f, 0f, height * 0.003f, Color.argb(160, 0, 0, 0))
    }
    private val centerWatermarkPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(150, 0, 0, 0) }
    private val noteBoxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = this@TutorialVideoRecorder.style.noteColor }
    private val rect = RectF()

    init {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BITRATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        inputSurface = encoder.createInputSurface()
        tempVideo.delete()
        muxer = MediaMuxer(tempVideo.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    }

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            if (frameTimeNanos - lastFrameNs >= FRAME_INTERVAL_NS - 2_000_000L) {
                lastFrameNs = frameTimeNanos
                try {
                    drawFrame()
                } catch (t: Throwable) {
                    // Mis. view memakai hardware bitmap yang tidak bisa digambar ke canvas software.
                    Log.e(TAG, "draw frame failed", t)
                    failure = t
                    running = false
                    return
                }
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    /** Mulai merekam. Dipanggil di main thread. */
    fun start() {
        encoder.start()
        running = true
        drainThread = Thread({ drainVideo() }, "TutorialVideoDrain").apply { start() }
        drawFrame()
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    /** Dipanggil tepat sebelum host menjadwalkan not pertama: titik nol suara. */
    fun markPlaybackStart() {
        playStartNs = System.nanoTime()
    }

    /** Berhenti merekam gambar. Dipanggil di main thread. */
    fun stop() {
        if (!running && drainThread == null) return
        running = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        try { encoder.signalEndOfInputStream() } catch (t: Throwable) { failure = failure ?: t }
    }

    /** Menyelesaikan video + suara. Mengembalikan file di cache export, siap disimpan atau dibagikan. */
    suspend fun finish(): File = withContext(Dispatchers.IO) {
        try {
            drainThread?.join()
            failure?.let { throw it }
            check(firstVideoPtsUs >= 0) { "no video frames" }
            val audio = encodeAudio()
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val name = title.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifEmpty { "video" }
            val out = File(dir, "${name}_${System.currentTimeMillis()}.mp4")
            try {
                muxFinal(audio, out)
            } catch (t: Throwable) {
                out.delete()
                throw t
            }
            out
        } finally {
            release()
        }
    }

    /** Membatalkan tanpa menyimpan. */
    fun cancel() {
        running = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        try { encoder.signalEndOfInputStream() } catch (_: Throwable) { }
        drainThread?.join(2_000)
        release()
    }

    // ─── Gambar ──────────────────────────────────────────────────────────────

    private fun drawFrame() {
        frameCanvas.drawColor(Color.BLACK)
        frameCanvas.save()
        frameCanvas.scale(width / view.width.toFloat(), height / view.height.toFloat())
        view.draw(frameCanvas)
        frameCanvas.restore()
        drawOverlay(frameCanvas)

        val c = inputSurface.lockHardwareCanvas()
        try {
            c.drawBitmap(frame, 0f, 0f, null)
        } finally {
            inputSurface.unlockCanvasAndPost(c)
        }
    }

    private fun drawOverlay(c: Canvas) {
        val pad = height * 0.03f

        // Judul lagu: pil gelap di kiri atas.
        if (style.showTitle) {
            val tw = titlePaint.measureText(title)
            val th = titlePaint.textSize
            rect.set(pad, pad, pad + tw + th * 1.2f, pad + th * 1.7f)
            c.drawRoundRect(rect, rect.height() / 2, rect.height() / 2, pillPaint)
            c.drawText(title, rect.left + th * 0.6f, rect.centerY() + th * 0.35f, titlePaint)
        }

        // Not yang sedang dimainkan: kotak kuning di kanan atas.
        if (style.showNote) currentNote()?.let { label ->
            val nh = notePaint.textSize
            val boxW = max(nh * 1.8f, notePaint.measureText(label) + nh)
            rect.set(width - pad - boxW, pad, width - pad, pad + nh * 1.5f)
            c.drawRoundRect(rect, nh * 0.35f, nh * 0.35f, noteBoxPaint)
            c.drawText(label, rect.centerX(), rect.centerY() + nh * 0.35f, notePaint)
        }

        if (style.showWatermark) {
            style.watermark?.takeIf { it.isNotBlank() }?.let {
                c.drawText(it, width - pad, height - pad, watermarkPaint)
            }
        }
        style.forcedWatermark?.let { drawCenterWatermark(c, it, width, height, centerWatermarkPaint) }
    }

    private fun currentNote(): String? {
        if (playStartNs == 0L) return null
        val elapsedMs = (System.nanoTime() - playStartNs) / 1_000_000
        while (tapCursor < sortedTaps.size && sortedTaps[tapCursor].timestamp <= elapsedMs) tapCursor++
        if (tapCursor == 0) return null
        return noteLabel(sortedTaps[tapCursor - 1].padIndex).ifEmpty { null }
    }

    // ─── Encode video (thread sendiri) ──────────────────────────────────────

    private fun drainVideo() {
        val info = MediaCodec.BufferInfo()
        var track = -1
        try {
            while (true) {
                val index = encoder.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        track = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                    }
                    index >= 0 -> {
                        val buf = encoder.getOutputBuffer(index)
                        val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                        if (buf != null && info.size > 0 && !isConfig && track >= 0) {
                            if (firstVideoPtsUs < 0) firstVideoPtsUs = info.presentationTimeUs
                            info.presentationTimeUs -= firstVideoPtsUs
                            buf.position(info.offset)
                            buf.limit(info.offset + info.size)
                            muxer.writeSampleData(track, buf, info)
                        }
                        encoder.releaseOutputBuffer(index, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            }
            if (track >= 0) muxer.stop()
        } catch (t: Throwable) {
            Log.e(TAG, "video drain failed", t)
            failure = failure ?: t
        }
    }

    // ─── Suara: render offline → AAC di memori ───────────────────────────────

    private class EncodedAudio(val format: MediaFormat, val samples: List<Pair<ByteArray, MediaCodec.BufferInfo>>)

    private suspend fun encodeAudio(): EncodedAudio {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, AUDIO_RATE, AUDIO_CHANNELS).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BITRATE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
        }
        val aac = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        aac.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        aac.start()
        val samples = ArrayList<Pair<ByteArray, MediaCodec.BufferInfo>>()
        var outFormat: MediaFormat? = null
        val info = MediaCodec.BufferInfo()
        var framesQueued = 0L

        fun drain(untilEos: Boolean) {
            while (true) {
                val index = aac.dequeueOutputBuffer(info, if (untilEos) TIMEOUT_US else 0L)
                if (index == MediaCodec.INFO_TRY_AGAIN_LATER) { if (untilEos) continue else return }
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) { outFormat = aac.outputFormat; continue }
                if (index < 0) continue
                val buf = aac.getOutputBuffer(index)
                val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                if (buf != null && info.size > 0 && !isConfig) {
                    val bytes = ByteArray(info.size)
                    buf.position(info.offset); buf.get(bytes)
                    samples.add(bytes to MediaCodec.BufferInfo().apply {
                        set(0, info.size, info.presentationTimeUs, info.flags)
                    })
                }
                aac.releaseOutputBuffer(index, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
            }
        }

        fun queue(bytes: ByteBuffer?, size: Int, frames: Int, eos: Boolean) {
            while (true) {
                val index = aac.dequeueInputBuffer(TIMEOUT_US)
                if (index < 0) { drain(false); continue }
                val input = aac.getInputBuffer(index)!!
                input.clear()
                if (bytes != null) input.put(bytes.array(), 0, size)
                val ptsUs = framesQueued * 1_000_000L / AUDIO_RATE
                aac.queueInputBuffer(index, 0, size, ptsUs, if (eos) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0)
                framesQueued += frames
                return
            }
        }

        try {
            val chunkFrames = 1024
            val byteBuf = ByteBuffer.allocate(chunkFrames * AUDIO_CHANNELS * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            val durationMs = audioDurationMs ?: ((taps.maxOfOrNull { it.timestamp } ?: 0L) + 1_500L)
            Mp3Renderer(context.applicationContext).renderPcm(
                setName = setName,
                taps = taps,
                micPath = micPath,
                renderTaps = renderTaps,
                resolver = RecordingExporter.resolver,
                durationMs = durationMs,
                maxPolyphony = RecordingExporter.maxPolyphony,
                music = music,
                onProgress = {}
            ) { pcm, frames ->
                var offset = 0
                while (offset < frames) {
                    val n = min(chunkFrames, frames - offset)
                    byteBuf.clear()
                    byteBuf.asShortBuffer().put(pcm, offset * AUDIO_CHANNELS, n * AUDIO_CHANNELS)
                    queue(byteBuf, n * AUDIO_CHANNELS * 2, n, eos = false)
                    drain(false)
                    offset += n
                }
            }
            queue(null, 0, 0, eos = true)
            drain(true)
        } finally {
            try { aac.stop() } catch (_: Throwable) { }
            aac.release()
        }
        return EncodedAudio(checkNotNull(outFormat) { "no audio format" }, samples)
    }

    // ─── Gabung video + suara ────────────────────────────────────────────────

    private fun muxFinal(audio: EncodedAudio, out: File) {
        val extractor = MediaExtractor()
        val mux = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            extractor.setDataSource(tempVideo.absolutePath)
            extractor.selectTrack(0)
            val videoTrack = mux.addTrack(extractor.getTrackFormat(0))
            val audioTrack = mux.addTrack(audio.format)
            mux.start()

            val buf = ByteBuffer.allocate(2 * 1024 * 1024)
            val info = MediaCodec.BufferInfo()
            while (true) {
                val size = extractor.readSampleData(buf, 0)
                if (size < 0) break
                info.set(0, size, extractor.sampleTime, extractor.sampleFlags)
                mux.writeSampleData(videoTrack, buf, info)
                extractor.advance()
            }

            // Suara mulai saat not pertama dijadwalkan, relatif terhadap frame video pertama.
            val offsetUs = max(0L, playStartNs / 1_000 - firstVideoPtsUs)
            for ((bytes, sampleInfo) in audio.samples) {
                info.set(0, bytes.size, sampleInfo.presentationTimeUs + offsetUs, sampleInfo.flags)
                mux.writeSampleData(audioTrack, ByteBuffer.wrap(bytes), info)
            }
            mux.stop()
        } finally {
            extractor.release()
            mux.release()
        }
    }

    private fun release() {
        if (released) return
        released = true
        try { encoder.stop() } catch (_: Throwable) { }
        try { encoder.release() } catch (_: Throwable) { }
        try { inputSurface.release() } catch (_: Throwable) { }
        try { muxer.release() } catch (_: Throwable) { }
        tempVideo.delete()
        frame.recycle()
    }
}

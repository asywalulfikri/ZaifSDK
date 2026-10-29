package sound.recorder.widget.recording.export

import android.content.Context
import android.content.res.Resources
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import kotlinx.coroutines.CancellationException
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.nio.ByteOrder

/** Audio hasil decode: PCM 16-bit interleaved. */
internal class DecodedPcm(val data: ShortArray, val sampleRate: Int, val channels: Int) {
    val frames: Int get() = if (channels == 0) 0 else data.size / channels
}

/**
 * Decode audio (3gp/m4a/mp3/ogg/wav) ke PCM 16-bit secara bertahap per potongan,
 * agar file panjang tidak dimuat utuh ke memori.
 * Wajib [close] setelah selesai (melepas MediaCodec & MediaExtractor).
 */
internal class PcmStream private constructor(
    private val extractor: MediaExtractor,
    private val isCancelled: () -> Boolean,
    /** Mulai decode dari posisi ini (µs); sampel sebelumnya dibuang agar posisi presisi. */
    private val startUs: Long = 0L
) : Closeable, StreamResampler.Source {

    override var sampleRate: Int = 0
        private set
    override var channels: Int = 1
        private set
    /** Durasi audio dari metadata (µs), 0 jika tidak diketahui. Hanya untuk estimasi progress. */
    var durationUs: Long = 0
        private set

    private val codec: MediaCodec
    private val info = MediaCodec.BufferInfo()
    private var isFloat = false
    private var inputDone = false
    private var outputDone = false

    init {
        var created: MediaCodec? = null
        try {
            val trackIndex = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw IOException("Tidak ada track audio")
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            if (format.containsKey(MediaFormat.KEY_DURATION)) durationUs = format.getLong(MediaFormat.KEY_DURATION)
            if (startUs > 0L) extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            created = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            created.configure(format, null, null, 0)
            created.start()
            codec = created
        } catch (e: Throwable) {
            try { created?.release() } catch (_: Exception) { }
            extractor.release()
            throw e
        }
    }

    /**
     * Potongan PCM berikutnya (interleaved, [channels] kanal), atau null jika audio habis.
     * Nilai [sampleRate]/[channels] bisa berubah setelah panggilan pertama (format output codec).
     */
    override fun read(): ShortArray? {
        var idleLoops = 0
        while (!outputDone) {
            if (isCancelled()) throw CancellationException("Decode dibatalkan")
            if (!inputDone) queueInput()

            val outIdx = codec.dequeueOutputBuffer(info, TIMEOUT_US)
            when {
                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> applyOutputFormat(codec.outputFormat)
                outIdx >= 0 -> {
                    val chunk = drainOutput(outIdx)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    if (chunk.isNotEmpty()) return chunk
                }
                else -> if (++idleLoops > MAX_IDLE_LOOPS) throw IOException("Decoder tidak merespons")
            }
        }
        return null
    }

    private fun queueInput() {
        val inIdx = codec.dequeueInputBuffer(TIMEOUT_US)
        if (inIdx < 0) return
        val buf = codec.getInputBuffer(inIdx) ?: return
        val read = extractor.readSampleData(buf, 0)
        if (read < 0) {
            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            inputDone = true
        } else {
            codec.queueInputBuffer(inIdx, 0, read, extractor.sampleTime, 0)
            extractor.advance()
        }
    }

    private fun applyOutputFormat(f: MediaFormat) {
        sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
        val encoding = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && f.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
            f.getInteger(MediaFormat.KEY_PCM_ENCODING)
        } else AudioFormat.ENCODING_PCM_16BIT
        isFloat = encoding == AudioFormat.ENCODING_PCM_FLOAT
        if (encoding != AudioFormat.ENCODING_PCM_16BIT && !isFloat) {
            throw IOException("Format PCM tidak didukung: $encoding")
        }
    }

    private fun drainOutput(outIdx: Int): ShortArray {
        try {
            val buf = codec.getOutputBuffer(outIdx) ?: return ShortArray(0)
            buf.position(info.offset)
            buf.limit(info.offset + info.size)
            buf.order(ByteOrder.nativeOrder())
            val chunk = if (isFloat) {
                val fb = buf.asFloatBuffer()
                ShortArray(fb.remaining()) { i -> (fb.get(i).coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort() }
            } else {
                val sb = buf.asShortBuffer()
                ShortArray(sb.remaining()).also { sb.get(it) }
            }
            return trimBeforeStart(chunk)
        } finally {
            codec.releaseOutputBuffer(outIdx, false)
        }
    }

    /** Setelah seek ke sync frame sebelumnya, buang sampel yang masih sebelum [startUs]. */
    private fun trimBeforeStart(chunk: ShortArray): ShortArray {
        if (startUs <= 0L || info.presentationTimeUs >= startUs || sampleRate <= 0) return chunk
        val skipFrames = (startUs - info.presentationTimeUs) * sampleRate / 1_000_000L
        val skip = (skipFrames * channels).coerceAtMost(chunk.size.toLong()).toInt()
        return if (skip == 0) chunk else chunk.copyOfRange(skip, chunk.size)
    }

    override fun close() {
        try { codec.stop() } catch (_: Exception) { }
        try { codec.release() } catch (_: Exception) { }
        try { extractor.release() } catch (_: Exception) { }
    }

    companion object Factory {
        private const val TIMEOUT_US = 10_000L
        /** ±10 detik tanpa output = codec macet, hentikan agar tidak loop selamanya. */
        private const val MAX_IDLE_LOOPS = 1_000

        fun open(path: String, isCancelled: () -> Boolean = { false }): PcmStream {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(path)
            } catch (e: Throwable) {
                extractor.release()
                throw e
            }
            return PcmStream(extractor, isCancelled)
        }

        /**
         * Lagu yang direkam (res/raw bawaan app atau content URI dari HP), mulai dari [startUs].
         * @return null jika lagu sudah tidak ada / tidak bisa dibuka (export tetap jalan tanpa lagu).
         */
        fun openMusic(context: Context, rawName: String?, uri: String?, startUs: Long, isCancelled: () -> Boolean): PcmStream? {
            val extractor = MediaExtractor()
            try {
                if (rawName != null) {
                    val id = context.resources.getIdentifier(rawName, "raw", context.packageName)
                    val afd = if (id != 0) context.resources.openRawResourceFd(id) else null
                    if (afd == null) {
                        extractor.release()
                        return null
                    }
                    afd.use { extractor.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
                } else if (uri != null) {
                    extractor.setDataSource(context, android.net.Uri.parse(uri), null)
                } else {
                    extractor.release()
                    return null
                }
            } catch (e: Throwable) {
                extractor.release()
                if (e is IOException || e is SecurityException || e is IllegalArgumentException) return null
                throw e
            }
            return PcmStream(extractor, isCancelled, startUs)
        }

        fun open(context: Context, sample: ExportSample, isCancelled: () -> Boolean = { false }): PcmStream? {
            val extractor = MediaExtractor()
            try {
                if (sample.resId == 0 && sample.assetPath == null) {
                    val uri = sample.uri ?: throw IOException("ExportSample kosong")
                    if (uri.startsWith("/")) extractor.setDataSource(uri)
                    else extractor.setDataSource(context, android.net.Uri.parse(uri), null)
                    return PcmStream(extractor, isCancelled)
                }
                val afd = if (sample.resId != 0) {
                    try { context.resources.openRawResourceFd(sample.resId) } catch (e: Resources.NotFoundException) { null }
                } else {
                    try { context.assets.openFd(sample.assetPath ?: throw IOException("ExportSample kosong")) } catch (e: java.io.FileNotFoundException) { null }
                }
                // null = resource/asset terkompresi di APK, tidak bisa dibuka sebagai fd
                if (afd == null) {
                    extractor.release()
                    return null
                }
                afd.use { extractor.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            } catch (e: Throwable) {
                extractor.release()
                throw e
            }
            return PcmStream(extractor, isCancelled)
        }
    }
}

/** Decode utuh ke memori — hanya untuk sampel pendek (suara pad). Audio panjang pakai [PcmStream]. */
internal object PcmDecoder {

    fun decodeSample(context: Context, sample: ExportSample, isCancelled: () -> Boolean = { false }): DecodedPcm {
        val stream = PcmStream.open(context, sample, isCancelled)
        if (stream != null) return stream.use { readAll(it) }

        // Resource terkompresi: salin ke file sementara dulu
        val tmp = File.createTempFile("export_sample", null, context.cacheDir)
        try {
            val input = if (sample.resId != 0) context.resources.openRawResource(sample.resId)
            else context.assets.open(sample.assetPath ?: throw IOException("ExportSample kosong"))
            input.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
            return PcmStream.open(tmp.absolutePath, isCancelled).use { readAll(it) }
        } finally {
            tmp.delete()
        }
    }

    private fun readAll(stream: PcmStream): DecodedPcm {
        var out = ShortArray(16 * 1024)
        var size = 0
        while (true) {
            val chunk = stream.read() ?: break
            if (size + chunk.size > out.size) out = out.copyOf(maxOf(out.size * 2, size + chunk.size))
            System.arraycopy(chunk, 0, out, size, chunk.size)
            size += chunk.size
        }
        return DecodedPcm(out.copyOf(size), stream.sampleRate, stream.channels)
    }
}

package sound.recorder.widget.recording.export

import android.content.Context
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import sound.recorder.widget.recording.database.RecordedMusicSegment
import sound.recorder.widget.recording.database.RecordedTap
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tanh

/** Sumber audio untuk MP3 tidak tersedia (tidak ada audio mic & host belum memasang resolver). */
class Mp3SourceUnavailableException : Exception("No audio source to render MP3")

/**
 * Merender rekaman menjadi MP3:
 * - audio mic (jika ada) di-resample ke 44.1 kHz
 * - setiap ketukan pad dimix ulang secara offline dari sampel suara host app
 * Diproses per blok agar memori tetap kecil walau rekaman panjang.
 */
internal class Mp3Renderer(private val context: Context) {

    private companion object {
        const val OUT_RATE = 44_100
        const val OUT_CHANNELS = 2
        const val BLOCK_FRAMES = 4_096
        const val RELEASE_FADE_FRAMES = OUT_RATE * 60 / 1000   // 60 ms
        const val STEAL_FADE_FRAMES = OUT_RATE * 15 / 1000     // 15 ms, suara diputus karena batas polifoni
        const val TAIL_FRAMES = OUT_RATE * 300 / 1000          // 300 ms
        const val LIMIT_THRESHOLD = 0.85f
    }

    /** Sampel sudah dikonversi ke stereo float 44.1 kHz. */
    private class StereoSample(val data: FloatArray) {
        val frames: Int get() = data.size / 2
    }

    private class Voice(
        val padIndex: Int,
        val startFrame: Long,
        val sample: StereoSample,
        val loop: Boolean,
        val gain: Float
    ) {
        var releaseFrame = Long.MAX_VALUE
        var fadeFrames = RELEASE_FADE_FRAMES
        val endFrame: Long
            get() {
                val natural = startFrame + sample.frames
                if (releaseFrame == Long.MAX_VALUE) return natural
                val released = releaseFrame + fadeFrames
                return if (loop) released else min(natural, released)
            }
    }

    /** Satu potongan lagu: decoder dibuka saat potongan mulai dan ditutup saat selesai (maks 1–2 terbuka). */
    private class MusicVoice(val seg: RecordedMusicSegment) {
        val startFrame = seg.atMs * OUT_RATE / 1000
        val endFrame = seg.endMs * OUT_RATE / 1000
        val gain = seg.volume.coerceIn(0f, 1f)
        var stream: PcmStream? = null
        var resampler: StreamResampler? = null
        var done = false

        fun close() {
            done = true
            resampler = null
            stream?.close()
            stream = null
        }
    }

    suspend fun render(
        setName: String,
        taps: List<RecordedTap>,
        micPath: String?,
        renderTaps: Boolean,
        resolver: RecordingExportResolver?,
        durationMs: Long,
        outFile: File,
        bitrateKbps: Int,
        maxPolyphony: Int,
        onProgress: (Int) -> Unit,
        music: List<RecordedMusicSegment> = emptyList()
    ) {
        val ctx = coroutineContext
        val isCancelled = { !ctx.isActive }
        val voices = if (renderTaps && resolver != null) {
            buildVoices(setName, taps, resolver, maxPolyphony, isCancelled)
        } else emptyList()
        ctx.ensureActive()

        // Audio mic dibaca bertahap (streaming) agar rekaman panjang tidak OOM
        val mic = micPath?.let { PcmStream.open(it, isCancelled) }
        val musicVoices = music.sortedBy { it.atMs }.map { MusicVoice(it) }
        try {
            if (mic == null && voices.isEmpty() && musicVoices.isEmpty()) throw Mp3SourceUnavailableException()
            val micResampler = mic?.let { StreamResampler(OUT_RATE, it) }

            // Total hanya estimasi untuk progress; render tetap jalan sampai audio mic benar-benar habis
            val micFrames = mic?.durationUs?.let { it * OUT_RATE / 1_000_000 } ?: 0L
            val voicesEnd = voices.maxOfOrNull { it.endFrame }?.plus(TAIL_FRAMES) ?: 0L
            val musicEnd = musicVoices.maxOfOrNull { it.endFrame } ?: 0L
            val estimatedFrames = maxOf(micFrames, voicesEnd, musicEnd, msToFrame(durationMs), 1L)

            val mix = FloatArray(BLOCK_FRAMES * OUT_CHANNELS)
            val pcm = ShortArray(BLOCK_FRAMES * OUT_CHANNELS)
            val active = ArrayList<Voice>()
            var nextVoice = 0
            var lastProgress = -1

            outFile.outputStream().buffered().use { out ->
                val encoder = Mp3Encoder(OUT_RATE, OUT_CHANNELS, bitrateKbps, out)
                var finished = false
                try {
                    var blockStart = 0L
                    while (blockStart < voicesEnd || blockStart < musicEnd || micResampler?.isFinished == false) {
                        ctx.ensureActive()
                        val frames = BLOCK_FRAMES
                        val blockEnd = blockStart + frames
                        mix.fill(0f)

                        micResampler?.mixInto(mix, frames)
                        mixMusic(musicVoices, blockStart, frames, mix, isCancelled)

                        while (nextVoice < voices.size && voices[nextVoice].startFrame < blockEnd) {
                            active.add(voices[nextVoice++])
                        }
                        val it = active.iterator()
                        while (it.hasNext()) {
                            val v = it.next()
                            mixVoice(v, blockStart, frames, mix)
                            if (v.endFrame <= blockEnd) it.remove()
                        }

                        for (i in 0 until frames * OUT_CHANNELS) {
                            pcm[i] = (softLimit(mix[i]) * Short.MAX_VALUE).toInt().toShort()
                        }
                        encoder.encode(pcm, frames)

                        blockStart = blockEnd
                        // Maksimal 99 sampai selesai, karena total hanya estimasi
                        val progress = (blockStart * 100 / estimatedFrames).toInt().coerceAtMost(99)
                        if (progress != lastProgress) {
                            lastProgress = progress
                            onProgress(progress)
                        }
                    }
                    encoder.finish()
                    finished = true
                } finally {
                    if (!finished) encoder.close()
                }
            }
            onProgress(100)
        } finally {
            mic?.close()
            musicVoices.forEach { it.close() }
        }
    }

    private fun mixMusic(
        musicVoices: List<MusicVoice>,
        blockStart: Long,
        frames: Int,
        mix: FloatArray,
        isCancelled: () -> Boolean
    ) {
        val blockEnd = blockStart + frames
        for (v in musicVoices) {
            if (v.done) continue
            if (v.startFrame >= blockEnd) break // urut: potongan berikutnya belum mulai
            if (v.resampler == null) {
                // Mulai dari posisi lagu + bagian yang sudah lewat (jika render mulai di tengah potongan)
                val lateMs = (max(blockStart, v.startFrame) - v.startFrame) * 1000 / OUT_RATE
                val stream = try {
                    PcmStream.openMusic(context, v.seg.rawName, v.seg.uri, (v.seg.posMs + lateMs) * 1000, isCancelled)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.e("Mp3Renderer", "Lagu tidak bisa didecode (${v.seg.title}): ${e.message}")
                    null
                }
                if (stream == null) {
                    v.close()
                    continue
                }
                v.stream = stream
                v.resampler = StreamResampler(OUT_RATE, stream)
            }
            val from = max(blockStart, v.startFrame)
            val to = min(blockEnd, v.endFrame)
            if (to > from) v.resampler?.mixInto(mix, (from - blockStart).toInt(), (to - from).toInt(), v.gain)
            if (v.endFrame <= blockEnd || v.resampler?.isFinished == true) v.close()
        }
    }

    private fun buildVoices(
        setName: String,
        taps: List<RecordedTap>,
        resolver: RecordingExportResolver,
        maxPolyphony: Int,
        isCancelled: () -> Boolean
    ): List<Voice> {
        val cache = HashMap<String, StereoSample?>()
        val voices = ArrayList<Voice>()
        // Suara yang masih berbunyi, urut dari yang paling lama → dipakai untuk voice stealing
        val sounding = ArrayDeque<Voice>()
        for (tap in taps.sortedBy { it.timestamp }) {
            val frame = msToFrame(tap.timestamp)
            if (tap.metadata == RecordingExporter.OFF_METADATA) {
                // Lepas not terakhir pada pad yang sama
                voices.lastOrNull {
                    it.padIndex == tap.padIndex && it.releaseFrame == Long.MAX_VALUE && it.startFrame <= frame
                }?.releaseFrame = frame
                continue
            }
            val sample = resolver.resolveSample(setName, tap) ?: continue
            val stereo = cache.getOrPut(sample.cacheKey) {
                try {
                    toStereo44k(PcmDecoder.decodeSample(context, sample, isCancelled))
                } catch (e: Exception) {
                    android.util.Log.e("Mp3Renderer", "Gagal decode sampel ${sample.cacheKey}: ${e.message}")
                    null
                }
            } ?: continue
            if (stereo.frames == 0) continue

            // Batas polifoni seperti SoundPool (setMaxStreams): suara terlama diputus dengan fade singkat
            sounding.removeAll { it.endFrame <= frame }
            while (maxPolyphony > 0 && sounding.size >= maxPolyphony) {
                sounding.removeFirst().apply {
                    if (frame < releaseFrame) {
                        releaseFrame = frame
                        fadeFrames = STEAL_FADE_FRAMES
                    }
                }
            }
            val voice = Voice(tap.padIndex, frame, stereo, sample.loop, sample.gain.coerceIn(0f, 2f))
            voices.add(voice)
            sounding.addLast(voice)
        }
        return voices
    }

    private fun mixVoice(v: Voice, blockStart: Long, frames: Int, mix: FloatArray) {
        val from = max(blockStart, v.startFrame)
        val to = min(blockStart + frames, v.endFrame)
        val len = v.sample.frames
        val data = v.sample.data
        var f = from
        while (f < to) {
            val pos = f - v.startFrame
            if (!v.loop && pos >= len) break
            val idx = (pos % len).toInt() * 2
            var g = v.gain
            if (f >= v.releaseFrame) {
                g *= 1f - (f - v.releaseFrame).toFloat() / v.fadeFrames
                if (g <= 0f) break
            }
            val o = ((f - blockStart) * 2).toInt()
            mix[o] += data[idx] * g
            mix[o + 1] += data[idx + 1] * g
            f++
        }
    }

    private fun toStereo44k(pcm: DecodedPcm): StereoSample {
        val ch = pcm.channels
        val srcFrames = pcm.frames
        if (srcFrames == 0) return StereoSample(FloatArray(0))
        val outFrames = (srcFrames.toLong() * OUT_RATE / pcm.sampleRate).toInt().coerceAtLeast(1)
        val ratio = pcm.sampleRate.toDouble() / OUT_RATE
        val out = FloatArray(outFrames * 2)
        for (i in 0 until outFrames) {
            val srcPos = i * ratio
            val i0 = min(srcPos.toInt(), srcFrames - 1)
            val i1 = min(i0 + 1, srcFrames - 1)
            val t = (srcPos - i0).toFloat()
            val l = lerp(pcm.data[i0 * ch], pcm.data[i1 * ch], t)
            out[i * 2] = l
            out[i * 2 + 1] = if (ch > 1) lerp(pcm.data[i0 * ch + 1], pcm.data[i1 * ch + 1], t) else l
        }
        return StereoSample(out)
    }

    private fun lerp(a: Short, b: Short, t: Float): Float =
        (a + (b - a) * t) / Short.MAX_VALUE

    /** Banyak pad dipukul bersamaan bisa > 1.0 — tekan halus agar tidak pecah. */
    private fun softLimit(x: Float): Float {
        val ax = abs(x)
        if (ax <= LIMIT_THRESHOLD) return x
        val knee = 1f - LIMIT_THRESHOLD
        val y = LIMIT_THRESHOLD + knee * tanh((ax - LIMIT_THRESHOLD) / knee)
        return if (x < 0) -y else y
    }

    private fun msToFrame(ms: Long): Long = ms * OUT_RATE / 1000
}

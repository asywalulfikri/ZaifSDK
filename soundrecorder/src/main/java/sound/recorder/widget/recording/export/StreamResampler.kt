package sound.recorder.widget.recording.export

/**
 * Resample PCM 16-bit dari sumber streaming ke stereo float pada [outRate] (interpolasi linear),
 * lalu ditambahkan (mix) ke buffer keluaran. Hanya menyimpan satu potongan sumber di memori.
 */
internal class StreamResampler(private val outRate: Int, private val source: Source) {

    /** Sumber PCM bertahap, mis. [PcmStream]. */
    interface Source {
        val sampleRate: Int
        val channels: Int
        /** Potongan berikutnya (interleaved) atau null jika habis. */
        fun read(): ShortArray?
    }

    private var buf = ShortArray(0)
    private var bufChannels = 1
    private var bufFrames = 0
    /** Index frame sumber (absolut) dari frame pertama di [buf]. */
    private var bufStart = 0L
    private var sourceEnded = false
    private var outFrame = 0L

    /** true jika sumber sudah habis dan semua frame sudah dikeluarkan. */
    var isFinished = false
        private set

    /** Tambahkan [frames] frame stereo ke [mix] (interleaved L,R). Berhenti lebih awal jika sumber habis. */
    fun mixInto(mix: FloatArray, frames: Int) = mixInto(mix, 0, frames, 1f)

    /** Seperti [mixInto], mulai di frame [offset] dari [mix] dengan penguatan [gain]. */
    fun mixInto(mix: FloatArray, offset: Int, frames: Int, gain: Float) {
        if (isFinished) return
        if (bufFrames == 0 && !fill(0)) {
            isFinished = true
            return
        }
        for (i in 0 until frames) {
            val pos = outFrame * source.sampleRate.toDouble() / outRate
            val i0 = pos.toLong()
            if (!fill(i0)) {
                isFinished = true
                return
            }
            val i1 = if (fill(i0 + 1)) i0 + 1 else i0
            val t = (pos - i0).toFloat()
            val l = lerp(sample(i0, 0), sample(i1, 0), t)
            val r = if (bufChannels > 1) lerp(sample(i0, 1), sample(i1, 1), t) else l
            val o = (offset + i) * 2
            mix[o] += l * gain
            mix[o + 1] += r * gain
            outFrame++
        }
    }

    /** Pastikan frame [index] ada di buffer; baca potongan berikutnya bila perlu. @return false jika di luar akhir audio. */
    private fun fill(index: Long): Boolean {
        while (index >= bufStart + bufFrames) {
            if (sourceEnded) return false
            val chunk = source.read()
            if (chunk == null) {
                sourceEnded = true
                return false
            }
            val channels = source.channels.coerceAtLeast(1)
            val chunkFrames = chunk.size / channels
            if (chunkFrames == 0) continue
            // Simpan frame terakhir buffer lama untuk interpolasi di batas potongan
            if (bufFrames > 0 && channels == bufChannels) {
                val merged = ShortArray((chunkFrames + 1) * channels)
                System.arraycopy(buf, (bufFrames - 1) * channels, merged, 0, channels)
                System.arraycopy(chunk, 0, merged, channels, chunkFrames * channels)
                bufStart += bufFrames - 1
                buf = merged
                bufFrames = chunkFrames + 1
            } else {
                bufStart += bufFrames
                buf = chunk
                bufFrames = chunkFrames
            }
            bufChannels = channels
        }
        return index >= bufStart
    }

    private fun sample(index: Long, channel: Int): Short =
        buf[(index - bufStart).toInt() * bufChannels + channel]

    private fun lerp(a: Short, b: Short, t: Float): Float = (a + (b - a) * t) / Short.MAX_VALUE
}

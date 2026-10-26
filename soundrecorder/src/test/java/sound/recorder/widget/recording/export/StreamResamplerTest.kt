package sound.recorder.widget.recording.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamResamplerTest {

    /** Sumber palsu: mengirim [data] per potongan berukuran [chunkFrames]. */
    private class FakeSource(
        private val data: ShortArray,
        override val sampleRate: Int,
        override val channels: Int,
        private val chunkFrames: Int
    ) : StreamResampler.Source {
        private var pos = 0
        var reads = 0
        override fun read(): ShortArray? {
            if (pos >= data.size) return null
            reads++
            val end = minOf(data.size, pos + chunkFrames * channels)
            return data.copyOfRange(pos, end).also { pos = end }
        }
    }

    private fun collect(resampler: StreamResampler, block: Int): FloatArray {
        val out = ArrayList<Float>()
        val mix = FloatArray(block * 2)
        while (!resampler.isFinished) {
            mix.fill(0f)
            resampler.mixInto(mix, block)
            out.addAll(mix.toList())
        }
        return out.toFloatArray()
    }

    @Test
    fun upsampleInterpolatesAcrossChunkBoundaries() {
        // Mono 22050 Hz: ramp 0, 1000, 2000, ... dipotong per 3 frame
        val src = ShortArray(10) { (it * 1000).toShort() }
        val out = collect(StreamResampler(44_100, FakeSource(src, 22_050, 1, 3)), block = 4)

        // Setiap frame keluaran ke-2n = sampel n, ke-(2n+1) = rata-rata sampel n dan n+1 (juga di batas potongan)
        for (n in 0 until 9) {
            assertEquals(n * 1000f / Short.MAX_VALUE, out[(2 * n) * 2], 1e-4f)
            assertEquals((n * 1000f + 500f) / Short.MAX_VALUE, out[(2 * n + 1) * 2], 1e-4f)
            // mono → kanan = kiri
            assertEquals(out[(2 * n) * 2], out[(2 * n) * 2 + 1], 0f)
        }
    }

    @Test
    fun stereoKeepsChannelsAndSameRateIsIdentity() {
        val src = ShortArray(8) { if (it % 2 == 0) 100 else -100 }
        val out = collect(StreamResampler(44_100, FakeSource(src, 44_100, 2, 2)), block = 2)

        for (f in 0 until 4) {
            assertEquals(100f / Short.MAX_VALUE, out[f * 2], 1e-6f)
            assertEquals(-100f / Short.MAX_VALUE, out[f * 2 + 1], 1e-6f)
        }
    }

    @Test
    fun readsSourceLazilyInChunks() {
        val source = FakeSource(ShortArray(44_100), 44_100, 1, 4_096)
        val resampler = StreamResampler(44_100, source)
        resampler.mixInto(FloatArray(2 * 1_000), 1_000)

        // Hanya potongan pertama yang dibaca, bukan seluruh audio
        assertEquals(1, source.reads)
        assertTrue(!resampler.isFinished)
    }

    @Test
    fun mixIntoWithOffsetAndGainLeavesEarlierFramesUntouched() {
        // Stereo 44100 Hz (tanpa resample), 4 frame bernilai 16384 (= 0.5)
        val src = ShortArray(8) { 16384 }
        val resampler = StreamResampler(44_100, FakeSource(src, 44_100, 2, 4))
        val mix = FloatArray(6 * 2) { 0.1f }
        resampler.mixInto(mix, 2, 3, 0.5f)
        // Frame 0–1 tidak disentuh
        for (i in 0 until 4) assertEquals(0.1f, mix[i], 1e-6f)
        // Frame 2–4: 0.1 + 0.5 * 0.5
        for (i in 4 until 10) assertEquals(0.35f, mix[i], 1e-3f)
        // Frame 5 tidak disentuh (hanya 3 frame diminta)
        for (i in 10 until 12) assertEquals(0.1f, mix[i], 1e-6f)
    }
}

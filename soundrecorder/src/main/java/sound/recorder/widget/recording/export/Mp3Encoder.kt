package sound.recorder.widget.recording.export

import de.sciss.jump3r.mp3.BitStream
import de.sciss.jump3r.mp3.GainAnalysis
import de.sciss.jump3r.mp3.GetAudio
import de.sciss.jump3r.mp3.ID3Tag
import de.sciss.jump3r.mp3.Lame
import de.sciss.jump3r.mp3.LameGlobalFlags
import de.sciss.jump3r.mp3.MPEGMode
import de.sciss.jump3r.mp3.Parse
import de.sciss.jump3r.mp3.Presets
import de.sciss.jump3r.mp3.Quantize
import de.sciss.jump3r.mp3.QuantizePVT
import de.sciss.jump3r.mp3.Reservoir
import de.sciss.jump3r.mp3.Takehiro
import de.sciss.jump3r.mp3.VBRTag
import de.sciss.jump3r.mp3.Version
import de.sciss.jump3r.mpg.Common
import de.sciss.jump3r.mpg.Interface
import de.sciss.jump3r.mpg.MPGLib
import java.io.OutputStream

/**
 * Encoder MP3 (LAME pure-Java / jump3r).
 * Tidak memakai `LameEncoder` bawaan jump3r karena bergantung pada javax.sound (tidak ada di Android),
 * jadi modul-modul LAME dirangkai manual di sini.
 */
internal class Mp3Encoder(
    private val sampleRate: Int,
    private val channels: Int,
    bitrateKbps: Int,
    private val out: OutputStream
) {
    private val lame = Lame()
    private val gfp: LameGlobalFlags
    private var left = IntArray(0)
    private var right = IntArray(0)
    private var mp3Buf = ByteArray(0)
    private var closed = false

    init {
        val gaud = GetAudio()
        val ga = GainAnalysis()
        val bs = BitStream()
        val presets = Presets()
        val qupvt = QuantizePVT()
        val qu = Quantize()
        val vbr = VBRTag()
        val ver = Version()
        val id3 = ID3Tag()
        val rv = Reservoir()
        val tak = Takehiro()
        val parse = Parse()
        val mpg = MPGLib()
        val intf = Interface()
        val common = Common()

        lame.setModules(ga, bs, presets, qupvt, qu, vbr, ver, id3, mpg)
        bs.setModules(ga, mpg, ver, vbr)
        id3.setModules(bs, ver)
        presets.setModules(lame)
        qu.setModules(bs, rv, qupvt, tak)
        qupvt.setModules(tak, rv, lame.enc.psy)
        rv.setModules(bs)
        tak.setModules(qupvt)
        vbr.setModules(lame, bs, ver)
        gaud.setModules(parse, mpg)
        parse.setModules(ver, id3, presets)
        mpg.setModules(intf, common)
        intf.setModules(vbr, common)

        gfp = lame.lame_init().apply {
            num_channels = channels
            in_samplerate = sampleRate
            out_samplerate = sampleRate
            mode = if (channels == 1) MPEGMode.MONO else MPEGMode.JOINT_STEREO
            brate = bitrateKbps
            quality = 5
        }
        id3.id3tag_init(gfp)
        gfp.write_id3tag_automatic = false
        gfp.findReplayGain = false
        check(lame.lame_init_params(gfp) >= 0) { "LAME init failed" }
    }

    /** @param pcm PCM 16-bit interleaved, [frames] = jumlah sampel per channel. */
    fun encode(pcm: ShortArray, frames: Int) {
        if (frames <= 0) return
        ensureCapacity(frames)
        if (channels == 1) {
            for (i in 0 until frames) left[i] = pcm[i].toInt() shl 16
        } else {
            for (i in 0 until frames) {
                left[i] = pcm[i * 2].toInt() shl 16
                right[i] = pcm[i * 2 + 1].toInt() shl 16
            }
        }
        val written = lame.lame_encode_buffer_int(
            gfp, left, if (channels == 1) left else right, frames, mp3Buf, 0, mp3Buf.size
        )
        check(written >= 0) { "LAME encode error $written" }
        out.write(mp3Buf, 0, written)
    }

    /** Tulis sisa frame lalu lepas encoder. */
    fun finish() {
        try {
            if (mp3Buf.size < 7200) mp3Buf = ByteArray(7200)
            val written = lame.lame_encode_flush(gfp, mp3Buf, 0, mp3Buf.size)
            if (written > 0) out.write(mp3Buf, 0, written)
        } finally {
            close()
        }
    }

    /** Lepas encoder tanpa flush (mis. saat dibatalkan / error). Aman dipanggil berkali-kali. */
    fun close() {
        if (closed) return
        closed = true
        lame.lame_close(gfp)
    }

    private fun ensureCapacity(frames: Int) {
        if (left.size < frames) {
            left = IntArray(frames)
            right = IntArray(frames)
            // Ukuran aman buffer mp3 menurut dokumentasi LAME: 1.25 * n + 7200
            mp3Buf = ByteArray((1.25 * frames).toInt() + 7200)
        }
    }
}

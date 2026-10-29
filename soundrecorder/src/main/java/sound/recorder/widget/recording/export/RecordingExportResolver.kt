package sound.recorder.widget.recording.export

import androidx.annotation.Keep
import androidx.annotation.RawRes
import sound.recorder.widget.recording.database.RecordedTap

/**
 * Disediakan oleh host app agar SDK tahu suara & nada dari setiap ketukan pad.
 * SDK hanya menyimpan padIndex + metadata, sedangkan file suara ada di host app.
 *
 * Daftarkan sekali, misal di Application / Fragment instrumen:
 * `RecordingExporter.resolver = MyExportResolver()`
 */
@Keep
interface RecordingExportResolver {

    /**
     * Sampel suara untuk satu ketukan (dipakai saat render MP3).
     * Return null jika ketukan ini tidak punya suara (akan dilewati).
     * Ketukan dengan metadata "OFF" tidak dikirim ke sini — dipakai sebagai tanda lepas not.
     */
    fun resolveSample(setName: String, tap: RecordedTap): ExportSample?

    /**
     * Nomor not MIDI (0..127) untuk ketukan ini.
     * Return null untuk memakai mapping default (60 + padIndex, atau 36 + padIndex untuk perkusi).
     */
    fun resolveMidiNote(setName: String, tap: RecordedTap): Int? = null

    /** Program General MIDI (0..127), contoh: 0 = Piano, 22 = Harmonica, 73 = Flute, 11 = Vibraphone. */
    fun midiProgram(setName: String): Int = 0

    /** true jika instrumen perkusi (drum/kendang) → ditulis ke channel 10 MIDI. */
    fun isPercussion(setName: String): Boolean = false
}

/**
 * Sumber suara satu ketukan. Isi salah satu: [resId] (res/raw), [assetPath] (assets/),
 * atau [uri] (suara custom pilihan user: content:// atau path file absolut).
 * @param loop true untuk instrumen tiup/sustain: sampel diulang sampai event "OFF".
 * @param gain volume relatif 0..1.
 * @param rate kecepatan putar sampel, sama dengan `rate` di `SoundPool.play()` / engine host
 *        (1 = asli, 2 = satu oktaf lebih tinggi). Untuk nada hasil geser pitch: `2^(semitone / 12)`.
 */
@Keep
data class ExportSample(
    @RawRes val resId: Int = 0,
    val assetPath: String? = null,
    val loop: Boolean = false,
    val gain: Float = 1f,
    val rate: Float = 1f,
    val uri: String? = null
) {
    /** Rate yang aman dipakai (batas sama dengan SoundPool: 0.5–2, diperlebar untuk engine host lain). */
    internal val safeRate: Float get() = if (rate.isFinite() && rate > 0f) rate.coerceIn(0.25f, 4f) else 1f
    internal val cacheKey: String get() = when {
        resId != 0 -> "res:$resId"
        assetPath != null -> "asset:$assetPath"
        else -> "uri:$uri"
    } + "@$safeRate"
}

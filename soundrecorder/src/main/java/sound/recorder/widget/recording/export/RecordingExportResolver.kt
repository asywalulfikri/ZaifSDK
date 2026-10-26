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
 * Sumber suara satu ketukan. Isi salah satu: [resId] (res/raw) atau [assetPath] (assets/).
 * @param loop true untuk instrumen tiup/sustain: sampel diulang sampai event "OFF".
 * @param gain volume relatif 0..1.
 */
@Keep
data class ExportSample(
    @RawRes val resId: Int = 0,
    val assetPath: String? = null,
    val loop: Boolean = false,
    val gain: Float = 1f
) {
    internal val cacheKey: String get() = if (resId != 0) "res:$resId" else "asset:$assetPath"
}

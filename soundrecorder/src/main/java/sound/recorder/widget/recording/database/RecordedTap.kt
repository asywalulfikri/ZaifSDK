package sound.recorder.widget.recording.database

import androidx.annotation.Keep

/**
 * Data model untuk menyimpan informasi ketukan saat rekaman.
 * @param padIndex Indeks pad yang ditekan (0-11).
 * @param timestamp Waktu (dalam milidetik) relatif sejak tombol RECORD ditekan.
 */
@Keep
data class RecordedTap(
    val padIndex: Int,
    val timestamp: Long,
    val metadata: String? = null
)
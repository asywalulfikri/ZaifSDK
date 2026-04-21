package sound.recorder.widget.recording.database

/**
 * Data model untuk menyimpan informasi ketukan saat rekaman.
 * @param padIndex Indeks pad yang ditekan (0-11).
 * @param timestamp Waktu (dalam milidetik) relatif sejak tombol RECORD ditekan.
 */
data class RecordedTap(
    val padIndex: Int,
    val timestamp: Long,
    val metadata: String? = null
)
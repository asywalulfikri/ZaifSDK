package sound.recorder.widget.recording.database

import androidx.annotation.Keep

/**
 * Satu potongan lagu (MP3) yang berbunyi selama rekaman. Disimpan sebagai JSON di [RecordingEntity.musicJson].
 * @param atMs waktu rekaman (ms) saat lagu mulai berbunyi
 * @param endMs waktu rekaman (ms) saat lagu dijeda/dihentikan/rekaman selesai
 * @param posMs posisi di dalam lagu pada [atMs]
 * @param rawName nama resource `res/raw` lagu bawaan app (nama, bukan ID — ID berubah tiap build)
 * @param uri content URI lagu dari HP
 * @param volume volume lagu (0..1) saat direkam
 */
@Keep
data class RecordedMusicSegment(
    val atMs: Long,
    val endMs: Long,
    val posMs: Long,
    val rawName: String? = null,
    val uri: String? = null,
    val title: String? = null,
    val volume: Float = 1f
) {
    /** Kunci sumber lagu (lagu yang sama → satu player / decoder). */
    val sourceKey: String? get() = rawName?.let { "raw:$it" } ?: uri?.let { "uri:$it" }
}

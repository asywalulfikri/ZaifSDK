package sound.recorder.widget.recording

import sound.recorder.widget.recording.database.RecordedTap
import sound.recorder.widget.recording.export.RecordingExporter

/**
 * Pasangan ON/OFF untuk instrumen bernada panjang (pianika, suling, …): nada dibunyikan saat ON
 * dan dilepas saat ketukan "OFF". Perkusi tidak memakai OFF → semua fungsi di sini tidak mengubah apa pun.
 */
internal object SustainNotes {

    private const val OFF = RecordingExporter.OFF_METADATA

    fun isOff(tap: RecordedTap) = tap.metadata == OFF

    /** Rekaman instrumen sustain = punya minimal satu OFF (sama dengan deteksi di tutorial). */
    fun usesOff(taps: List<RecordedTap>) = taps.any(::isOff)

    /** Nada (ketukan ON terakhir per pad) yang masih ditahan tepat sebelum [beforeMs]. Urut waktu. */
    fun heldBefore(sortedTaps: List<RecordedTap>, beforeMs: Long): List<RecordedTap> {
        val held = LinkedHashMap<Int, RecordedTap>()
        for (t in sortedTaps) {
            if (t.timestamp >= beforeMs) break
            if (isOff(t)) held.remove(t.padIndex) else {
                held.remove(t.padIndex) // pad ditekan ulang → urutan ikut ketukan terbaru
                held[t.padIndex] = t
            }
        }
        return held.values.toList()
    }

    /** Pad yang masih ditahan setelah semua ketukan sampai [untilMs] (inklusif). */
    fun heldPadsAt(sortedTaps: List<RecordedTap>, untilMs: Long): List<Int> =
        heldBefore(sortedTaps, untilMs + 1).map { it.padIndex }

    /** Tambah OFF di [atMs] untuk nada yang belum dilepas (mis. rekam di-stop saat jari masih menahan). */
    fun closeHeld(taps: List<RecordedTap>, atMs: Long): List<RecordedTap> {
        if (!usesOff(taps)) return taps
        val sorted = taps.sortedBy { it.timestamp }
        val held = heldPadsAt(sorted, Long.MAX_VALUE - 1)
        if (held.isEmpty()) return taps
        val end = maxOf(atMs, sorted.last().timestamp)
        return sorted + held.map { RecordedTap(it, end, OFF) }
    }

    fun offFor(padIndex: Int, atMs: Long) = RecordedTap(padIndex, atMs, OFF)
}

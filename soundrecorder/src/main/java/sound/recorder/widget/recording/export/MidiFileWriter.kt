package sound.recorder.widget.recording.export

import sound.recorder.widget.recording.database.RecordedTap
import java.io.ByteArrayOutputStream

/**
 * Menulis Standard MIDI File (format 0, satu track) dari daftar ketukan.
 * Tempo tetap 120 BPM, resolusi 480 tick per ketuk → 1 ms = 0.96 tick.
 */
internal object MidiFileWriter {

    private const val PPQ = 480
    private const val TEMPO_US_PER_QUARTER = 500_000
    private const val DEFAULT_NOTE_MS = 300L
    private const val VELOCITY = 100

    private class Event(val tick: Long, val order: Int, val bytes: ByteArray)

    fun write(
        trackName: String,
        setName: String,
        taps: List<RecordedTap>,
        resolver: RecordingExportResolver?
    ): ByteArray {
        val percussion = resolver?.isPercussion(setName) ?: guessPercussion(setName)
        val channel = if (percussion) 9 else 0
        val sorted = taps.sortedBy { it.timestamp }

        // Pasangkan NOTE-ON dengan OFF (instrumen sustain) atau beri durasi default
        data class Note(val pad: Int, val note: Int, val startMs: Long, var endMs: Long)
        val notes = mutableListOf<Note>()
        val open = mutableMapOf<Int, Note>()
        for (tap in sorted) {
            if (tap.metadata == RecordingExporter.OFF_METADATA) {
                open.remove(tap.padIndex)?.let { it.endMs = tap.timestamp }
                continue
            }
            // Pad yang sama ditekan lagi → tutup not sebelumnya
            open.remove(tap.padIndex)?.let { it.endMs = minOf(it.endMs, tap.timestamp) }
            val noteNumber = (resolver?.resolveMidiNote(setName, tap)
                ?: ((if (percussion) 36 else 60) + tap.padIndex)).coerceIn(0, 127)
            val note = Note(tap.padIndex, noteNumber, tap.timestamp, tap.timestamp + DEFAULT_NOTE_MS)
            notes.add(note)
            open[tap.padIndex] = note
        }

        val events = mutableListOf<Event>()
        for (n in notes) {
            val end = n.endMs.coerceAtLeast(n.startMs + 1)
            events.add(Event(msToTick(n.startMs), 1, byteArrayOf((0x90 or channel).toByte(), n.note.toByte(), VELOCITY.toByte())))
            // order 0 → note-off ditulis sebelum note-on di tick yang sama
            events.add(Event(msToTick(end), 0, byteArrayOf((0x80 or channel).toByte(), n.note.toByte(), 0)))
        }
        events.sortWith(compareBy<Event>({ it.tick }, { it.order }))

        val track = ByteArrayOutputStream()
        // Track name
        val nameBytes = trackName.toByteArray(Charsets.UTF_8)
        track.writeVlq(0); track.write(byteArrayOf(0xFF.toByte(), 0x03))
        track.writeVlq(nameBytes.size.toLong()); track.write(nameBytes)
        // Tempo
        track.writeVlq(0)
        track.write(byteArrayOf(0xFF.toByte(), 0x51, 0x03,
            (TEMPO_US_PER_QUARTER shr 16).toByte(), (TEMPO_US_PER_QUARTER shr 8).toByte(), TEMPO_US_PER_QUARTER.toByte()))
        // Program change (channel 10 tidak butuh)
        if (!percussion) {
            val program = (resolver?.midiProgram(setName) ?: 0).coerceIn(0, 127)
            track.writeVlq(0); track.write(byteArrayOf((0xC0 or channel).toByte(), program.toByte()))
        }

        var lastTick = 0L
        for (e in events) {
            track.writeVlq(e.tick - lastTick)
            track.write(e.bytes)
            lastTick = e.tick
        }
        // End of track
        track.writeVlq(0); track.write(byteArrayOf(0xFF.toByte(), 0x2F, 0x00))

        val trackBytes = track.toByteArray()
        return ByteArrayOutputStream().apply {
            write("MThd".toByteArray(Charsets.US_ASCII))
            writeInt32(6)
            writeInt16(0)   // format 0
            writeInt16(1)   // 1 track
            writeInt16(PPQ)
            write("MTrk".toByteArray(Charsets.US_ASCII))
            writeInt32(trackBytes.size)
            write(trackBytes)
        }.toByteArray()
    }

    private fun msToTick(ms: Long): Long = ms * PPQ * 1000 / TEMPO_US_PER_QUARTER

    private fun guessPercussion(setName: String): Boolean {
        val s = setName.lowercase()
        return listOf("drum", "kendang", "dholak", "tabla", "perkusi", "percussion", "rebana", "cajon")
            .any { s.contains(it) }
    }

    private fun ByteArrayOutputStream.writeVlq(value: Long) {
        var v = value.coerceAtLeast(0)
        var buffer = v and 0x7F
        while (true) {
            v = v shr 7
            if (v == 0L) break
            buffer = (buffer shl 8) or ((v and 0x7F) or 0x80)
        }
        while (true) {
            write((buffer and 0xFF).toInt())
            if (buffer and 0x80 != 0L) buffer = buffer shr 8 else break
        }
    }

    private fun ByteArrayOutputStream.writeInt32(v: Int) {
        write(v ushr 24); write(v ushr 16); write(v ushr 8); write(v)
    }

    private fun ByteArrayOutputStream.writeInt16(v: Int) {
        write(v ushr 8); write(v)
    }
}

package sound.recorder.widget.recording.export

import org.junit.Assert.assertEquals
import org.junit.Test
import sound.recorder.widget.recording.database.RecordedTap

class MidiFileWriterTest {

    /** (tick, status, data1) dari semua event channel; memvalidasi struktur SMF sekaligus. */
    private fun parse(bytes: ByteArray): List<Triple<Long, Int, Int>> {
        var p = 0
        fun u8() = bytes[p++].toInt() and 0xFF
        fun u32() = (u8() shl 24) or (u8() shl 16) or (u8() shl 8) or u8()
        fun u16() = (u8() shl 8) or u8()
        fun vlq(): Long { var v = 0L; while (true) { val b = u8(); v = (v shl 7) or (b and 0x7F).toLong(); if (b and 0x80 == 0) return v } }

        assertEquals("MThd", String(bytes, 0, 4)); p = 4
        assertEquals(6, u32()); assertEquals(0, u16()); assertEquals(1, u16()); assertEquals(480, u16())
        assertEquals("MTrk", String(bytes, p, 4)); p += 4
        val end = u32() + p
        assertEquals(bytes.size, end)

        val events = mutableListOf<Triple<Long, Int, Int>>()
        var tick = 0L
        while (p < end) {
            tick += vlq()
            val status = u8()
            when {
                status == 0xFF -> { u8(); val len = vlq().toInt(); p += len }
                status and 0xF0 == 0xC0 -> events.add(Triple(tick, status, u8()))
                else -> { val d1 = u8(); u8(); events.add(Triple(tick, status, d1)) }
            }
        }
        assertEquals(end, p)
        return events
    }

    @Test
    fun sustainedNoteEndsAtOffEvent() {
        val taps = listOf(
            RecordedTap(2, 0, "PIANIKA"),
            RecordedTap(2, 1000, RecordingExporter.OFF_METADATA),
            RecordedTap(4, 1000, "PIANIKA")
        )
        val result = parse(MidiFileWriter.write("Test", "pianika", taps, null))

        assertEquals(
            listOf(
                Triple(0L, 0xC0, 0),            // program change
                Triple(0L, 0x90, 62),           // note on pad 2
                Triple(960L, 0x80, 62),         // OFF pada 1000 ms
                Triple(960L, 0x90, 64),
                Triple(960L + 288L, 0x80, 64)   // durasi default 300 ms
            ),
            result
        )
    }

    @Test
    fun percussionUsesChannel10AndResolverNotes() {
        val resolver = object : RecordingExportResolver {
            override fun resolveSample(setName: String, tap: RecordedTap): ExportSample? = null
            override fun resolveMidiNote(setName: String, tap: RecordedTap) = 38
            override fun isPercussion(setName: String) = true
        }
        val result = parse(MidiFileWriter.write("Drum", "drum", listOf(RecordedTap(0, 200_000)), resolver))

        assertEquals(
            listOf(Triple(192_000L, 0x99, 38), Triple(192_000L + 288L, 0x89, 38)),
            result
        )
    }

    @Test
    fun repeatedPadClosesPreviousNote() {
        val taps = listOf(RecordedTap(0, 0), RecordedTap(0, 100))
        val result = parse(MidiFileWriter.write("x", "suling", taps, null)).filter { it.second != 0xC0 }

        assertEquals(
            listOf(Triple(0L, 0x90, 60), Triple(96L, 0x80, 60), Triple(96L, 0x90, 60), Triple(384L, 0x80, 60)),
            result
        )
    }
}

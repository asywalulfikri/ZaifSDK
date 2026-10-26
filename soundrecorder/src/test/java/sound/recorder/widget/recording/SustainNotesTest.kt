package sound.recorder.widget.recording

import org.junit.Assert.assertEquals
import org.junit.Test
import sound.recorder.widget.recording.database.RecordedTap

class SustainNotesTest {

    private fun on(pad: Int, ts: Long) = RecordedTap(pad, ts, "pianika")
    private fun off(pad: Int, ts: Long) = RecordedTap(pad, ts, "OFF")
    private fun List<RecordedTap>.str() = joinToString { "${it.padIndex}@${it.timestamp}${if (it.metadata == "OFF") "-" else "+"}" }

    @Test
    fun percussionIsNeverChanged() {
        val drums = listOf(RecordedTap(0, 100, "kendang"), RecordedTap(1, 900, "kendang"), RecordedTap(0, 2_000, "kendang"))
        assertEquals(drums, SustainNotes.closeHeld(drums, 5_000))
        // Potong perkusi = filter + geser biasa, tanpa ON/OFF tambahan
        assertEquals("1@0+, 0@1100+", RecordingTrim.trimTaps(drums, 900, 2_500, 900).str())
    }

    @Test
    fun trimStartInsideHeldNoteReplaysItAtZero() {
        // Pad 3 ditahan 1000–4000, potong mulai 2000
        val taps = listOf(on(3, 1_000), off(3, 4_000), on(1, 5_000), off(1, 6_000))
        assertEquals("3@0+, 3@2000-, 1@3000+, 1@4000-", RecordingTrim.trimTaps(taps, 2_000, 7_000, 2_000).str())
    }

    @Test
    fun trimEndInsideHeldNoteAddsOff() {
        // Pad 2 ditahan 1000–5000, potong berakhir 3000
        val taps = listOf(on(2, 1_000), off(2, 5_000))
        assertEquals("2@0+, 2@2000-", RecordingTrim.trimTaps(taps, 1_000, 3_000, 1_000).str())
    }

    @Test
    fun trimBothEndsInsideSameNote() {
        val taps = listOf(on(4, 0), off(4, 10_000))
        assertEquals("4@0+, 4@4000-", RecordingTrim.trimTaps(taps, 3_000, 7_000, 3_000).str())
    }

    @Test
    fun releasedNotesBeforeStartAreNotReplayed() {
        val taps = listOf(on(1, 0), off(1, 500), on(2, 3_000), off(2, 3_500))
        assertEquals("2@0+, 2@500-", RecordingTrim.trimTaps(taps, 3_000, 4_000, 3_000).str())
    }

    @Test
    fun closeHeldAddsOffOnlyForNotesStillDown() {
        val taps = listOf(on(1, 0), off(1, 500), on(2, 1_000), on(3, 1_200))
        assertEquals("1@0+, 1@500-, 2@1000+, 3@1200+, 2@3000-, 3@3000-", SustainNotes.closeHeld(taps, 3_000).str())
    }

    @Test
    fun repressedPadCountsAsOneHeldNote() {
        val taps = listOf(on(1, 0), off(1, 100), on(1, 200), on(1, 300))
        assertEquals("1@0+, 1@100-, 1@200+, 1@300+, 1@400-", SustainNotes.closeHeld(taps, 400).str())
    }
}

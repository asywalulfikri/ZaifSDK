package sound.recorder.widget.recording

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sound.recorder.widget.recording.database.RecordedMusicSegment

class MusicRecordTrackerTest {

    private fun seg(at: Long, end: Long, pos: Long) =
        RecordedMusicSegment(atMs = at, endMs = end, posMs = pos, rawName = "lagu")

    @Test
    fun alignZeroOffsetKeepsSegments() {
        val list = listOf(seg(0, 5_000, 30_000))
        assertEquals(list, MusicRecordTracker.align(list, 0))
    }

    @Test
    fun alignShiftsLaterSegmentsAndTrimsStartBeforeMic() {
        // Mic mulai 50 ms setelah tombol rekam
        val aligned = MusicRecordTracker.align(
            listOf(seg(0, 5_000, 30_000), seg(6_000, 8_000, 35_000)), 50
        )
        // Potongan pertama: 50 ms awal dibuang → lagu maju 50 ms
        assertEquals(seg(0, 4_950, 30_050), aligned[0])
        // Potongan berikutnya hanya digeser
        assertEquals(seg(5_950, 7_950, 35_000), aligned[1])
    }

    @Test
    fun alignDropsSegmentsThatEndedBeforeMicStarted() {
        val aligned = MusicRecordTracker.align(listOf(seg(0, 40, 1_000), seg(100, 900, 2_000)), 50)
        assertEquals(listOf(seg(50, 850, 2_000)), aligned)
    }

    @Test
    fun parseValidJsonAndSkipInvalidSegments() {
        val json = """[
            {"atMs":0,"endMs":8074,"posMs":30049,"rawName":"ampar_ampar_pisang","title":"Ampar","volume":0.5},
            {"atMs":100,"endMs":100,"posMs":0,"rawName":"kosong"},
            {"atMs":0,"endMs":500,"posMs":0}
        ]"""
        val parsed = RecordedMusicJson.parse(json)
        assertEquals(1, parsed.size)
        assertEquals("ampar_ampar_pisang", parsed[0].rawName)
        assertEquals(0.5f, parsed[0].volume, 1e-6f)
    }

    @Test
    fun parseNullOrEmptyMeansNoMusic() {
        assertTrue(RecordedMusicJson.parse(null).isEmpty())
        assertTrue(RecordedMusicJson.parse("").isEmpty())
    }
}

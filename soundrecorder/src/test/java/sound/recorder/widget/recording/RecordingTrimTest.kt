package sound.recorder.widget.recording

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sound.recorder.widget.recording.database.RecordedMusicSegment
import sound.recorder.widget.recording.database.RecordedTap

class RecordingTrimTest {

    private fun tap(ts: Long, pad: Int = 0) = RecordedTap(pad, ts, "m")
    private fun seg(at: Long, end: Long, pos: Long) = RecordedMusicSegment(at, end, pos, rawName = "lagu")

    @Test
    fun trimTapsKeepsRangeAndShifts() {
        val taps = listOf(tap(500), tap(1_000), tap(2_500), tap(4_000), tap(4_001))
        val out = RecordingTrim.trimTaps(taps, startMs = 1_000, endMs = 4_000, shiftMs = 1_000)
        assertEquals(listOf(0L, 1_500L, 3_000L), out.map { it.timestamp })
    }

    @Test
    fun trimTapsShiftFromAudioFrameNeverNegative() {
        // Audio mulai di frame 1_024 ms, ketukan di 1_000 ms tetap ikut di 0
        val out = RecordingTrim.trimTaps(listOf(tap(1_000), tap(2_000)), 900, 3_000, 1_024)
        assertEquals(listOf(0L, 976L), out.map { it.timestamp })
    }

    @Test
    fun trimMusicClipsAndAdvancesSongPosition() {
        val music = listOf(seg(0, 5_000, 30_000), seg(6_000, 9_000, 40_000), seg(9_500, 12_000, 50_000))
        val out = RecordingTrim.trimMusic(music, startMs = 2_000, endMs = 8_000, shiftMs = 2_000)
        assertEquals(
            listOf(
                seg(0, 3_000, 32_000),   // terpotong di awal → lagu maju 2 dtk
                seg(4_000, 6_000, 40_000) // terpotong di akhir saja
            ),
            out
        )
    }

    @Test
    fun trimMusicOutsideRangeIsDropped() {
        assertTrue(RecordingTrim.trimMusic(listOf(seg(0, 1_000, 0)), 2_000, 3_000, 2_000).isEmpty())
    }
}

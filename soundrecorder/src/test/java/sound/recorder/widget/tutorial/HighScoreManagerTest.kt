package sound.recorder.widget.tutorial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import sound.recorder.widget.testutil.FakeContext

class HighScoreManagerTest {

    private lateinit var ctx: FakeContext

    @Before
    fun setUp() {
        ctx = FakeContext()
    }

    @Test
    fun noScoreIsZero() {
        assertEquals(0, HighScoreManager.getHighScore(ctx, "Gundul Pacul"))
    }

    @Test
    fun onlyHigherScoreIsSaved() {
        assertTrue(HighScoreManager.saveIfBetter(ctx, "Gundul Pacul", 120))
        assertFalse(HighScoreManager.saveIfBetter(ctx, "Gundul Pacul", 80))
        assertFalse("skor sama bukan rekor baru", HighScoreManager.saveIfBetter(ctx, "Gundul Pacul", 120))
        assertEquals(120, HighScoreManager.getHighScore(ctx, "Gundul Pacul"))
        assertTrue(HighScoreManager.saveIfBetter(ctx, "Gundul Pacul", 150))
        assertEquals(150, HighScoreManager.getHighScore(ctx, "Gundul Pacul"))
    }

    @Test
    fun songNameIgnoresCaseAndOuterSpaces() {
        HighScoreManager.saveIfBetter(ctx, "Gundul Pacul", 90)
        assertEquals(90, HighScoreManager.getHighScore(ctx, "  gundul pacul "))
    }

    @Test
    fun songsAreSeparate() {
        HighScoreManager.saveIfBetter(ctx, "Lagu A", 10)
        assertEquals(0, HighScoreManager.getHighScore(ctx, "Lagu B"))
    }

    @Test
    fun resetAllClearsEverySong() {
        HighScoreManager.saveIfBetter(ctx, "Lagu A", 10)
        HighScoreManager.saveIfBetter(ctx, "Lagu B", 20)
        HighScoreManager.resetAll(ctx)
        assertEquals(0, HighScoreManager.getHighScore(ctx, "Lagu A"))
        assertEquals(0, HighScoreManager.getHighScore(ctx, "Lagu B"))
    }
}

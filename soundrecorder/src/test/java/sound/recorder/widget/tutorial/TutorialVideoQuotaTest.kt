package sound.recorder.widget.tutorial

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sound.recorder.widget.testutil.FakeContext
import sound.recorder.widget.testutil.dateId

class TutorialVideoQuotaTest {

    @Test
    fun oneFreeVideoPerDay() {
        val ctx = FakeContext()
        assertTrue(TutorialVideoQuota.hasFree(ctx))
        TutorialVideoQuota.consume(ctx)
        assertFalse(TutorialVideoQuota.hasFree(ctx))
    }

    @Test
    fun yesterdaysVideoDoesNotCount() {
        val ctx = FakeContext()
        ctx.getSharedPreferences("tutorial_video_quota", 0).edit().putInt("free_day", dateId(-1)).apply()
        assertTrue(TutorialVideoQuota.hasFree(ctx))
    }
}

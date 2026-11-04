package sound.recorder.widget.tutorial

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sound.recorder.widget.testutil.FakeContext
import sound.recorder.widget.testutil.dateId

class TutorialVideoQuotaTest {

    @After
    fun tearDown() {
        TutorialVideoQuota.freePerDay = 2
    }

    @Test
    fun twoFreeVideosPerDayByDefault() {
        val ctx = FakeContext()
        assertTrue(TutorialVideoQuota.hasFree(ctx))
        TutorialVideoQuota.consume(ctx)
        assertTrue(TutorialVideoQuota.hasFree(ctx))
        TutorialVideoQuota.consume(ctx)
        assertFalse(TutorialVideoQuota.hasFree(ctx))
    }

    @Test
    fun hostCanChangeFreePerDay() {
        val ctx = FakeContext()
        TutorialVideoQuota.freePerDay = 1
        TutorialVideoQuota.consume(ctx)
        assertFalse(TutorialVideoQuota.hasFree(ctx))
        TutorialVideoQuota.freePerDay = 0
        assertFalse(TutorialVideoQuota.hasFree(FakeContext()))
    }

    @Test
    fun yesterdaysVideoDoesNotCount() {
        val ctx = FakeContext()
        ctx.getSharedPreferences("tutorial_video_quota", 0).edit()
            .putInt("free_day", dateId(-1)).putInt("free_used", 2).apply()
        assertTrue(TutorialVideoQuota.hasFree(ctx))
    }

    @Test
    fun oldVersionUsageTodayCountsAsOne() {
        // Versi lama hanya menyimpan tanggal pemakaian, tanpa hitungan
        val ctx = FakeContext()
        ctx.getSharedPreferences("tutorial_video_quota", 0).edit().putInt("free_day", dateId(0)).apply()
        assertTrue(TutorialVideoQuota.hasFree(ctx))
        TutorialVideoQuota.consume(ctx)
        assertFalse(TutorialVideoQuota.hasFree(ctx))
    }
}

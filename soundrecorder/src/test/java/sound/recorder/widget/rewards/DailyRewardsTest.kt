package sound.recorder.widget.rewards

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import sound.recorder.widget.testutil.FakeContext
import sound.recorder.widget.testutil.dayOfYearId
import sound.recorder.widget.util.CoinManager

class DailyRewardsTest {

    private lateinit var ctx: FakeContext
    private val practice = RewardMission("practice_secs", target = 300, reward = 5, titleRes = 0, inMinutes = true)
    private val songs = RewardMission("songs", target = 3, reward = 4, titleRes = 0)

    private fun prefs() = ctx.getSharedPreferences("zaif_daily_rewards", 0)

    @Before
    fun setUp() {
        ctx = FakeContext()
        DailyRewards.missions = listOf(practice, songs)
    }

    @After
    fun tearDown() {
        DailyRewards.missions = emptyList()
        DailyRewards.streakRewards = intArrayOf(2, 3, 4, 5, 6, 8, 10)
        DailyRewards.adReward = 3
        DailyRewards.adDailyLimit = 3
    }

    // ── Misi ────────────────────────────────────────────────────────────────

    @Test
    fun disabledWhenHostHasNoMissions() {
        DailyRewards.missions = emptyList()
        assertFalse(DailyRewards.isEnabled)
        assertFalse(DailyRewards.hasClaimable(ctx))
    }

    @Test
    fun progressAddsUpAndStopsAtTarget() {
        DailyRewards.add(ctx, "songs")
        DailyRewards.add(ctx, "songs", 1)
        assertEquals(2, DailyRewards.progress(ctx, songs))
        DailyRewards.add(ctx, "songs", 10)
        assertEquals(3, DailyRewards.progress(ctx, songs))
    }

    @Test
    fun unknownKeyAndNonPositiveAmountAreIgnored() {
        DailyRewards.add(ctx, "tidak_ada", 5)
        DailyRewards.add(ctx, "songs", 0)
        DailyRewards.add(ctx, "songs", -2)
        assertEquals(0, DailyRewards.progress(ctx, songs))
        assertFalse(prefs().all.keys.any { it.contains("tidak_ada") })
    }

    @Test
    fun cannotClaimBeforeTarget() {
        DailyRewards.add(ctx, "practice_secs", 299)
        assertFalse(DailyRewards.canClaim(ctx, practice))
        assertEquals(0, DailyRewards.claim(ctx, practice))
        assertEquals(0, CoinManager.getBalance(ctx))
    }

    @Test
    fun claimGivesCoinsOnlyOnce() {
        DailyRewards.add(ctx, "practice_secs", 300)
        assertTrue(DailyRewards.canClaim(ctx, practice))
        assertEquals(5, DailyRewards.claim(ctx, practice))
        assertEquals(5, CoinManager.getBalance(ctx))

        assertTrue(DailyRewards.isClaimed(ctx, practice))
        assertEquals(0, DailyRewards.claim(ctx, practice))
        assertEquals(5, CoinManager.getBalance(ctx))
    }

    @Test
    fun missionsAreIndependent() {
        DailyRewards.add(ctx, "songs", 3)
        assertEquals(0, DailyRewards.progress(ctx, practice))
        assertEquals(4, DailyRewards.claim(ctx, songs))
        assertFalse(DailyRewards.isClaimed(ctx, practice))
    }

    @Test
    fun yesterdaysProgressAndClaimAreReset() {
        prefs().edit()
            .putInt("day", dayOfYearId(-1))
            .putInt("p_songs", 3)
            .putBoolean("c_songs", true)
            .putInt("p_misi_lama", 7)
            .apply()

        assertEquals(0, DailyRewards.progress(ctx, songs))
        assertFalse(DailyRewards.isClaimed(ctx, songs))

        DailyRewards.add(ctx, "songs", 1)
        assertEquals(1, DailyRewards.progress(ctx, songs))
        // Misi yang sudah dihapus host ikut dibersihkan
        assertFalse(prefs().contains("p_misi_lama"))
        assertFalse(prefs().contains("c_songs"))
    }

    @Test
    fun hasClaimableForCheckInOrFinishedMission() {
        assertTrue("check-in belum diambil", DailyRewards.hasClaimable(ctx))
        DailyRewards.checkIn(ctx)
        assertFalse(DailyRewards.hasClaimable(ctx))
        DailyRewards.add(ctx, "songs", 3)
        assertTrue(DailyRewards.hasClaimable(ctx))
        DailyRewards.claim(ctx, songs)
        assertFalse(DailyRewards.hasClaimable(ctx))
    }

    // ── Iklan ───────────────────────────────────────────────────────────────

    @Test
    fun adRewardsStopAtDailyLimit() {
        assertEquals(3, DailyRewards.adsLeftToday(ctx))
        repeat(3) { assertEquals(3, DailyRewards.rewardAd(ctx)) }
        assertEquals(0, DailyRewards.adsLeftToday(ctx))
        assertEquals(0, DailyRewards.rewardAd(ctx))
        assertEquals(9, CoinManager.getBalance(ctx))
        assertEquals(3, DailyRewards.adsWatchedToday(ctx))
    }

    @Test
    fun adCounterResetsOnNewDay() {
        prefs().edit().putInt("ads_day", dayOfYearId(-1)).putInt("ads_count", 3).apply()
        assertEquals(0, DailyRewards.adsWatchedToday(ctx))
        assertEquals(3, DailyRewards.adsLeftToday(ctx))
        assertEquals(3, DailyRewards.rewardAd(ctx))
    }

    @Test
    fun hostCanChangeAdRewardAndLimit() {
        DailyRewards.adReward = 7
        DailyRewards.adDailyLimit = 1
        assertEquals(7, DailyRewards.rewardAd(ctx))
        assertEquals(0, DailyRewards.rewardAd(ctx))
    }

    @Test
    fun loweringLimitBelowWatchedNeverGoesNegative() {
        repeat(3) { DailyRewards.rewardAd(ctx) }
        DailyRewards.adDailyLimit = 1
        assertEquals(0, DailyRewards.adsLeftToday(ctx))
    }

    // ── Check-in ────────────────────────────────────────────────────────────

    @Test
    fun firstCheckInGivesDayOneReward() {
        assertTrue(DailyRewards.canCheckIn(ctx))
        assertEquals(2, DailyRewards.checkIn(ctx))
        assertEquals(1, DailyRewards.streak(ctx))
        assertEquals(2, CoinManager.getBalance(ctx))
    }

    @Test
    fun secondCheckInSameDayGivesNothing() {
        DailyRewards.checkIn(ctx)
        assertFalse(DailyRewards.canCheckIn(ctx))
        assertEquals(0, DailyRewards.checkIn(ctx))
        assertEquals(2, CoinManager.getBalance(ctx))
    }

    @Test
    fun checkInAfterYesterdayContinuesStreak() {
        prefs().edit().putInt("last_check_in_day", dayOfYearId(-1)).putInt("streak", 3).apply()
        assertEquals(3, DailyRewards.streak(ctx))
        assertEquals(5, DailyRewards.checkIn(ctx)) // hari ke-4
        assertEquals(4, DailyRewards.streak(ctx))
    }

    @Test
    fun missedDayResetsStreak() {
        prefs().edit().putInt("last_check_in_day", dayOfYearId(-2)).putInt("streak", 6).apply()
        assertEquals(0, DailyRewards.streak(ctx))
        assertEquals(2, DailyRewards.checkIn(ctx))
        assertEquals(1, DailyRewards.streak(ctx))
    }

    @Test
    fun rewardAfterLastDayStaysAtLastValue() {
        assertEquals(2, DailyRewards.rewardForDay(1))
        assertEquals(10, DailyRewards.rewardForDay(7))
        assertEquals(10, DailyRewards.rewardForDay(30))
        assertEquals(2, DailyRewards.rewardForDay(0))
    }

    @Test
    fun longStreakKeepsMaxReward() {
        prefs().edit().putInt("last_check_in_day", dayOfYearId(-1)).putInt("streak", 20).apply()
        assertEquals(10, DailyRewards.checkIn(ctx))
        assertEquals(21, DailyRewards.streak(ctx))
    }
}

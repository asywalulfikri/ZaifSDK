package sound.recorder.widget.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import sound.recorder.widget.testutil.FakeContext
import sound.recorder.widget.testutil.HOUR_MS

class CoinManagerTest {

    private lateinit var ctx: FakeContext

    @Before
    fun setUp() {
        ctx = FakeContext()
    }

    @Test
    fun newUserStartsWithZeroCoins() {
        assertEquals(0, CoinManager.getBalance(ctx))
    }

    @Test
    fun addCoinsAccumulates() {
        CoinManager.addCoins(ctx, 5)
        CoinManager.addCoins(ctx, 3, ZaifAnalytics.SOURCE_MISSION)
        assertEquals(8, CoinManager.getBalance(ctx))
    }

    @Test
    fun spendCoinsDeductsWhenEnough() {
        CoinManager.addCoins(ctx, 10)
        assertTrue(CoinManager.spendCoins(ctx, 7, ZaifAnalytics.PURPOSE_EXPORT))
        assertEquals(3, CoinManager.getBalance(ctx))
    }

    @Test
    fun spendCoinsRefusesWhenNotEnoughAndKeepsBalance() {
        CoinManager.addCoins(ctx, 4)
        assertFalse(CoinManager.spendCoins(ctx, 5))
        assertEquals(4, CoinManager.getBalance(ctx))
    }

    @Test
    fun spendExactBalanceLeavesZero() {
        CoinManager.addCoins(ctx, 6)
        assertTrue(CoinManager.spendCoins(ctx, 6))
        assertEquals(0, CoinManager.getBalance(ctx))
    }

    @Test
    fun spendZeroOrNegativeIsRejected() {
        CoinManager.addCoins(ctx, 5)
        assertFalse(CoinManager.spendCoins(ctx, 0))
        assertFalse(CoinManager.spendCoins(ctx, -3))
        assertEquals(5, CoinManager.getBalance(ctx))
    }

    @Test
    fun spendCoinUsesUnlockCost() {
        assertFalse(CoinManager.spendCoin(ctx))
        CoinManager.addCoins(ctx, CoinManager.UNLOCK_COST)
        assertTrue(CoinManager.spendCoin(ctx))
        assertEquals(0, CoinManager.getBalance(ctx))
    }

    @Test
    fun dailyBonusOnlyOncePerInterval() {
        assertTrue(CoinManager.canClaimDaily(ctx))
        assertTrue(CoinManager.claimDailyBonus(ctx))
        assertEquals(CoinManager.DAILY_COINS, CoinManager.getBalance(ctx))

        assertFalse(CoinManager.canClaimDaily(ctx))
        assertFalse(CoinManager.claimDailyBonus(ctx))
        assertEquals(CoinManager.DAILY_COINS, CoinManager.getBalance(ctx))
    }

    @Test
    fun nextClaimCountsDownFromFiveHours() {
        assertEquals(0L, CoinManager.nextClaimInMs(ctx))
        CoinManager.claimDailyBonus(ctx)
        val left = CoinManager.nextClaimInMs(ctx)
        assertTrue("sisa $left ms", left in (5 * HOUR_MS - 5_000)..(5 * HOUR_MS))
    }

    @Test
    fun dailyBonusAvailableAgainAfterFiveHours() {
        CoinManager.prefs(ctx).edit()
            .putLong("last_daily", System.currentTimeMillis() - 5 * HOUR_MS - 1_000)
            .apply()
        assertTrue(CoinManager.canClaimDaily(ctx))
        assertEquals(0L, CoinManager.nextClaimInMs(ctx))
    }

    @Test
    fun withIconReplacesCoinEmojiWithSupportedIcon() {
        assertEquals("${CoinManager.icon} 10", CoinManager.withIcon("🪙 10"))
        assertEquals("tanpa koin", CoinManager.withIcon("tanpa koin"))
    }
}

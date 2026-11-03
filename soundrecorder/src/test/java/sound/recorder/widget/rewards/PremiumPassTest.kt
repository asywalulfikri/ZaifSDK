package sound.recorder.widget.rewards

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import sound.recorder.widget.testutil.DAY_MS
import sound.recorder.widget.testutil.FakeContext
import sound.recorder.widget.util.CoinManager

class PremiumPassTest {

    private lateinit var ctx: FakeContext
    private val tolerance = 5_000L

    private fun prefs() = ctx.getSharedPreferences("zaif_premium_pass", 0)

    @Before
    fun setUp() {
        ctx = FakeContext()
    }

    @After
    fun tearDown() {
        PremiumPass.firstCostCoins = 50
        PremiumPass.costCoins = 100
        PremiumPass.durationDays = 2
    }

    @Test
    fun inactiveByDefault() {
        assertFalse(PremiumPass.isActive(ctx))
        assertEquals(0L, PremiumPass.remainingMs(ctx))
    }

    @Test
    fun firstRedeemIsCheaperThenNormalPrice() {
        assertTrue(PremiumPass.isFirstRedeem(ctx))
        assertEquals(50, PremiumPass.cost(ctx))
        CoinManager.addCoins(ctx, 50)
        assertTrue(PremiumPass.redeem(ctx))
        assertFalse(PremiumPass.isFirstRedeem(ctx))
        assertEquals(100, PremiumPass.cost(ctx))
    }

    @Test
    fun redeemFailsWithoutEnoughCoins() {
        CoinManager.addCoins(ctx, 49)
        assertFalse(PremiumPass.redeem(ctx))
        assertFalse(PremiumPass.isActive(ctx))
        assertEquals(49, CoinManager.getBalance(ctx))
        assertTrue("harga pertama belum terpakai", PremiumPass.isFirstRedeem(ctx))
    }

    @Test
    fun redeemSpendsCoinsAndActivatesForDuration() {
        CoinManager.addCoins(ctx, 60)
        assertTrue(PremiumPass.redeem(ctx))
        assertEquals(10, CoinManager.getBalance(ctx))
        assertTrue(PremiumPass.isActive(ctx))
        val left = PremiumPass.remainingMs(ctx)
        assertTrue("sisa $left", left in (2 * DAY_MS - tolerance)..(2 * DAY_MS))
    }

    @Test
    fun redeemWhileActiveExtendsInsteadOfReplacing() {
        CoinManager.addCoins(ctx, 150)
        PremiumPass.redeem(ctx)
        PremiumPass.redeem(ctx)
        assertEquals(0, CoinManager.getBalance(ctx))
        val left = PremiumPass.remainingMs(ctx)
        assertTrue("sisa $left", left in (4 * DAY_MS - tolerance)..(4 * DAY_MS))
    }

    @Test
    fun expiredPassIsInactive() {
        val now = System.currentTimeMillis()
        prefs().edit()
            .putLong("redeemed_at", now - 3 * DAY_MS)
            .putLong("expires_at", now - DAY_MS)
            .apply()
        assertFalse(PremiumPass.isActive(ctx))
    }

    @Test
    fun clockMovedBackBeforeRedeemCancelsPass() {
        val now = System.currentTimeMillis()
        prefs().edit()
            .putLong("redeemed_at", now + DAY_MS)
            .putLong("expires_at", now + 3 * DAY_MS)
            .apply()
        assertFalse(PremiumPass.isActive(ctx))
        assertEquals(0L, PremiumPass.remainingMs(ctx))
    }

    @Test
    fun hostCanChangePriceAndDuration() {
        PremiumPass.firstCostCoins = 10
        PremiumPass.durationDays = 7
        CoinManager.addCoins(ctx, 10)
        assertTrue(PremiumPass.redeem(ctx))
        assertTrue(PremiumPass.remainingMs(ctx) > 6 * DAY_MS)
    }
}

package sound.recorder.widget.rewards

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import sound.recorder.widget.testutil.FakeContext

class PremiumPurchaseTest {

    private lateinit var ctx: FakeContext

    @Before
    fun setUp() {
        ctx = FakeContext()
    }

    @Test
    fun notPurchasedByDefault() {
        assertFalse(PremiumPurchase.isPurchased(ctx))
    }

    @Test
    fun setPurchasedIsSaved() {
        PremiumPurchase.setPurchased(ctx, true)
        assertTrue(PremiumPurchase.isPurchased(ctx))
    }

    @Test
    fun setPurchasedFalseClearsFlag() {
        PremiumPurchase.setPurchased(ctx, true)
        PremiumPurchase.setPurchased(ctx, false)
        assertFalse(PremiumPurchase.isPurchased(ctx))
    }

    @Test
    fun premiumPassDoesNotCountAsPurchase() {
        PremiumPass.durationDays = 2
        ctx.getSharedPreferences("zaif_premium_pass", 0).edit()
            .putLong("expires_at", System.currentTimeMillis() + 60_000L).apply()
        assertTrue(PremiumPass.isActive(ctx))
        assertFalse(PremiumPurchase.isPurchased(ctx))
    }
}

package sound.recorder.widget.util

import org.junit.Test
import sound.recorder.widget.testutil.FakeContext

/**
 * Tracking tidak boleh membuat fitur crash, termasuk di host tanpa google-services (Firebase belum siap).
 * Di JVM test Firebase memang tidak bisa jalan, jadi setiap panggilan harus tertangkap diam-diam.
 */
class ZaifAnalyticsTest {

    @Test
    fun everyEventIsSafeWithoutFirebase() {
        val ctx = FakeContext()
        ZaifAnalytics.coinEarned(ctx, ZaifAnalytics.SOURCE_MISSION, 5)
        ZaifAnalytics.coinSpent(ctx, ZaifAnalytics.PURPOSE_EXPORT, 10)
        ZaifAnalytics.coinAdWatched(ctx)
        ZaifAnalytics.premiumPassRedeemed(ctx, 50)
        ZaifAnalytics.removeAdsPurchased(ctx)
        ZaifAnalytics.premiumOfferShown(ctx, ZaifAnalytics.PLACE_VIDEO)
        ZaifAnalytics.premiumOfferClicked(ctx, ZaifAnalytics.PLACE_REMOVE_ADS)
        ZaifAnalytics.exportStarted(ctx, ZaifAnalytics.FORMAT_VIDEO, ZaifAnalytics.SOURCE_RECORDING)
        ZaifAnalytics.exportFinished(ctx, "mp3", ZaifAnalytics.SOURCE_TUTORIAL)
        ZaifAnalytics.exportFailed(ctx, "midi", ZaifAnalytics.SOURCE_RECORDING, "x".repeat(500))
        ZaifAnalytics.exportUnlock(ctx, ZaifAnalytics.UNLOCK_COIN)
        ZaifAnalytics.rewardAdRequested(ctx)
        ZaifAnalytics.rewardAdResult(ctx, ZaifAnalytics.AD_EARNED)
    }
}

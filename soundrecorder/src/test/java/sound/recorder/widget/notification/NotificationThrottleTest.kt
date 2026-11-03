package sound.recorder.widget.notification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import sound.recorder.widget.testutil.FakeContext
import sound.recorder.widget.testutil.HOUR_MS

class NotificationThrottleTest {

    private lateinit var ctx: FakeContext
    private val now get() = System.currentTimeMillis()

    private fun set(lastShown: Long? = null, lastOpen: Long? = null) {
        val e = ctx.getSharedPreferences("zaif_notification_throttle", 0).edit()
        lastShown?.let { e.putLong("last_shown_at", it) }
        lastOpen?.let { e.putLong("last_open_at", it) }
        e.apply()
    }

    @Before
    fun setUp() {
        ctx = FakeContext()
    }

    @Test
    fun freshInstallCanShow() {
        assertTrue(NotificationThrottle.canShow(ctx))
    }

    @Test
    fun noSecondNotificationWithin48Hours() {
        NotificationThrottle.markShown(ctx)
        assertFalse(NotificationThrottle.canShow(ctx))
        set(lastShown = now - 47 * HOUR_MS)
        assertFalse(NotificationThrottle.canShow(ctx))
        set(lastShown = now - 49 * HOUR_MS)
        assertTrue(NotificationThrottle.canShow(ctx))
    }

    @Test
    fun skippedWhenAppOpenedWithin20Hours() {
        NotificationThrottle.markAppOpened(ctx)
        assertFalse(NotificationThrottle.canShow(ctx))
        set(lastOpen = now - 19 * HOUR_MS)
        assertFalse(NotificationThrottle.canShow(ctx))
        set(lastOpen = now - 21 * HOUR_MS)
        assertTrue(NotificationThrottle.canShow(ctx))
    }

    @Test
    fun bothRulesMustPass() {
        set(lastShown = now - 72 * HOUR_MS, lastOpen = now - HOUR_MS)
        assertFalse(NotificationThrottle.canShow(ctx))
        set(lastShown = now - HOUR_MS, lastOpen = now - 72 * HOUR_MS)
        assertFalse(NotificationThrottle.canShow(ctx))
    }

    @Test
    fun clockMovedBackDoesNotBlockForever() {
        // Nilai tersimpan di masa depan (jam HP dimundurkan) dianggap sudah lama lewat
        set(lastShown = now + 10 * 24 * HOUR_MS, lastOpen = now + 10 * 24 * HOUR_MS)
        assertTrue(NotificationThrottle.canShow(ctx))
    }
}

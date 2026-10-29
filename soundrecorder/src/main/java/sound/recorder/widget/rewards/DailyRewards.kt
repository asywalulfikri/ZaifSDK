package sound.recorder.widget.rewards

import android.content.Context
import androidx.annotation.Keep
import androidx.annotation.StringRes
import sound.recorder.widget.R
import sound.recorder.widget.notification.CoinNotificationWorker
import sound.recorder.widget.util.CoinManager
import java.util.Calendar

/**
 * Satu misi harian milik host app.
 * @param key id unik & stabil (disimpan di SharedPreferences), dipakai di [DailyRewards.add].
 * @param titleRes string host berisi `%1$d` = target (dalam menit bila [inMinutes]).
 * @param inMinutes progres dicatat dalam detik, ditampilkan sebagai menit.
 */
@Keep
data class RewardMission(
    val key: String,
    val target: Int,
    val reward: Int,
    @StringRes val titleRes: Int,
    val inMinutes: Boolean = false,
    val emoji: String = "🎯"
)

/**
 * Misi harian (reset tiap tengah malam), check-in beruntun, dan koin dari iklan rewarded.
 * Koin masuk ke [CoinManager] SDK, jadi bisa dipakai untuk [PremiumPass] dan export berbayar koin.
 *
 * Pemakaian di host app (sekali, misal di Application.onCreate):
 * ```
 * DailyRewards.missions = listOf(RewardMission("practice_secs", 5 * 60, 5, R.string.mission_practice, inMinutes = true))
 * ```
 * lalu catat progres: `DailyRewards.add(context, "practice_secs", detik)` dan buka panel: `RewardsDialog.show(activity)`.
 */
object DailyRewards {

    /** Misi milik host app. Kosong = fitur misi tidak dipakai. */
    @JvmStatic
    var missions: List<RewardMission> = emptyList()

    /** Hadiah check-in hari ke-1..n; setelahnya tetap memakai nilai terakhir. */
    @JvmStatic
    var streakRewards = intArrayOf(2, 3, 4, 5, 6, 8, 10)

    /** Koin per iklan rewarded dan batas iklan per hari. */
    @JvmStatic
    var adReward = 3

    @JvmStatic
    var adDailyLimit = 3

    val isEnabled: Boolean get() = missions.isNotEmpty()

    private const val PREFS          = "zaif_daily_rewards"
    private const val KEY_DAY        = "day"
    private const val KEY_LAST_CHECK = "last_check_in_day"
    private const val KEY_STREAK     = "streak"
    private const val KEY_ADS_DAY    = "ads_day"
    private const val KEY_ADS_COUNT  = "ads_count"
    private val lock = Any()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** yyyyDDD menurut zona waktu HP, mis. 2026302. */
    private fun dayId(offsetDays: Int = 0): Int {
        val c = Calendar.getInstance()
        if (offsetDays != 0) c.add(Calendar.DAY_OF_YEAR, offsetDays)
        return c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR)
    }

    private fun progressKey(key: String) = "p_$key"
    private fun claimedKey(key: String) = "c_$key"
    private fun isToday(context: Context) = prefs(context).getInt(KEY_DAY, 0) == dayId()
    private fun mission(key: String) = missions.firstOrNull { it.key == key }

    // ── Misi ────────────────────────────────────────────────────────────────

    @JvmStatic
    fun progress(context: Context, mission: RewardMission): Int =
        if (isToday(context)) prefs(context).getInt(progressKey(mission.key), 0).coerceAtMost(mission.target) else 0

    @JvmStatic
    fun isClaimed(context: Context, mission: RewardMission): Boolean =
        isToday(context) && prefs(context).getBoolean(claimedKey(mission.key), false)

    @JvmStatic
    fun canClaim(context: Context, mission: RewardMission): Boolean =
        !isClaimed(context, mission) && progress(context, mission) >= mission.target

    /** Tambah progres misi [key] (detik untuk misi [RewardMission.inMinutes]). Key tak dikenal diabaikan. */
    @JvmStatic
    @JvmOverloads
    fun add(context: Context, key: String, amount: Int = 1) {
        val mission = mission(key) ?: return
        if (amount <= 0) return
        synchronized(lock) {
            val p = prefs(context)
            val editor = p.edit()
            val current = if (isToday(context)) p.getInt(progressKey(key), 0) else {
                // Hari baru: hapus progres & status klaim kemarin (semua key, termasuk misi lama)
                p.all.keys.filter { it.startsWith("p_") || it.startsWith("c_") }.forEach { editor.remove(it) }
                editor.putInt(KEY_DAY, dayId())
                0
            }
            if (current >= mission.target) return
            editor.putInt(progressKey(key), (current + amount).coerceAtMost(mission.target)).apply()
        }
    }

    /** Ambil hadiah misi; mengembalikan jumlah koin, atau 0 bila belum bisa. */
    @JvmStatic
    fun claim(context: Context, mission: RewardMission): Int {
        synchronized(lock) {
            if (!canClaim(context, mission)) return 0
            prefs(context).edit().putBoolean(claimedKey(mission.key), true).apply()
            CoinManager.addCoins(context, mission.reward)
            return mission.reward
        }
    }

    /** true bila ada check-in atau misi yang hadiahnya bisa diambil (untuk titik merah di tombol Misi). */
    @JvmStatic
    fun hasClaimable(context: Context): Boolean =
        isEnabled && (canCheckIn(context) || missions.any { canClaim(context, it) })

    // ── Iklan rewarded ───────────────────────────────────────────────────────

    @JvmStatic
    fun adsWatchedToday(context: Context): Int {
        val p = prefs(context)
        return if (p.getInt(KEY_ADS_DAY, 0) == dayId()) p.getInt(KEY_ADS_COUNT, 0) else 0
    }

    @JvmStatic
    fun adsLeftToday(context: Context): Int = (adDailyLimit - adsWatchedToday(context)).coerceAtLeast(0)

    /** Catat iklan yang selesai; mengembalikan koin yang didapat, atau 0 bila batas harian habis. */
    @JvmStatic
    fun rewardAd(context: Context): Int {
        synchronized(lock) {
            val watched = adsWatchedToday(context)
            if (watched >= adDailyLimit) return 0
            prefs(context).edit()
                .putInt(KEY_ADS_DAY, dayId())
                .putInt(KEY_ADS_COUNT, watched + 1)
                .apply()
            CoinManager.addCoins(context, adReward)
            return adReward
        }
    }

    // ── Check-in beruntun ────────────────────────────────────────────────────

    @JvmStatic
    fun canCheckIn(context: Context): Boolean =
        prefs(context).getInt(KEY_LAST_CHECK, 0) != dayId()

    /** Streak yang sedang berjalan; 0 bila kemarin terlewat. */
    @JvmStatic
    fun streak(context: Context): Int {
        val p = prefs(context)
        val last = p.getInt(KEY_LAST_CHECK, 0)
        return if (last == dayId() || last == dayId(-1)) p.getInt(KEY_STREAK, 0) else 0
    }

    @JvmStatic
    fun rewardForDay(day: Int): Int = streakRewards[(day - 1).coerceIn(0, streakRewards.size - 1)]

    /** Check-in hari ini; mengembalikan koin yang didapat, atau 0 bila sudah check-in. */
    @JvmStatic
    fun checkIn(context: Context): Int {
        val reward = synchronized(lock) {
            if (!canCheckIn(context)) return 0
            val day = streak(context) + 1
            val reward = rewardForDay(day)
            prefs(context).edit()
                .putInt(KEY_LAST_CHECK, dayId())
                .putInt(KEY_STREAK, day)
                .apply()
            CoinManager.addCoins(context, reward)
            reward
        }
        // Jadwalkan pengingat check-in berikutnya (tetap dibatasi NotificationThrottle)
        CoinNotificationWorker.schedule(context)
        return reward
    }

    // ── Teks ────────────────────────────────────────────────────────────────

    /** Sisa waktu, mis. "1h 5j" / "3j 20m" sesuai bahasa. */
    @JvmStatic
    fun formatRemaining(context: Context, ms: Long): String {
        val totalMin = ms / 60_000L
        val days = (totalMin / (24 * 60)).toInt()
        val hours = ((totalMin / 60) % 24).toInt()
        val minutes = (totalMin % 60).toInt()
        return if (days > 0) context.getString(R.string.daily_rewards_left_dh, days, hours)
        else context.getString(R.string.daily_rewards_left_hm, hours, minutes)
    }
}

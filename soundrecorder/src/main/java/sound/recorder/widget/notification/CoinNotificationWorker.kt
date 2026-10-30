package sound.recorder.widget.notification

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import sound.recorder.widget.R
import sound.recorder.widget.builder.ZaifSDKBuilder
import sound.recorder.widget.rewards.DailyRewards
import sound.recorder.widget.util.CoinManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

class CoinNotificationWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    companion object {
        private const val CHANNEL_ID        = "coin_reminder"
        private const val WORK_TAG          = "coin_claim_reminder"
        private const val NOTIF_ID          = 1001
        private const val KEY_TARGET_CLASS  = "target_class"
        private const val KEY_NOTIF_TITLE   = "notif_title"

        /**
         * Jadwalkan notif jam 20:00 pertama setelah koin harian bisa di-claim lagi.
         * Dipanggil saat aplikasi dibuka (MyApp, jika isCoin) dan otomatis setelah claim sukses.
         */
        fun schedule(context: Context) {
            try {
                val now = Calendar.getInstance()
                // Dulu: kalau belum bisa claim langsung return — padahal schedule() dipanggil
                // setelah claim, jadi notif berikutnya tidak pernah terjadwal.
                val claimableAt = now.timeInMillis + CoinManager.nextClaimInMs(context)
                val target = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 20)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                // Majukan ke jam 20:00 berikutnya sampai tidak di masa lalu dan koin sudah bisa di-claim
                while (!target.after(now) || target.timeInMillis < claimableAt) {
                    target.add(Calendar.DAY_OF_YEAR, 1)
                }

                val delay = target.timeInMillis - now.timeInMillis

                WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG)

                val request = OneTimeWorkRequestBuilder<CoinNotificationWorker>()
                    .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                    .addTag(WORK_TAG)
                    .build()

                WorkManager.getInstance(context).enqueue(request)
            } catch (e: Throwable) {
                // Lihat catatan di MyApp.kt: beberapa device Android 14 melempar
                // NoSuchMethodError (Error, bukan Exception) dari WorkManager/JobScheduler.
            }
        }

        fun cancel(context: Context) {
            try {
                WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG)
            } catch (e: Throwable) {
            }
        }

        fun scheduleTest(context: Context) {
            try {
                WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG)
                val request = OneTimeWorkRequestBuilder<CoinNotificationWorker>()
                    .setInitialDelay(5, TimeUnit.SECONDS)
                    .addTag(WORK_TAG)
                    .build()
                WorkManager.getInstance(context).enqueue(request)
            } catch (e: Throwable) {
            }
        }
    }

    @SuppressLint("MissingPermission") // dicek lewat areNotificationsEnabled()
    override fun doWork(): Result {
        // Sudah di-claim sejak dijadwalkan → tidak perlu diingatkan
        // App dengan misi harian SDK: ingatkan check-in; selain itu bonus koin harian lama
        val claimable = if (DailyRewards.isEnabled) DailyRewards.canCheckIn(context) else CoinManager.canClaimDaily(context)
        if (!claimable) return Result.success()
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return Result.success()
        // Jangan terlalu sering: batas bersama dengan pengingat buka app, dan lewati bila app baru dibuka
        if (!NotificationThrottle.canShow(context)) return Result.success()
        createChannel()

        // Dulu wajib inputData target_class/notif_title yang tidak pernah diisi oleh schedule()
        // → selalu Result.failure(). Sekarang opsional: default buka launcher activity host app.
        val targetClass = inputData.getString(KEY_TARGET_CLASS)
            ?.let { runCatching { Class.forName(it) }.getOrNull() }
        val intent = (if (targetClass != null) Intent(context, targetClass)
            else context.packageManager.getLaunchIntentForPackage(context.packageName))
            ?.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK }
            ?: return Result.success()
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notifTitle = inputData.getString(KEY_NOTIF_TITLE)
            ?: runCatching { ZaifSDKBuilder.load(context)?.appName }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: context.applicationInfo.loadLabel(context.packageManager).toString()
        val notification = NotificationCompat.Builder(context,
            CHANNEL_ID
        )
            .setSmallIcon(R.drawable.ic_baseline_notifications_active_24)
            .setContentTitle("${CoinManager.icon} $notifTitle")
            .setContentText(CoinManager.withIcon(context.getString(R.string.claim_daily_reward)))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIF_ID, notification)
        NotificationThrottle.markShown(context)

        return Result.success()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Coin Reminder",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        nm.createNotificationChannel(channel)
    }
}

package sound.recorder.widget.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import sound.recorder.widget.R
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

        fun schedule(context: Context) {
            // Jika sudah claim hari ini, jangan jadwalkan notif untuk hari ini
            if (!CoinManager.canClaimDaily(context)) {
                // Opsional: Bisa jadwalkan untuk besok jam 20:00, 
                // tapi biasanya cukup panggil schedule() lagi setelah claim sukses.
                return 
            }

            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 20)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // Jika sekarang sudah lewat jam 20:00, jadwalkan untuk besok jam 20:00
            if (now.after(target)) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }

            val delay = target.timeInMillis - now.timeInMillis

            WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG)

            val request = OneTimeWorkRequestBuilder<CoinNotificationWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .addTag(WORK_TAG)
                .build()

            WorkManager.getInstance(context).enqueue(request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG)
        }

        fun scheduleTest(context: Context) {
            WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG)
            val request = OneTimeWorkRequestBuilder<CoinNotificationWorker>()
                .setInitialDelay(5, TimeUnit.SECONDS)
                .addTag(WORK_TAG)
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }

    override fun doWork(): Result {
        createChannel()

        val className = inputData.getString(KEY_TARGET_CLASS) ?: return Result.failure()
        val targetClass = runCatching { Class.forName(className) }.getOrNull() ?: return Result.failure()

        val intent = Intent(context, targetClass).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notifTitle = inputData.getString(KEY_NOTIF_TITLE) ?: ""
        val notification = NotificationCompat.Builder(context,
            CHANNEL_ID
        )
            .setSmallIcon(R.drawable.ic_baseline_notifications_active_24)
            .setContentTitle("🪙 $notifTitle")
            .setContentText(context.getString(R.string.claim_daily_reward))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, notification)

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

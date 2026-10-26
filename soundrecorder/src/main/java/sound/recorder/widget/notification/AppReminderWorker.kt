package sound.recorder.widget.notification

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import sound.recorder.widget.R
import sound.recorder.widget.builder.ZaifSDKBuilder
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Notifikasi lokal "ayo buka aplikasi lagi": muncul tiap [INTERVAL_DAYS] hari sekitar jam 20:30–21:30
 * (sengaja setelah notif koin jam 20:00 di [CoinNotificationWorker], supaya tidak muncul bersamaan).
 * Terpisah dari notif koin: pengingat ini aktif di semua app, notif koin hanya jika isCoin.
 *
 * Satu OneTimeWork yang menjadwalkan dirinya lagi setelah jalan (bukan PeriodicWork, supaya jamnya
 * tidak bergeser terus). [schedule] aman dipanggil setiap aplikasi dibuka: jadwal yang sudah ada dipertahankan.
 * Teks diambil dari [AppReminderMessages] sesuai bahasa device dan jam saat notif muncul.
 */
class AppReminderWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    companion object {
        private const val TAG = "AppReminderWorker"
        private const val CHANNEL_ID = "app_reminder"
        private const val WORK_NAME = "app_open_reminder"
        private const val NOTIF_ID = 1002

        private const val INTERVAL_DAYS = 2
        private const val WINDOW_START_HOUR = 20
        private const val WINDOW_START_MINUTE = 30 // mulai 20:30
        private const val WINDOW_MINUTES = 60      // acak sampai 21:30

        // Kalau WorkManager telat (Doze / HP mati), jangan kirim notif tengah malam
        private const val QUIET_START_HOUR = 22
        private const val QUIET_END_HOUR = 8

        /** Jadwalkan pengingat kalau belum ada. Panggil saat aplikasi dibuka. */
        @JvmStatic
        fun schedule(context: Context) {
            enqueue(context, delayToWindow(daysAhead = INTERVAL_DAYS), ExistingWorkPolicy.KEEP)
        }

        @JvmStatic
        fun cancel(context: Context) {
            try {
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            } catch (e: Throwable) {
                Log.e(TAG, "cancel error: ${e.message}")
            }
        }

        /** Untuk uji coba: notif muncul ±10 detik lagi, lalu jadwal normal berlanjut. */
        @JvmStatic
        fun scheduleTest(context: Context) {
            enqueue(context, TimeUnit.SECONDS.toMillis(10), ExistingWorkPolicy.REPLACE)
        }

        private fun enqueue(context: Context, delayMs: Long, policy: ExistingWorkPolicy) {
            try {
                val request = OneTimeWorkRequestBuilder<AppReminderWorker>()
                    .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                    .build()
                WorkManager.getInstance(context.applicationContext)
                    .enqueueUniqueWork(WORK_NAME, policy, request)
            } catch (e: Throwable) {
                // Lihat catatan di MyApp.kt: beberapa device Android 14 melempar
                // NoSuchMethodError (Error, bukan Exception) dari WorkManager/JobScheduler.
                Log.e(TAG, "enqueue error: ${e.message}")
            }
        }

        /** Jeda sampai jam acak 20:30–21:30, [daysAhead] hari dari hari ini. */
        private fun delayToWindow(daysAhead: Int): Long {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, daysAhead)
                set(Calendar.HOUR_OF_DAY, WINDOW_START_HOUR)
                set(Calendar.MINUTE, WINDOW_START_MINUTE)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                add(Calendar.MINUTE, Random.nextInt(WINDOW_MINUTES))
            }
            if (!target.after(now)) target.add(Calendar.DAY_OF_YEAR, 1)
            return target.timeInMillis - now.timeInMillis
        }

        private fun isQuietHour(hour: Int) = hour >= QUIET_START_HOUR || hour < QUIET_END_HOUR
    }

    override fun doWork(): Result {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (isQuietHour(hour)) {
            // Terlambat jalan: lewati, coba lagi malam berikutnya (hari ini kalau sudah lewat tengah malam)
            scheduleNext(daysAhead = if (hour < QUIET_END_HOUR) 0 else 1)
            return Result.success()
        }

        try {
            showNotification(hour)
        } catch (e: Throwable) {
            Log.e(TAG, "show error: ${e.message}")
        }
        scheduleNext(daysAhead = INTERVAL_DAYS)
        return Result.success()
    }

    // APPEND_OR_REPLACE: worker ini masih RUNNING, jadi KEEP akan mengabaikan jadwal baru
    // dan REPLACE akan membatalkan worker ini sendiri.
    private fun scheduleNext(daysAhead: Int) =
        enqueue(context, delayToWindow(daysAhead), ExistingWorkPolicy.APPEND_OR_REPLACE)

    @SuppressLint("MissingPermission") // dicek lewat areNotificationsEnabled()
    private fun showNotification(hour: Int) {
        // Izin notifikasi belum diberi / dimatikan user → diam saja, jadwal tetap jalan
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED) }
            ?: return
        val pendingIntent = PendingIntent.getActivity(
            context, NOTIF_ID, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val (title, body) = AppReminderMessages.build(appName(), hour)
        createChannel()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_baseline_notifications_active_24)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIF_ID, notification)
    }

    /** Nama dari ZaifSDKConfig (diset host app), fallback ke label aplikasi. */
    private fun appName(): String {
        val fromConfig = runCatching { ZaifSDKBuilder.load(context)?.appName }.getOrNull()
        if (!fromConfig.isNullOrBlank()) return fromConfig
        return context.applicationInfo.loadLabel(context.packageManager).toString()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Reminder", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }
}

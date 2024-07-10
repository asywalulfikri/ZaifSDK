package sound.recorder.widget.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class BackgroundService : Service() {
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val intent1 = Intent(this, this.applicationContext.javaClass)
        val pendingIntent1 =
            PendingIntent.getActivity(this, 0, intent1, PendingIntent.FLAG_IMMUTABLE)
        val notification1 = NotificationCompat.Builder(this, "ScreenRecorder")
            .setContentTitle("ScreenRe")
            .setContentText("Filming...")
            .setContentIntent(pendingIntent1).build()
        startForeground(1, notification1)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "ScreenRecorder", "Foreground notification",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(
                NotificationManager::class.java
            )
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        stopForeground(true)
        stopSelf()
        super.onDestroy()
    }
}
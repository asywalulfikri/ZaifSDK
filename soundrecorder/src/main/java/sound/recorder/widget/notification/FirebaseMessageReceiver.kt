package sound.recorder.widget.notification

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import sound.recorder.widget.R

class FirebaseMessageReceiver : FirebaseMessagingService() {
    private val channelId = "default_notification_channel_id"

    override fun onNewToken(token: String) {
        Log.d("Response", "Refreshed token: $token")
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d("ResponseNotification", remoteMessage.toString())
        if (remoteMessage.data.isNotEmpty() || remoteMessage.notification != null) {
            showNotification(
                remoteMessage.notification?.title.toString(),
                remoteMessage.notification?.body.toString()
            )
        }
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    fun showNotification(title: String?, message: String?) {
        val intent = Intent()
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent: PendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        } else {
            PendingIntent.getActivity(
                this,
                0, intent, PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val builder = NotificationCompat.Builder(applicationContext, channelId)
            .setAutoCancel(true)
            .setContentText(message)
            .setSound(soundUri)
            .setSmallIcon(R.drawable.ic_baseline_notifications_active_24)
            .setContentTitle(title)
            .setVibrate(
                longArrayOf(
                    1000, 1000, 1000,
                    1000, 1000
                )
            )
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(
            NOTIFICATION_SERVICE
        ) as NotificationManager

        // Check if the Android Version is greater than Oreo
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                channelId, "web_app",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(
                notificationChannel
            )
        }
        notificationManager.notify(0, builder.build())
    }
}
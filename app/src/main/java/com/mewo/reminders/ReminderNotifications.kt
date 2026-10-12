package com.mewo.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.mewo.MainActivity
import com.mewo.R
import com.mewo.data.WordEntity

class ReminderNotifications(
    private val context: Context,
) {
    companion object {
        const val CHANNEL_ID = "hebrew_reminders"
        private const val NOTIFICATION_ID = 1
    }

    fun createChannel() {
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.reminder_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        channel.description = context.getString(R.string.reminder_channel_description)
        // Use the resource name so the stored URI survives resource ID changes on upgrades.
        // Android preserves sound/importance choices on channels that already exist.
        channel.setSound(
            "android.resource://${context.packageName}/${context.resources.getResourceTypeName(R.raw.ding_don)}/"
                .plus(context.resources.getResourceEntryName(R.raw.ding_don))
                .toUri(),
            AudioAttributes
                .Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun allowed(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        return NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            manager.getNotificationChannel(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    // allowed() checks the runtime permission and the channel immediately before posting.
    @SuppressLint("MissingPermission")
    fun publish(words: List<WordEntity>): Boolean {
        if (words.isEmpty() || !allowed()) return false
        val openApp =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val layout = ReminderNotificationLayout(context)
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.reminder_title))
                .setContentText(layout.line(words.first()))
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(layout.compact(words))
                .setCustomBigContentView(layout.expanded(words))
                .setCustomHeadsUpContentView(layout.compact(words))
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .build()
        return try {
            context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }
}

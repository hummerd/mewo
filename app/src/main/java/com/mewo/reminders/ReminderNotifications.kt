package com.mewo.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mewo.MainActivity
import com.mewo.R
import com.mewo.data.WordEntity

class ReminderNotifications(private val context: Context) {
    companion object { const val CHANNEL_ID = "hebrew_reminders"; private const val NOTIFICATION_ID = 1 }

    fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_DEFAULT)
        channel.description = context.getString(R.string.reminder_channel_description)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun allowed(): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        val manager = context.getSystemService(NotificationManager::class.java)
        return NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            manager.getNotificationChannel(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    // allowed() checks the runtime permission and the channel immediately before posting.
    @SuppressLint("MissingPermission")
    fun publish(words: List<WordEntity>): Boolean {
        if (words.isEmpty() || !allowed()) return false
        val openApp = PendingIntent.getActivity(context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val lines = words.map { "\u200f${it.hebrew}\u200e — ${it.english} (${it.transliteration})" }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.reminder_title))
            .setContentText(lines.first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        return try {
            context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
            true
        } catch (_: SecurityException) { false }
    }
}

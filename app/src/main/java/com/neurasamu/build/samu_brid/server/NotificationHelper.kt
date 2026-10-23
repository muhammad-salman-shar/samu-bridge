package com.neurasamu.build.samu_brid.server

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.neurasamu.build.samu_brid.MainActivity

object NotificationHelper {
    const val CHANNEL_REPLY = "samu_bridge_reply"
    const val KEY_REPLY = "key_reply_text"

    fun showReplyNotification(ctx: Context, id: Int, prompt: String, source: String) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_REPLY, "Chat Replies", NotificationManager.IMPORTANCE_HIGH)
            ch.description = "Notifications for incoming chat requests"
            nm.createNotificationChannel(ch)
        }

        // 1. Reply Action
        val replyIntent = Intent(ctx, NotificationReplyReceiver::class.java).apply {
            action = "com.neurasamu.build.samu_brid.ACTION_REPLY"
            putExtra("id", id)
        }
        val replyPending = PendingIntent.getBroadcast(
            ctx, id, replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val remoteInput = RemoteInput.Builder(KEY_REPLY)
            .setLabel("Reply to $source...")
            .build()
        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send, "Reply", replyPending
        ).addRemoteInput(remoteInput).build()

        // 2. Copy Action
        val copyIntent = Intent(ctx, NotificationCopyReceiver::class.java).apply {
            action = "com.neurasamu.build.samu_brid.ACTION_COPY"
            putExtra("copy_text", prompt)
            putExtra("notif_id", id)
        }
        val copyPending = PendingIntent.getBroadcast(
            ctx, id + 1000, copyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val copyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_save, "Copy", copyPending
        ).build()

        val openIntent = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(ctx, CHANNEL_REPLY)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("New message from $source")
            .setContentText(prompt)
            .setStyle(NotificationCompat.BigTextStyle().bigText(prompt))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(openIntent)
            .addAction(replyAction)
            .addAction(copyAction)
            .setAutoCancel(false)
            .build()

        nm.notify(id, notif)
    }

    fun cancel(ctx: Context, id: Int) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(id)
    }
}

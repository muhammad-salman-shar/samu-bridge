package com.neurasamu.build.samu_brid.server

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.neurasamu.build.samu_brid.core.BridgePipeline

class NotificationReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("id", -1)
        val remoteInput = RemoteInput.getResultsFromIntent(intent)
        val text = remoteInput?.getCharSequence(NotificationHelper.KEY_REPLY)?.toString()
        
        if (!text.isNullOrBlank()) {
            BridgePipeline.reply(text)
            NotificationHelper.cancel(context, id)
        }
    }
}

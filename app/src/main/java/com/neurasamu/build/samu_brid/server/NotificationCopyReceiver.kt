package com.neurasamu.build.samu_brid.server

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast

class NotificationCopyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val text = intent.getStringExtra("copy_text") ?: return
        val id = intent.getIntExtra("notif_id", -1)
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("samu_bridge_copy", text))
        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        // Notification stays visible — user removes it manually
        _ = id
    }
}

package com.neurasamu.build.samu_brid.ui.tabs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neurasamu.build.samu_brid.core.BridgePipeline
import com.neurasamu.build.samu_brid.data.BridgeSettings
import com.neurasamu.build.samu_brid.data.ChatLog
import com.neurasamu.build.samu_brid.server.BridgeServerService
import kotlinx.coroutines.delay

@Composable
fun BridgeTab(settings: BridgeSettings) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val entries by ChatLog.entries.collectAsState()
    val pending by BridgePipeline.pending.collectAsState()
    var input by remember { mutableStateOf("") }
    var serverOn by remember { mutableStateOf(BridgeServerService.isRunning) }

    LaunchedEffect(Unit) {
        while (true) { serverOn = BridgeServerService.isRunning; delay(1000) }
    }

    val hasPending = pending.isNotEmpty()

    Column(Modifier.fillMaxSize().padding(8.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Server: " + if (serverOn) "ON :${BridgeServerService.boundPort}" else "OFF",
                color = if (serverOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (hasPending) "PENDING: ${pending.size}" else "idle",
                color = if (hasPending) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium
            )
        }
        HorizontalDivider()

        Box(
            Modifier.weight(1f).fillMaxWidth().padding(top = 6.dp, bottom = 6.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .padding(6.dp)
        ) {
            if (entries.isEmpty()) {
                Text(
                    "Output box\n\nSamu Chat se jo bhi message aayega yahan dikhega.\nTab input box me reply likh ke Send dabao — wapas Samu Chat me chala jayega.",
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(entries) { e -> EntryRow(e) }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = {
                val last = ChatLog.lastOut() ?: return@Button
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("out", last))
            }) { Text("Copy last") }
            Button(onClick = {
                val last = ChatLog.lastOut() ?: return@Button
                input = last
            }) { Text("Import → input") }
            Button(onClick = { ChatLog.clear() }) { Text("Clear") }
        }

        HorizontalDivider(Modifier.padding(vertical = 6.dp))

        if (hasPending) {
            val p = pending.first()
            Text(
                "Reply to: [${p.source}] ${p.prompt.take(120)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary
            )
        }

        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text(if (hasPending) "Reply text" else "Input (test only)") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 160.dp)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
        ) {
            Button(onClick = {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val t = cm.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                input = if (input.isBlank()) t else "$input\n$t"
            }) { Text("Paste") }
            Button(
                onClick = {
                    val p = input.trim()
                    if (p.isBlank()) return@Button
                    keyboard?.hide()
                    if (hasPending) {
                        BridgePipeline.reply(p)
                    } else {
                        BridgePipeline.logManual(p)
                    }
                    input = ""
                },
                enabled = input.isNotBlank()
            ) { Text(if (hasPending) "Send reply" else "Log") }
            Button(onClick = { input = "" }) { Text("Clear") }
        }
    }
}

@Composable
private fun EntryRow(e: ChatLog.Entry) {
    val tag = when (e.kind) {
        ChatLog.Kind.IN -> "IN"; ChatLog.Kind.OUT -> "OUT"; ChatLog.Kind.ERR -> "ERR"
    }
    Text(
        "[$tag${if (e.source.isNotBlank()) " ${e.source}" else ""}] ${e.text}",
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        color = when (e.kind) {
            ChatLog.Kind.ERR -> MaterialTheme.colorScheme.error
            ChatLog.Kind.OUT -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurface
        }
    )
    HorizontalDivider(Modifier.padding(vertical = 2.dp))
}

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
import kotlinx.coroutines.launch

@Composable
fun BridgeTab(settings: BridgeSettings) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val entries by ChatLog.entries.collectAsState()
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var serverOn by remember { mutableStateOf(BridgeServerService.isRunning) }

    LaunchedEffect(Unit) {
        while (true) { serverOn = BridgeServerService.isRunning; delay(1000) }
    }

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
                "Target: " + settings.targetLabel.ifBlank { settings.targetPackage.ifBlank { "—" } },
                style = MaterialTheme.typography.labelMedium
            )
        }
        HorizontalDivider()

        Box(
            Modifier.weight(1f).fillMaxWidth().padding(top = 6.dp, bottom = 6.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .padding(6.dp)
        ) {
            if (busy && entries.isNotEmpty()) {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(entries) { e -> EntryRow(e) }
                    item { Text("[…] Waiting for DeepSeek response…", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary) }
                }
            } else if (entries.isEmpty()) {
                Text(
                    "Output box\n\nChat app se aane wale messages (IN), driver ke responses (OUT), aur errors (ERR) yahan dikhenge.",
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(entries) { e ->
                        val tag = when (e.kind) {
                            ChatLog.Kind.IN -> "IN"
                            ChatLog.Kind.OUT -> "OUT"
                            ChatLog.Kind.ERR -> "ERR"
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

        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("Input") },
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
                    busy = true
                    scope.launch {
                        kotlinx.coroutines.delay(300)
                        BridgePipeline.submit(p, "manual")
                        busy = false
                    }
                    input = ""
                },
                enabled = !busy && input.isNotBlank()
            ) { Text(if (busy) "Sending…" else "Send") }
            Button(onClick = { input = "" }, enabled = !busy) { Text("Clear") }
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

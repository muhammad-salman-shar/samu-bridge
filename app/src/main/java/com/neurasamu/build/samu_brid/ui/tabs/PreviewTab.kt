package com.neurasamu.build.samu_brid.ui.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neurasamu.build.samu_brid.accessibility.BridgeAccessibilityService
import com.neurasamu.build.samu_brid.data.LogRepo

@Composable
fun PreviewTab(targetPackage: String) {
    var dump by remember { mutableStateOf("") }
    var showDump by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(8.dp)) {
        Text(
            "Target: " + if (targetPackage.isBlank()) "(not set)" else targetPackage,
            style = MaterialTheme.typography.bodySmall
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = {
                val cached = BridgeAccessibilityService.getCachedTree(targetPackage)
                dump = cached
                    ?: "(no cached tree yet)\n\nTarget app ko kholke kuch seconds foreground me rakho,\nphir yahan wapas aake try karo."
                showDump = true
                LogRepo.info("Cache dump: ${dump.length} chars")
            }) { Text("Dump target tree") }
            Button(onClick = {
                val inst = BridgeAccessibilityService.instance
                dump = inst?.dumpActiveTree() ?: "(service not connected)"
                showDump = true
            }) { Text("Dump active") }
            Button(onClick = { showDump = false; dump = "" }) { Text("Clear") }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        if (showDump) {
            Text(
                dump,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            )
        } else {
            Text(
                "Steps:\n" +
                    "1) Bridge tab → target DeepSeek app choose karo.\n" +
                    "2) DeepSeek app launch karo, 2-3 sec wait karo.\n" +
                    "3) Wapas SaMu Bridge me aao, yahan 'Dump target tree' dabao.\n" +
                    "4) Output copy karke mujhe paste karo.\n\n" +
                    "Note: 'Dump active' sirf SaMu Bridge ka apna tree dega, isliye cache wala button use karo.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

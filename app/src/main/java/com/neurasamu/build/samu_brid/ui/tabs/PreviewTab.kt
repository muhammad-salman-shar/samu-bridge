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
fun PreviewTab() {
    var dump by remember { mutableStateOf("") }
    var showDump by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = {
                val inst = BridgeAccessibilityService.instance
                dump = inst?.dumpTree() ?: "(accessibility service not connected)"
                showDump = true
                LogRepo.info("Tree dump requested (${dump.length} chars)")
            }) { Text("Dump target tree") }
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
                "Preview tab.\n\n" +
                    "- 'Dump target tree' se currently active app (jo bhi screen pe hai) ka accessibility tree dikhega.\n" +
                    "- Ye output paste karke hum DeepSeek app ke input field aur response container ke exact view IDs dhundhenge.\n" +
                    "- Screen mirror (Accessibility screenshot) aur live step highlight agle batch me.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

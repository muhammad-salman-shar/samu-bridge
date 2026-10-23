package com.neurasamu.build.samu_brid.ui.tabs

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.neurasamu.build.samu_brid.accessibility.BridgeAccessibilityService
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun PreviewTab(targetPackage: String) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var ageSec by remember { mutableStateOf(-1L) }
    var live by remember { mutableStateOf(true) }

    LaunchedEffect(targetPackage, live) {
        while (isActive && live) {
            val svc = BridgeAccessibilityService.instance
            if (svc == null) { bitmap = null; ageSec = -1; delay(800); continue }
            bitmap = BridgeAccessibilityService.lastTargetFrame
            val at = BridgeAccessibilityService.lastTargetFrameAt
            ageSec = if (at == 0L) -1 else (System.currentTimeMillis() - at) / 1000
            delay(500)
        }
    }

    Column(Modifier.fillMaxSize().padding(8.dp)) {
        Text(
            "Target: " + if (targetPackage.isBlank()) "(not set)" else targetPackage,
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            when {
                ageSec < 0 -> "No frame yet — DeepSeek app foreground me kholo, 2–3 sec wait, wapas aao."
                ageSec <= 5 -> "Live (${ageSec}s ago)"
                else -> "STALE (${ageSec}s ago) — DeepSeek app ko foreground me kholke 2–3 sec ruko"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (ageSec in 0..5) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
            Button(onClick = { live = !live }) { Text(if (live) "Pause" else "Resume") }
            Button(onClick = {
                bitmap = null; ageSec = -1; live = true
            }) { Text("Clear") }
        }
        HorizontalDivider()

        val b = bitmap
        if (b != null) {
            val ratio = b.width.toFloat() / b.height.toFloat().coerceAtLeast(1f)
            Image(
                bitmap = b.asImageBitmap(),
                contentDescription = "Target app preview",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().aspectRatio(ratio)
            )
        } else {
            Box(Modifier.fillMaxWidth().aspectRatio(0.5f), contentAlignment = Alignment.Center) {
                Text(
                    "Preview khali hai.\n\n1) Bridge tab me 'Launch' dabao\n2) DeepSeek app load hone do\n3) 2-3 sec wait\n4) Bridge tab me wapas aake yahan dekho",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

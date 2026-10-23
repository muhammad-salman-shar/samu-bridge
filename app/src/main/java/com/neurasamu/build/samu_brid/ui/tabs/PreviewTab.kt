package com.neurasamu.build.samu_brid.ui.tabs

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun PreviewTab(targetPackage: String) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var loading by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var live by remember { mutableStateOf(true) }

    LaunchedEffect(targetPackage, live) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            err = "Screen mirror requires Android 11+ (API 30)."
            return@LaunchedEffect
        }
        while (isActive && live) {
            val svc = BridgeAccessibilityService.instance
            if (svc == null) { err = "Accessibility service not connected"; delay(1500); continue }
            loading = bitmap == null
            val bmp = withTimeoutOrNull(3000) { svc.takeScreenshotBitmap() }
            if (bmp != null) {
                bitmap = bmp
                err = null
            } else if (bitmap == null) {
                err = "Screenshot failed or timed out"
            }
            loading = false
            delay(1200)
        }
    }

    Column(Modifier.fillMaxSize().padding(8.dp)) {
        Text(
            "Target: " + if (targetPackage.isBlank()) "(not set)" else targetPackage,
            style = MaterialTheme.typography.bodySmall
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
            Button(onClick = { live = !live }) { Text(if (live) "Pause" else "Resume") }
            Button(onClick = { bitmap = null; err = null; live = true }) { Text("Refresh") }
        }
        HorizontalDivider()

        val b = bitmap
        if (b != null) {
            val ratio = b.width.toFloat() / b.height.toFloat().coerceAtLeast(1f)
            Image(
                bitmap = b.asImageBitmap(),
                contentDescription = "Live screen",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().aspectRatio(ratio)
            )
        } else if (loading) {
            Box(Modifier.fillMaxWidth().aspectRatio(0.5f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (err != null) {
            Text(err ?: "", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp))
        } else {
            Text(
                "Live mirror starting...\nMake sure target app is foreground.\nAndroid 11+ required.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}

package com.neurasamu.build.samu_brid.ui

import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.neurasamu.build.samu_brid.data.BridgeSettings
import com.neurasamu.build.samu_brid.data.LogRepo
import com.neurasamu.build.samu_brid.data.SettingsRepo
import com.neurasamu.build.samu_brid.ui.components.BridgeTopBar
import com.neurasamu.build.samu_brid.ui.components.LogList
import com.neurasamu.build.samu_brid.ui.components.SettingsSheet
import com.neurasamu.build.samu_brid.ui.components.WebViewBox
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val ctx = LocalContext.current
    val repo = remember { SettingsRepo(ctx) }
    val scope = rememberCoroutineScope()

    var settings by remember { mutableStateOf(BridgeSettings()) }
    var urlInput by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val status = "Idle — server not started (Phase 3)"

    LaunchedEffect(Unit) {
        repo.flow.collect { s ->
            settings = s
            if (urlInput.isBlank()) urlInput = s.websiteUrl
        }
    }

    BackHandler(enabled = webView?.canGoBack() == true) { webView?.goBack() }

    Scaffold(topBar = { BridgeTopBar(onSettings = { showSettings = true }) }) { pad ->
        Column(modifier = Modifier.padding(pad).fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("Website URL") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    val u = urlInput.trim()
                    if (u.isNotBlank()) {
                        val full = if (!u.startsWith("http")) "https://$u" else u
                        webView?.loadUrl(full)
                        LogRepo.info("Loading $full")
                        scope.launch { repo.save(settings.copy(websiteUrl = full)) }
                    }
                }) { Text("Go") }
            }
            WebViewBox(
                initialUrl = settings.websiteUrl,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                onReady = { webView = it }
            )
            HorizontalDivider()
            Text(
                "Status: $status",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodySmall
            )
            HorizontalDivider()
            Text(
                "Logs",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium
            )
            LogList(modifier = Modifier.weight(0.4f).fillMaxWidth())
        }
    }

    if (showSettings) {
        SettingsSheet(
            initial = settings,
            onDismiss = { showSettings = false },
            onSave = { newS ->
                scope.launch { repo.save(newS) }
                settings = newS
                showSettings = false
                LogRepo.info("Settings saved (port=${newS.port}, model=${newS.modelName})")
            }
        )
    }
}

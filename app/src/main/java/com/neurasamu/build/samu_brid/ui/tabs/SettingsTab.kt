package com.neurasamu.build.samu_brid.ui.tabs

import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.neurasamu.build.samu_brid.accessibility.BridgeAccessibilityService
import com.neurasamu.build.samu_brid.core.AppLauncher
import com.neurasamu.build.samu_brid.core.InstalledApp
import com.neurasamu.build.samu_brid.data.BridgeSettings
import com.neurasamu.build.samu_brid.data.LogRepo
import com.neurasamu.build.samu_brid.data.SettingsRepo
import com.neurasamu.build.samu_brid.server.BridgeServerService
import com.neurasamu.build.samu_brid.ui.components.AppPickerSheet
import com.neurasamu.build.samu_brid.ui.components.LogList
import com.neurasamu.build.samu_brid.ui.components.SettingsSheet
import kotlinx.coroutines.launch

@Composable
fun SettingsTab(settings: BridgeSettings, onSettingsChanged: (BridgeSettings) -> Unit) {
    val ctx = LocalContext.current
    val repo = remember { SettingsRepo(ctx) }
    val scope = rememberCoroutineScope()
    var showPicker by remember { mutableStateOf(false) }
    var showApiEdit by remember { mutableStateOf(false) }
    var accessibilityOn by remember { mutableStateOf(false) }
    var serverOn by remember { mutableStateOf(BridgeServerService.isRunning) }
    var result by remember { mutableStateOf("") }
    val apps = remember { AppLauncher.listInstalled(ctx) }

    LaunchedEffect(Unit) { accessibilityOn = AppLauncher.isAccessibilityEnabled() }
    LaunchedEffect(settings.targetPackage) {
        BridgeAccessibilityService.targetPackage = settings.targetPackage
        if (settings.targetPackage.isNotBlank()) LogRepo.info("Service target = ${settings.targetPackage}")
    }
    LaunchedEffect(Unit) {
        while (true) { serverOn = BridgeServerService.isRunning; kotlinx.coroutines.delay(1500) }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Target app", style = MaterialTheme.typography.labelLarge)
        Text(
            if (settings.targetPackage.isBlank()) "Not selected"
            else "${settings.targetLabel.ifBlank { "(no label)" }}\n${settings.targetPackage}",
            style = MaterialTheme.typography.bodyMedium
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { showPicker = true }) { Text("Choose app") }
            Button(onClick = { AppLauncher.launchApp(ctx, settings.targetPackage) },
                enabled = settings.targetPackage.isNotBlank()) { Text("Launch") }
        }
        HorizontalDivider()

        Text("Accessibility service", style = MaterialTheme.typography.labelLarge)
        Text(if (accessibilityOn) "ON" else "OFF", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { AppLauncher.openAccessibilitySettings(ctx) }) { Text("Open settings") }
            Button(onClick = { accessibilityOn = AppLauncher.isAccessibilityEnabled() }) { Text("Refresh") }
        }
        HorizontalDivider()

        Text("API", style = MaterialTheme.typography.labelLarge)
        Text("Model: ${settings.modelName}", style = MaterialTheme.typography.bodyMedium)
        Text("Token: " + if (settings.apiToken.isBlank()) "(empty)" else "\u2022\u2022\u2022\u2022" + settings.apiToken.takeLast(4),
            style = MaterialTheme.typography.bodyMedium)
        Text("Port: ${settings.port}", style = MaterialTheme.typography.bodyMedium)
        Text("Base URL: http://127.0.0.1:${settings.port}/v1", style = MaterialTheme.typography.bodySmall)
        Button(onClick = { showApiEdit = true }) { Text("Edit API settings") }
        HorizontalDivider()

        Text("Server", style = MaterialTheme.typography.labelLarge)
        Text(
            if (serverOn) "Running on http://127.0.0.1:${BridgeServerService.boundPort}/v1" else "Stopped",
            style = MaterialTheme.typography.bodyMedium
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val intent = Intent(ctx, BridgeServerService::class.java).apply {
                    if (BridgeServerService.isRunning) action = BridgeServerService.ACTION_STOP
                    else {
                        putExtra("port", settings.port)
                        putExtra("token", settings.apiToken)
                        putExtra("model", settings.modelName)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !BridgeServerService.isRunning)
                    ctx.startForegroundService(intent)
                else ctx.startService(intent)
                scope.launch { kotlinx.coroutines.delay(700); serverOn = BridgeServerService.isRunning }
            }) { Text(if (serverOn) "Stop server" else "Start server") }
            Button(onClick = {
                scope.launch {
                    result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        runCatching {
                            val u = java.net.URL("http://127.0.0.1:${settings.port}/v1/models")
                            val c = u.openConnection() as java.net.HttpURLConnection
                            c.setRequestProperty("Authorization", "Bearer ${settings.apiToken}")
                            c.connectTimeout = 3000
                            val code = c.responseCode
                            val body = (if (code in 200..299) c.inputStream else c.errorStream)
                                .bufferedReader().readText()
                            "HTTP $code\n$body"
                        }.getOrElse { "ERR: ${it.message}" }
                    }
                }
            }) { Text("Test /v1/models") }
        }
        if (result.isNotBlank()) Text(result, style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()

        Text("Logs", style = MaterialTheme.typography.labelLarge)
        LogList(modifier = Modifier.height(200.dp).fillMaxWidth())
        Spacer(Modifier.height(4.dp))
    }

    if (showPicker) {
        AppPickerSheet(apps = apps, onDismiss = { showPicker = false }, onPick = { app: InstalledApp ->
            scope.launch {
                val ns = settings.copy(targetPackage = app.packageName, targetLabel = app.label)
                repo.save(ns); onSettingsChanged(ns)
            }
            showPicker = false
            LogRepo.info("Target set: ${app.label} (${app.packageName})")
        })
    }
    if (showApiEdit) {
        SettingsSheet(
            initial = settings,
            onDismiss = { showApiEdit = false },
            onSave = { ns ->
                scope.launch { repo.save(ns) }
                onSettingsChanged(ns); showApiEdit = false
                LogRepo.info("API settings saved")
            }
        )
    }
}

package com.neurasamu.build.samu_brid.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.neurasamu.build.samu_brid.data.BridgeSettings
import com.neurasamu.build.samu_brid.data.SettingsRepo
import com.neurasamu.build.samu_brid.ui.components.BridgeTopBar
import com.neurasamu.build.samu_brid.ui.tabs.BridgeTab
import com.neurasamu.build.samu_brid.ui.tabs.SettingsTab

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val ctx = LocalContext.current
    val repo = remember { SettingsRepo(ctx) }
    var tab by remember { mutableStateOf(0) }
    var settings by remember { mutableStateOf(BridgeSettings()) }

    LaunchedEffect(Unit) { repo.flow.collect { settings = it } }

    Scaffold(
        topBar = { BridgeTopBar() },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.Chat, null) },
                    label = { Text("Chat") }
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.Settings, null) },
                    label = { Text("Settings") }
                )
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> BridgeTab(settings = settings)
                else -> SettingsTab(settings = settings, onSettingsChanged = { settings = it })
            }
        }
    }
}

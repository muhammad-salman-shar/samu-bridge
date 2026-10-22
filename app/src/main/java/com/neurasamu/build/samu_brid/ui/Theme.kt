package com.neurasamu.build.samu_brid.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF1E88E5),
    secondary = Color(0xFF1565C0)
)
private val Dark = darkColorScheme(
    primary = Color(0xFF64B5F6),
    secondary = Color(0xFF42A5F5)
)

@Composable
fun SamuBridgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        content = content
    )
}

package com.neurasamu.build.samu_brid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.neurasamu.build.samu_brid.ui.MainScreen
import com.neurasamu.build.samu_brid.ui.SamuBridgeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SamuBridgeTheme { MainScreen() }
        }
    }
}

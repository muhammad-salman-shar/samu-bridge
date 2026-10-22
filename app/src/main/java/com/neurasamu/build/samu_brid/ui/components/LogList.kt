package com.neurasamu.build.samu_brid.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neurasamu.build.samu_brid.data.LogRepo

@Composable
fun LogList(modifier: Modifier = Modifier) {
    val logs by LogRepo.logs.collectAsState()
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        items(logs.reversed()) { e ->
            Text(
                "${e.time} [${e.level}] ${e.message}",
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)
            )
        }
    }
}

package com.neurasamu.build.samu_brid.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.neurasamu.build.samu_brid.core.InstalledApp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerSheet(
    apps: List<InstalledApp>,
    onDismiss: () -> Unit,
    onPick: (InstalledApp) -> Unit
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, apps) {
        if (query.isBlank()) apps
        else apps.filter { it.label.contains(query, true) || it.packageName.contains(query, true) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f).padding(16.dp)) {
            Text("Choose target app", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search (e.g. deepseek)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            )
            LazyColumn(Modifier.fillMaxWidth().fillMaxHeight()) {
                items(filtered, key = { it.packageName }) { app ->
                    Column(
                        Modifier.fillMaxWidth()
                            .clickable { onPick(app) }
                            .padding(vertical = 10.dp)
                    ) {
                        Text(app.label, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            app.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

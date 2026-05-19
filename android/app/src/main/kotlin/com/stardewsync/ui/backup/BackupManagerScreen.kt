package com.stardewsync.ui.backup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stardewsync.data.api.ApiClient
import com.stardewsync.data.prefs.AppPreferences
import com.stardewsync.service.FileAccessService
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupManagerScreen(
    api: ApiClient,
    fileAccess: FileAccessService,
    prefs: AppPreferences,
) {
    val vm: BackupManagerViewModel = viewModel(
        factory = BackupManagerViewModelFactory(api, fileAccess, prefs)
    )
    val state by vm.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val fmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)

    LaunchedEffect(state.statusMessage) {
        state.statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearStatus()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Backup Manager") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (state.isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    Text(
                        "On This Device",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                if (state.localBackups.isEmpty()) {
                    item {
                        Text(
                            "No local backups",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                } else {
                    items(state.localBackups) { entry ->
                        BackupRow(
                            entry = entry,
                            dateLabel = fmt.format(Date(entry.timestampMs)),
                            onDelete = { vm.confirmDeleteLocal(entry) },
                        )
                    }
                }

                item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

                item {
                    Text(
                        "On Server",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                if (state.serverBackups.isEmpty()) {
                    item {
                        Text(
                            "No server backups",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                } else {
                    items(state.serverBackups) { entry ->
                        BackupRow(
                            entry = entry,
                            dateLabel = fmt.format(Date(entry.timestampMs)),
                            onDelete = { vm.confirmDeleteServer(entry) },
                        )
                    }
                }
            }
        }
    }

    state.pendingDeleteLocal?.let { entry ->
        DeleteConfirmDialog(
            name = entry.name,
            onConfirm = { vm.executeDeleteLocal() },
            onDismiss = { vm.cancelDelete() },
        )
    }

    state.pendingDeleteServer?.let { entry ->
        DeleteConfirmDialog(
            name = entry.name,
            onConfirm = { vm.executeDeleteServer() },
            onDismiss = { vm.cancelDelete() },
        )
    }
}

@Composable
private fun BackupRow(entry: BackupEntry, dateLabel: String, onDelete: () -> Unit) {
    ListItem(
        headlineContent = { Text(entry.slotId) },
        supportingContent = { Text(dateLabel) },
        trailingContent = {
            Row {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete backup",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
    )
}

@Composable
private fun DeleteConfirmDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete backup?") },
        text = { Text("This will permanently delete:\n$name") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

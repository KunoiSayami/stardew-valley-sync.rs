package com.stardewsync.ui.backup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
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

            TabRow(selectedTabIndex = state.selectedTab) {
                Tab(
                    selected = state.selectedTab == 0,
                    onClick = { vm.selectTab(0) },
                    text = { Text("Client") },
                )
                Tab(
                    selected = state.selectedTab == 1,
                    onClick = { vm.selectTab(1) },
                    text = { Text("Server") },
                )
            }

            when (state.selectedTab) {
                0 -> BackupTabContent(
                    backups = state.localBackups,
                    emptyText = "No local backups",
                    fmt = fmt,
                    onDelete = { vm.confirmDeleteLocal(it) },
                    onAutoDelete = { vm.showPurgeLocalDialog() },
                )
                1 -> BackupTabContent(
                    backups = state.serverBackups,
                    emptyText = "No server backups",
                    fmt = fmt,
                    onDelete = { vm.confirmDeleteServer(it) },
                    onAutoDelete = { vm.showPurgeServerDialog() },
                )
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

    if (state.showPurgeLocalDialog) {
        AutoDeleteDialog(
            onConfirm = { days -> vm.purgeLocalBackups(days) },
            onDismiss = { vm.dismissPurgeDialog() },
        )
    }

    if (state.showPurgeServerDialog) {
        AutoDeleteDialog(
            onConfirm = { days -> vm.purgeServerBackups(days) },
            onDismiss = { vm.dismissPurgeDialog() },
        )
    }
}

@Composable
private fun BackupTabContent(
    backups: List<BackupEntry>,
    emptyText: String,
    fmt: DateFormat,
    onDelete: (BackupEntry) -> Unit,
    onAutoDelete: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Backups",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onAutoDelete) {
                Text("Auto-delete old…")
            }
        }
        HorizontalDivider()
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (backups.isEmpty()) {
                item {
                    Text(
                        emptyText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            } else {
                items(backups) { entry ->
                    BackupRow(
                        entry = entry,
                        dateLabel = fmt.format(Date(entry.timestampMs)),
                        onDelete = { onDelete(entry) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BackupRow(entry: BackupEntry, dateLabel: String, onDelete: () -> Unit) {
    ListItem(
        headlineContent = { Text(entry.slotId) },
        supportingContent = { Text(dateLabel) },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete backup",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
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

@Composable
private fun AutoDeleteDialog(onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("30") }
    val days = text.toIntOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Auto-delete old backups") },
        text = {
            Column {
                Text("Delete backups older than:")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it.filter { c -> c.isDigit() } },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(80.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("days", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { days?.let { onConfirm(it) } },
                enabled = days != null && days > 0,
            ) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

package com.stardewsync.ui.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.stardewsync.data.api.ApiClient
import com.stardewsync.data.prefs.AppPreferences
import com.stardewsync.service.FileAccessService
import com.stardewsync.ui.navigation.AppNavGraph
import com.stardewsync.ui.sync.SyncDirection
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(
    navController: NavController,
    api: ApiClient,
    fileAccess: FileAccessService,
    prefs: AppPreferences,
    ip: String,
    port: String,
) {
    val vm: SyncViewModel = viewModel(
        factory = SyncViewModelFactory(api, fileAccess, prefs)
    )
    val state by vm.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showPathDialog by remember { mutableStateOf(false) }
    var pathInput by remember { mutableStateOf("") }

    // Resume on lifecycle ON_RESUME
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.onResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Snackbar on status change
    LaunchedEffect(state.statusMessage) {
        state.statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearStatus()
        }
    }

    // Watch for selected_path from directory browser
    val savedPath = navController.currentBackStackEntry
        ?.savedStateHandle
        ?.getStateFlow<String?>("selected_path", null)
        ?.collectAsState()
    LaunchedEffect(savedPath?.value) {
        savedPath?.value?.let { path ->
            vm.setSavesPath(path)
            navController.currentBackStackEntry?.savedStateHandle?.remove<String>("selected_path")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$ip:$port") },
                actions = {
                    if (!state.hasPermission) {
                        IconButton(onClick = { navController.navigate(AppNavGraph.ROUTE_FILE_ACCESS) }) {
                            Icon(Icons.Default.Lock, contentDescription = "File access")
                        }
                    }
                    IconButton(onClick = { navController.navigate(AppNavGraph.ROUTE_FILE_ACCESS) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                    IconButton(onClick = { showPathDialog = true }) {
                        Icon(Icons.Default.Folder, contentDescription = "Saves path")
                    }
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = {
                        vm.disconnect {
                            navController.navigate(AppNavGraph.ROUTE_CONNECT) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Disconnect")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (!state.hasPermission) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "File access permission required",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { vm.requestPermission() }) { Text("Grant") }
                }
            }

            if (state.dirMissing) {
                Text(
                    "Saves directory not found. Tap the folder icon to set a custom path.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            SuggestionChip(
                onClick = { showPathDialog = true },
                label = {
                    Text(
                        state.savesPath ?: fileAccess.getDefaultSavesPath(),
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            if (state.isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            val fmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.serverSlots) { slot ->
                    val localMs = state.localSlots[slot.slotId]
                    val slotErr = state.slotErrors[slot.slotId] ?: emptySet()
                    ListItem(
                        headlineContent = { Text(slot.displayName) },
                        supportingContent = {
                            Column {
                                Text("Server: ${fmt.format(Date(slot.lastModifiedMs))} · ${slot.formattedSize}")
                                Text(if (localMs != null) "Local:  ${fmt.format(Date(localMs))}" else "Local:  —")
                            }
                        },
                        trailingContent = {
                            Row {
                                IconButton(
                                    onClick = { vm.pull(slot) },
                                    enabled = !state.isLoading && state.hasPermission &&
                                        SyncDirection.PULL !in slotErr,
                                ) {
                                    Icon(
                                        Icons.Default.CloudDownload,
                                        contentDescription = "Download",
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                IconButton(
                                    onClick = { vm.push(slot) },
                                    enabled = localMs != null && !state.isLoading &&
                                        state.hasPermission && SyncDirection.PUSH !in slotErr,
                                ) {
                                    Icon(
                                        Icons.Default.CloudUpload,
                                        contentDescription = "Upload",
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    // Conflict dialog
    state.pendingConflict?.let { conflict ->
        ConflictDialog(
            info = conflict,
            onOverwrite = { vm.resolveConflict(overwrite = true) },
            onCancel = { vm.dismissConflict() },
        )
    }

    // Shizuku suggestion dialog
    if (state.showShizukuSuggestion) {
        AlertDialog(
            onDismissRequest = { vm.dismissShizukuSuggestion() },
            title = { Text("Use Shizuku instead?") },
            text = {
                Text(
                    "\"All Files Access\" is granted but the saves directory wasn't found. " +
                        "Shizuku is available and can access save files directly. " +
                        "Would you like to switch to Shizuku mode?"
                )
            },
            confirmButton = {
                FilledTonalButton(onClick = { vm.switchToShizuku() }) { Text("Switch to Shizuku") }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissShizukuSuggestion() }) { Text("No, stay") }
            },
        )
    }

    // Saves path dialog
    if (showPathDialog) {
        pathInput = state.savesPath ?: fileAccess.getDefaultSavesPath()
        AlertDialog(
            onDismissRequest = { showPathDialog = false },
            title = { Text("Saves directory") },
            text = {
                Column {
                    OutlinedTextField(
                        value = pathInput,
                        onValueChange = { pathInput = it },
                        label = { Text("Path") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    val modPath = fileAccess.getModLauncherSavesPath()
                    if (modPath != null) {
                        OutlinedButton(
                            onClick = { pathInput = modPath },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                Icons.Default.Extension,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.size(6.dp))
                            Text("Use mod launcher path")
                        }
                    }
                    TextButton(onClick = {
                        showPathDialog = false
                        navController.navigate(
                            AppNavGraph.browserRoute(pathInput.ifBlank { fileAccess.getDefaultSavesPath() })
                        )
                    }) {
                        Text("Browse...")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.setSavesPath(pathInput.takeIf { it.isNotBlank() })
                    showPathDialog = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = {
                    vm.setSavesPath(null)
                    showPathDialog = false
                }) { Text("Reset to default") }
            },
        )
    }
}

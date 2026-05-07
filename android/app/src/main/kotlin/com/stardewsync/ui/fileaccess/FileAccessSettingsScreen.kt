package com.stardewsync.ui.fileaccess

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.stardewsync.FileAccessMode
import com.stardewsync.service.FileAccessService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileAccessSettingsScreen(navController: NavController, fileAccess: FileAccessService) {
    val vm: FileAccessSettingsViewModel = viewModel(
        factory = FileAccessSettingsViewModelFactory(fileAccess)
    )
    val state by vm.uiState.collectAsState()

    LaunchedEffect(Unit) { vm.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("File Access Method") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                return@Column
            }

            ListItem(
                headlineContent = { Text("All Files Access (MANAGE_STORAGE)") },
                supportingContent = { Text("Standard Android permission for external storage") },
                leadingContent = {
                    RadioButton(
                        selected = state.mode == FileAccessMode.MANAGE_STORAGE,
                        onClick = { vm.selectMode(FileAccessMode.MANAGE_STORAGE) },
                    )
                },
            )

            HorizontalDivider()

            ListItem(
                headlineContent = { Text("Shizuku") },
                supportingContent = {
                    Text(if (state.shizukuAvailable) "Shizuku is available" else "Shizuku is not running")
                },
                leadingContent = {
                    RadioButton(
                        selected = state.mode == FileAccessMode.SHIZUKU,
                        onClick = { if (state.shizukuAvailable) vm.selectMode(FileAccessMode.SHIZUKU) },
                        enabled = state.shizukuAvailable,
                    )
                },
            )

            HorizontalDivider()

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.hasPermission) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF5B7C3E))
                    Spacer(Modifier.width(8.dp))
                    Text("Permission granted", color = Color(0xFF5B7C3E))
                } else {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Permission not granted", color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { vm.requestPermission() },
                            enabled = !state.isRequesting,
                        ) {
                            if (state.isRequesting)
                                CircularProgressIndicator(modifier = Modifier.padding(horizontal = 8.dp))
                            else
                                Text("Grant access")
                        }
                    }
                }
            }
        }
    }
}

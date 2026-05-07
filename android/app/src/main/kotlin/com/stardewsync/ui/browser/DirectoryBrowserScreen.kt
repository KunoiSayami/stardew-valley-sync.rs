package com.stardewsync.ui.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.stardewsync.service.FileAccessService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectoryBrowserScreen(
    navController: NavController,
    fileAccess: FileAccessService,
    initialPath: String,
) {
    val vm: DirectoryBrowserViewModel = viewModel(
        factory = DirectoryBrowserViewModelFactory(fileAccess)
    )
    val state by vm.uiState.collectAsState()

    LaunchedEffect(Unit) {
        if (state.currentPath.isEmpty()) vm.loadPath(initialPath, "/")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Choose folder") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (state.breadcrumbs.size > 1) vm.navigateUp()
                        else navController.popBackStack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set("selected_path", state.currentPath)
                        navController.popBackStack()
                    }) {
                        Text("Select")
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                state.breadcrumbs.forEachIndexed { index, crumb ->
                    if (index > 0) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.padding(horizontal = 2.dp),
                        )
                    }
                    val isLast = index == state.breadcrumbs.lastIndex
                    TextButton(onClick = { if (!isLast) vm.navigateToBreadcrumb(index) }) {
                        Text(
                            text = crumb.label,
                            style = if (isLast) MaterialTheme.typography.labelMedium
                            else MaterialTheme.typography.bodySmall,
                            color = if (isLast) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            HorizontalDivider()

            if (state.isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.entries) { entry ->
                    ListItem(
                        headlineContent = { Text(entry.name) },
                        leadingContent = { Icon(Icons.Default.Folder, contentDescription = null) },
                        trailingContent = {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                        },
                        modifier = Modifier.clickable { vm.loadPath(entry.path, entry.name) },
                    )
                }
            }
        }
    }
}

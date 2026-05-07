package com.stardewsync.ui.connect

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.stardewsync.data.prefs.AppPreferences
import com.stardewsync.service.DiscoveryService
import com.stardewsync.ui.navigation.AppNavGraph

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerConnectScreen(
    navController: NavController,
    prefs: AppPreferences,
    discovery: DiscoveryService,
) {
    val vm: ServerConnectViewModel = viewModel(
        factory = ServerConnectViewModelFactory(prefs, discovery)
    )
    val state by vm.uiState.collectAsState()

    LaunchedEffect(Unit) { vm.scan() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Connect to PC Server") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Discovered servers", style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = { vm.scan() }, enabled = !state.isScanning) {
                    if (state.isScanning)
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    else
                        Icon(Icons.Default.Refresh, contentDescription = "Scan")
                }
            }

            if (state.discovered.isEmpty() && !state.isScanning) {
                Text("No servers found", style = MaterialTheme.typography.bodyMedium)
            }

            state.discovered.forEach { server ->
                ListItem(
                    headlineContent = { Text(server.toString()) },
                    modifier = Modifier.clickable { vm.selectDiscovered(server) },
                )
            }

            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            Text("Manual entry", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.ip,
                onValueChange = vm::updateIp,
                label = { Text("IP address") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.port,
                onValueChange = vm::updatePort,
                label = { Text("Port") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.pin,
                onValueChange = vm::updatePin,
                label = { Text("PIN") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            )
            if (state.error != null) {
                Spacer(Modifier.height(4.dp))
                Text(state.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Button(
                    onClick = {
                        vm.connect(state.ip, state.port, state.pin) { ip, port, pin ->
                            navController.navigate(AppNavGraph.syncRoute(ip, port, pin)) {
                                popUpTo(AppNavGraph.ROUTE_CONNECT) { inclusive = true }
                            }
                        }
                    },
                    enabled = !state.isConnecting,
                ) {
                    if (state.isConnecting)
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    else
                        Text("Connect")
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

package com.stardewsync.ui.connect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stardewsync.data.api.ApiClient
import com.stardewsync.data.model.DiscoveredServer
import com.stardewsync.data.prefs.AppPreferences
import com.stardewsync.service.DiscoveryService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConnectUiState(
    val ip: String = "",
    val port: String = "24742",
    val pin: String = "",
    val discovered: List<DiscoveredServer> = emptyList(),
    val isScanning: Boolean = false,
    val isConnecting: Boolean = false,
    val error: String? = null,
)

class ServerConnectViewModel(
    private val prefs: AppPreferences,
    private val discovery: DiscoveryService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConnectUiState())
    val uiState: StateFlow<ConnectUiState> = _uiState

    fun updateIp(value: String) = _uiState.update { it.copy(ip = value, error = null) }
    fun updatePort(value: String) = _uiState.update { it.copy(port = value, error = null) }
    fun updatePin(value: String) = _uiState.update { it.copy(pin = value, error = null) }

    fun selectDiscovered(server: DiscoveredServer) {
        _uiState.update { it.copy(ip = server.host, port = server.port.toString(), error = null) }
    }

    fun scan() {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, discovered = emptyList(), error = null) }
            try {
                val servers = discovery.discoverServers()
                _uiState.update { it.copy(discovered = servers) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Discovery failed: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isScanning = false) }
            }
        }
    }

    fun connect(ip: String, port: String, pin: String, onSuccess: (String, String, String) -> Unit) {
        val portInt = port.toIntOrNull()
        if (ip.isBlank() || portInt == null || pin.isBlank()) {
            _uiState.update { it.copy(error = "Please fill in all fields") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isConnecting = true, error = null) }
            try {
                val baseUrl = "http://$ip:$port"
                ApiClient(baseUrl, pin).health()
                prefs.serverIp = ip
                prefs.serverPort = port
                prefs.serverPin = pin
                onSuccess(ip, port, pin)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Could not connect: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isConnecting = false) }
            }
        }
    }
}

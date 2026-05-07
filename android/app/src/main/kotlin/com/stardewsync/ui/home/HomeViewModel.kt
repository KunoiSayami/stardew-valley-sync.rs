package com.stardewsync.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stardewsync.data.prefs.AppPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class HomeDestination {
    object Loading : HomeDestination()
    object Connect : HomeDestination()
    data class Sync(val ip: String, val port: String, val pin: String) : HomeDestination()
}

class HomeViewModel(private val prefs: AppPreferences) : ViewModel() {

    private val _destination = MutableStateFlow<HomeDestination>(HomeDestination.Loading)
    val destination: StateFlow<HomeDestination> = _destination

    init {
        viewModelScope.launch {
            val ip = prefs.serverIp
            val pin = prefs.serverPin
            _destination.value = if (!ip.isNullOrBlank() && !pin.isNullOrBlank())
                HomeDestination.Sync(ip, prefs.serverPort, pin)
            else
                HomeDestination.Connect
        }
    }
}

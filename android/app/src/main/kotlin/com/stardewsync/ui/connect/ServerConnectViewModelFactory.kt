package com.stardewsync.ui.connect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.stardewsync.data.prefs.AppPreferences
import com.stardewsync.service.DiscoveryService

class ServerConnectViewModelFactory(
    private val prefs: AppPreferences,
    private val discovery: DiscoveryService,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return ServerConnectViewModel(prefs, discovery) as T
    }
}

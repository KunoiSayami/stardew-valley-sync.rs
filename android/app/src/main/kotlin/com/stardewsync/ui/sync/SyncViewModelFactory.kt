package com.stardewsync.ui.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.stardewsync.data.api.ApiClient
import com.stardewsync.data.prefs.AppPreferences
import com.stardewsync.service.FileAccessService

class SyncViewModelFactory(
    private val api: ApiClient,
    private val fileAccess: FileAccessService,
    private val prefs: AppPreferences,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return SyncViewModel(api, fileAccess, prefs) as T
    }
}

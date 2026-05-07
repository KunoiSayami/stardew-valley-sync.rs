package com.stardewsync.ui.fileaccess

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.stardewsync.service.FileAccessService

class FileAccessSettingsViewModelFactory(private val fileAccess: FileAccessService) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return FileAccessSettingsViewModel(fileAccess) as T
    }
}

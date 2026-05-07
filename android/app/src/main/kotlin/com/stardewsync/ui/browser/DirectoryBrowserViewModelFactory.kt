package com.stardewsync.ui.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.stardewsync.service.FileAccessService

class DirectoryBrowserViewModelFactory(private val fileAccess: FileAccessService) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return DirectoryBrowserViewModel(fileAccess) as T
    }
}

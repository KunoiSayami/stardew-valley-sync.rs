package com.stardewsync.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.stardewsync.data.prefs.AppPreferences

class HomeViewModelFactory(private val prefs: AppPreferences) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return HomeViewModel(prefs) as T
    }
}

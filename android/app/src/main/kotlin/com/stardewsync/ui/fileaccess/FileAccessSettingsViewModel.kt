package com.stardewsync.ui.fileaccess

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stardewsync.FileAccessMode
import com.stardewsync.service.FileAccessService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FileAccessUiState(
    val mode: FileAccessMode = FileAccessMode.MANAGE_STORAGE,
    val shizukuAvailable: Boolean = false,
    val hasPermission: Boolean = false,
    val isLoading: Boolean = true,
    val isRequesting: Boolean = false,
)

class FileAccessSettingsViewModel(private val fileAccess: FileAccessService) : ViewModel() {

    private val _uiState = MutableStateFlow(FileAccessUiState())
    val uiState: StateFlow<FileAccessUiState> = _uiState

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val shizuku = fileAccess.isShizukuAvailable()
            val hasPerm = fileAccess.hasPermission()
            _uiState.update {
                it.copy(
                    mode = fileAccess.currentMode,
                    shizukuAvailable = shizuku,
                    hasPermission = hasPerm,
                    isLoading = false,
                )
            }
        }
    }

    fun selectMode(mode: FileAccessMode) {
        fileAccess.currentMode = mode
        _uiState.update { it.copy(mode = mode) }
        load()
    }

    fun requestPermission() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRequesting = true) }
            val granted = fileAccess.requestPermission()
            _uiState.update { it.copy(hasPermission = granted, isRequesting = false) }
        }
    }
}

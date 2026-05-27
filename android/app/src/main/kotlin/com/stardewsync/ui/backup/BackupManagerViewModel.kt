package com.stardewsync.ui.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stardewsync.data.api.ApiClient
import com.stardewsync.data.api.BackupInfo
import com.stardewsync.data.prefs.AppPreferences
import com.stardewsync.service.FileAccessService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BackupEntry(val name: String, val slotId: String, val timestampMs: Long)

data class BackupUiState(
    val selectedTab: Int = 0,
    val localBackups: List<BackupEntry> = emptyList(),
    val serverBackups: List<BackupEntry> = emptyList(),
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val pendingDeleteLocal: BackupEntry? = null,
    val pendingDeleteServer: BackupEntry? = null,
    val showPurgeLocalDialog: Boolean = false,
    val showPurgeServerDialog: Boolean = false,
)

class BackupManagerViewModel(
    private val api: ApiClient,
    private val fileAccess: FileAccessService,
    private val prefs: AppPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState

    init {
        refresh()
    }

    fun selectTab(index: Int) = _uiState.update { it.copy(selectedTab = index) }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val local = runCatching {
                fileAccess.listBackups(prefs.savesPath).map {
                    BackupEntry(
                        name = it["name"] as String,
                        slotId = it["slotId"] as String,
                        timestampMs = it["timestampMs"] as Long,
                    )
                }
            }.getOrElse { emptyList() }

            val server = runCatching {
                api.listServerBackups().map { BackupEntry(it.name, it.slotId, it.timestampMs) }
            }.getOrElse { emptyList() }

            _uiState.update { it.copy(isLoading = false, localBackups = local, serverBackups = server) }
        }
    }

    fun confirmDeleteLocal(entry: BackupEntry) = _uiState.update { it.copy(pendingDeleteLocal = entry) }
    fun confirmDeleteServer(entry: BackupEntry) = _uiState.update { it.copy(pendingDeleteServer = entry) }
    fun cancelDelete() = _uiState.update { it.copy(pendingDeleteLocal = null, pendingDeleteServer = null) }

    fun executeDeleteLocal() {
        val entry = _uiState.value.pendingDeleteLocal ?: return
        _uiState.update { it.copy(pendingDeleteLocal = null) }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                fileAccess.deleteBackup(entry.name, prefs.savesPath)
                _uiState.update { it.copy(statusMessage = "Deleted ${entry.name}") }
            } catch (e: Exception) {
                _uiState.update { it.copy(statusMessage = "Delete failed: ${e.message}") }
            }
            refresh()
        }
    }

    fun executeDeleteServer() {
        val entry = _uiState.value.pendingDeleteServer ?: return
        _uiState.update { it.copy(pendingDeleteServer = null) }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                api.deleteServerBackup(entry.name)
                _uiState.update { it.copy(statusMessage = "Deleted ${entry.name}") }
            } catch (e: Exception) {
                _uiState.update { it.copy(statusMessage = "Delete failed: ${e.message}") }
            }
            refresh()
        }
    }

    fun showPurgeLocalDialog() = _uiState.update { it.copy(showPurgeLocalDialog = true) }
    fun showPurgeServerDialog() = _uiState.update { it.copy(showPurgeServerDialog = true) }
    fun dismissPurgeDialog() = _uiState.update { it.copy(showPurgeLocalDialog = false, showPurgeServerDialog = false) }

    fun purgeLocalBackups(days: Int) {
        _uiState.update { it.copy(showPurgeLocalDialog = false) }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val cutoffMs = System.currentTimeMillis() - days.toLong() * 86_400_000L
                val toDelete = _uiState.value.localBackups.filter { it.timestampMs < cutoffMs }
                var count = 0
                for (entry in toDelete) {
                    runCatching { fileAccess.deleteBackup(entry.name, prefs.savesPath) }
                        .onSuccess { count++ }
                }
                _uiState.update { it.copy(statusMessage = "Deleted $count backup(s) older than $days day(s)") }
            } catch (e: Exception) {
                _uiState.update { it.copy(statusMessage = "Purge failed: ${e.message}") }
            }
            refresh()
        }
    }

    fun purgeServerBackups(days: Int) {
        _uiState.update { it.copy(showPurgeServerDialog = false) }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val count = api.purgeServerBackups(days)
                _uiState.update { it.copy(statusMessage = "Deleted $count backup(s) older than $days day(s)") }
            } catch (e: Exception) {
                _uiState.update { it.copy(statusMessage = "Purge failed: ${e.message}") }
            }
            refresh()
        }
    }

    fun clearStatus() = _uiState.update { it.copy(statusMessage = null) }
}

class BackupManagerViewModelFactory(
    private val api: ApiClient,
    private val fileAccess: FileAccessService,
    private val prefs: AppPreferences,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return BackupManagerViewModel(api, fileAccess, prefs) as T
    }
}

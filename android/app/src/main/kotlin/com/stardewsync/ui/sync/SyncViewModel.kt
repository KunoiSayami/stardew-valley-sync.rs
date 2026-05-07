package com.stardewsync.ui.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stardewsync.data.api.ApiClient
import com.stardewsync.data.api.ConflictException
import com.stardewsync.data.model.SaveSlotInfo
import com.stardewsync.data.prefs.AppPreferences
import com.stardewsync.service.FileAccessService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SyncDirection { PULL, PUSH }

data class ConflictInfo(
    val slotId: String,
    val serverMs: Long,
    val localMs: Long,
    val direction: SyncDirection,
    val zipBytes: ByteArray? = null, // for pull conflicts
)

data class SyncUiState(
    val serverSlots: List<SaveSlotInfo> = emptyList(),
    val localSlots: Map<String, Long> = emptyMap(),
    val hasPermission: Boolean = false,
    val savesPath: String? = null,
    val dirMissing: Boolean = false,
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val pendingConflict: ConflictInfo? = null,
)

class SyncViewModel(
    private val api: ApiClient,
    private val fileAccess: FileAccessService,
    private val prefs: AppPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SyncUiState())
    val uiState: StateFlow<SyncUiState> = _uiState

    fun onResume() = refresh()

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val hasPerm = fileAccess.hasPermission()
                val savesPath = prefs.savesPath
                val dirExists = fileAccess.savesDirExists(savesPath)
                val serverSlots = api.listSaves()
                val localMap: Map<String, Long> = if (hasPerm && dirExists) {
                    fileAccess.listSaves(savesPath).associate {
                        (it["slotId"] as String) to (it["lastModifiedMs"] as Long)
                    }
                } else emptyMap()

                _uiState.update {
                    it.copy(
                        serverSlots = serverSlots,
                        localSlots = localMap,
                        hasPermission = hasPerm,
                        savesPath = savesPath,
                        dirMissing = hasPerm && !dirExists,
                        isLoading = false,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, statusMessage = "Refresh failed: ${e.message}")
                }
            }
        }
    }

    fun pull(slot: SaveSlotInfo) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val (zipBytes, serverMs) = api.downloadSave(slot.slotId)
                val localMs = _uiState.value.localSlots[slot.slotId] ?: 0L
                if (localMs > serverMs) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            pendingConflict = ConflictInfo(
                                slotId = slot.slotId,
                                serverMs = serverMs,
                                localMs = localMs,
                                direction = SyncDirection.PULL,
                                zipBytes = zipBytes,
                            ),
                        )
                    }
                    return@launch
                }
                fileAccess.writeSave(slot.slotId, zipBytes, prefs.savesPath)
                _uiState.update { it.copy(isLoading = false, statusMessage = "Downloaded ${slot.displayName}") }
                refresh()
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, statusMessage = "Download failed: ${e.message}") }
            }
        }
    }

    fun push(slot: SaveSlotInfo) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val localMs = fileAccess.getSlotModifiedMs(slot.slotId, prefs.savesPath)
                val zipBytes = fileAccess.readSave(slot.slotId, prefs.savesPath)
                api.uploadSave(slot.slotId, zipBytes, localMs)
                _uiState.update { it.copy(isLoading = false, statusMessage = "Uploaded ${slot.displayName}") }
                refresh()
            } catch (e: ConflictException) {
                val localMs = _uiState.value.localSlots[slot.slotId] ?: 0L
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        pendingConflict = ConflictInfo(
                            slotId = slot.slotId,
                            serverMs = e.serverLastModifiedMs,
                            localMs = localMs,
                            direction = SyncDirection.PUSH,
                        ),
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, statusMessage = "Upload failed: ${e.message}") }
            }
        }
    }

    fun resolveConflict(overwrite: Boolean) {
        val conflict = _uiState.value.pendingConflict ?: return
        _uiState.update { it.copy(pendingConflict = null) }
        if (!overwrite) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                when (conflict.direction) {
                    SyncDirection.PULL -> {
                        val zipBytes = conflict.zipBytes ?: api.downloadSave(conflict.slotId).first
                        fileAccess.writeSave(conflict.slotId, zipBytes, prefs.savesPath)
                        _uiState.update { it.copy(statusMessage = "Downloaded ${conflict.slotId}") }
                    }
                    SyncDirection.PUSH -> {
                        val localMs = fileAccess.getSlotModifiedMs(conflict.slotId, prefs.savesPath)
                        val zipBytes = fileAccess.readSave(conflict.slotId, prefs.savesPath)
                        api.uploadSave(conflict.slotId, zipBytes, localMs, force = true)
                        _uiState.update { it.copy(statusMessage = "Uploaded ${conflict.slotId}") }
                    }
                }
                _uiState.update { it.copy(isLoading = false) }
                refresh()
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, statusMessage = "Sync failed: ${e.message}") }
            }
        }
    }

    fun dismissConflict() = _uiState.update { it.copy(pendingConflict = null) }

    fun requestPermission() {
        viewModelScope.launch {
            fileAccess.requestPermission()
            refresh()
        }
    }

    fun setSavesPath(path: String?) {
        prefs.savesPath = path
        refresh()
    }

    fun clearStatus() = _uiState.update { it.copy(statusMessage = null) }

    fun disconnect(onDisconnected: () -> Unit) {
        prefs.clearServerCredentials()
        onDisconnected()
    }
}

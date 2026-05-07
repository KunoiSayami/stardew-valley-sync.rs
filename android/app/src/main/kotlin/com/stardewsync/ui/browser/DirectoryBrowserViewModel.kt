package com.stardewsync.ui.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stardewsync.service.FileAccessService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BreadcrumbItem(val label: String, val path: String)
data class DirectoryEntry(val name: String, val path: String)

data class BrowserUiState(
    val currentPath: String = "",
    val entries: List<DirectoryEntry> = emptyList(),
    val breadcrumbs: List<BreadcrumbItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

class DirectoryBrowserViewModel(private val fileAccess: FileAccessService) : ViewModel() {

    private val _uiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _uiState

    private val breadcrumbStack = mutableListOf<BreadcrumbItem>()

    fun loadPath(path: String, label: String) {
        if (breadcrumbStack.none { it.path == path }) {
            breadcrumbStack.add(BreadcrumbItem(label, path))
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val raw = fileAccess.listDirectory(path)
                val entries = raw.map { DirectoryEntry(it["name"] ?: "", it["path"] ?: "") }
                _uiState.update {
                    it.copy(
                        currentPath = path,
                        entries = entries,
                        breadcrumbs = breadcrumbStack.toList(),
                        isLoading = false,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun navigateUp() {
        if (breadcrumbStack.size > 1) {
            breadcrumbStack.removeLast()
            val prev = breadcrumbStack.last()
            loadPath(prev.path, prev.label)
        }
    }

    fun navigateToBreadcrumb(index: Int) {
        if (index < breadcrumbStack.size) {
            while (breadcrumbStack.size > index + 1) breadcrumbStack.removeLast()
            val crumb = breadcrumbStack[index]
            loadPath(crumb.path, crumb.label)
        }
    }
}

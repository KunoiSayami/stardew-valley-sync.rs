package com.stardewsync

enum class FileAccessMode { MANAGE_STORAGE, SHIZUKU }

interface FileAccessBackend {
    fun hasPermission(): Boolean
    fun requestPermission(onResult: (Boolean) -> Unit)
    fun getDefaultSavesPath(): String
    fun listDirectory(path: String): List<Map<String, String>>
    fun savesDirExists(savesPath: String?): Boolean
    fun listSaves(savesPath: String?): List<Map<String, Any>>
    fun readSave(slotId: String, savesPath: String?): ByteArray
    fun writeSave(slotId: String, data: ByteArray, savesPath: String?, lastModifiedMs: Long? = null)
    fun getSlotModifiedMs(slotId: String, savesPath: String?): Long
}

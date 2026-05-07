package com.stardewsync.service

import android.app.Activity
import android.content.Context
import android.os.Environment
import com.stardewsync.FileAccessBackend
import com.stardewsync.FileAccessMode
import com.stardewsync.ManageStorageBackend
import com.stardewsync.ShizukuBackend
import com.stardewsync.data.prefs.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class FileAccessService(
    private val context: Context,
    private val prefs: AppPreferences,
) {
    companion object {
        private const val MOD_LAUNCHER_PACKAGE = "abc.smapi.gameloader"
    }

    val manageBackend = ManageStorageBackend()
    val shizukuBackend = ShizukuBackend()

    var currentMode: FileAccessMode
        get() = prefs.fileAccessMode
        set(v) { prefs.fileAccessMode = v }

    private val activeBackend: FileAccessBackend
        get() = if (currentMode == FileAccessMode.SHIZUKU) shizukuBackend else manageBackend

    fun onActivityCreated(activity: Activity) {
        manageBackend.activity = activity
        shizukuBackend.registerListeners()
    }

    fun onActivityDestroyed() {
        manageBackend.activity = null
        shizukuBackend.unregisterListeners()
    }

    fun isShizukuAvailable(): Boolean = shizukuBackend.isShizukuAvailable()

    fun getModLauncherSavesPath(): String? {
        val installed = runCatching {
            context.packageManager.getPackageInfo(MOD_LAUNCHER_PACKAGE, 0)
            true
        }.getOrDefault(false)
        if (!installed) return null
        return File(
            Environment.getExternalStorageDirectory(),
            "Android/data/$MOD_LAUNCHER_PACKAGE/files/Saves"
        ).absolutePath
    }

    suspend fun hasPermission(): Boolean = withContext(Dispatchers.IO) {
        activeBackend.hasPermission()
    }

    suspend fun requestPermission(): Boolean = suspendCancellableCoroutine { cont ->
        activeBackend.requestPermission { granted -> cont.resume(granted) }
    }

    fun getDefaultSavesPath(): String = activeBackend.getDefaultSavesPath()

    suspend fun listDirectory(path: String): List<Map<String, String>> = withContext(Dispatchers.IO) {
        activeBackend.listDirectory(path)
    }

    suspend fun savesDirExists(savesPath: String?): Boolean = withContext(Dispatchers.IO) {
        activeBackend.savesDirExists(savesPath)
    }

    suspend fun listSaves(savesPath: String?): List<Map<String, Any>> = withContext(Dispatchers.IO) {
        activeBackend.listSaves(savesPath)
    }

    suspend fun readSave(slotId: String, savesPath: String?): ByteArray = withContext(Dispatchers.IO) {
        activeBackend.readSave(slotId, savesPath)
    }

    suspend fun writeSave(slotId: String, data: ByteArray, savesPath: String?, lastModifiedMs: Long? = null) = withContext(Dispatchers.IO) {
        activeBackend.writeSave(slotId, data, savesPath, lastModifiedMs)
    }

    suspend fun getSlotModifiedMs(slotId: String, savesPath: String?): Long = withContext(Dispatchers.IO) {
        activeBackend.getSlotModifiedMs(slotId, savesPath)
    }
}

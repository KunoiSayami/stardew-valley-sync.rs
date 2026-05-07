package com.stardewsync

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import androidx.core.net.toUri

class ManageStorageBackend : FileAccessBackend {

    var activity: Activity? = null
    private var pendingCallback: ((Boolean) -> Unit)? = null

    companion object {
        const val REQUEST_CODE = 42002
        private const val STARDEW_PACKAGE = "com.chucklefish.stardewvalley"

        fun defaultPath(): String =
            File(
                Environment.getExternalStorageDirectory(),
                "Android/data/$STARDEW_PACKAGE/files/Saves"
            ).absolutePath
    }

    private fun savesDir(customPath: String?): File =
        if (!customPath.isNullOrBlank()) File(customPath) else File(defaultPath())

    override fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

    override fun requestPermission(onResult: (Boolean) -> Unit) {
        if (hasPermission()) { onResult(true); return }
        val act = activity ?: run { onResult(false); return }
        pendingCallback = onResult
        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
            data = "package:${act.packageName}".toUri()
        }
        act.startActivityForResult(intent, REQUEST_CODE)
    }

    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != REQUEST_CODE) return false
        val cb = pendingCallback ?: return false
        pendingCallback = null
        cb(hasPermission())
        return true
    }

    override fun getDefaultSavesPath(): String = defaultPath()

    override fun listDirectory(path: String): List<Map<String, String>> {
        val dir = File(path)
        if (!dir.exists() || !dir.isDirectory) throw RuntimeException("Not a directory: $path")
        return dir.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedBy { it.name.lowercase() }
            ?.map { mapOf("name" to it.name, "path" to it.absolutePath) }
            ?: emptyList()
    }

    override fun savesDirExists(savesPath: String?): Boolean = savesDir(savesPath).exists()

    override fun listSaves(savesPath: String?): List<Map<String, Any>> {
        val dir = savesDir(savesPath)
        if (!dir.exists()) return emptyList()
        return dir.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { slotDir ->
                val mainFile = File(slotDir, slotDir.name).takeIf { it.exists() } ?: return@mapNotNull null
                val infoFile = File(slotDir, "SaveGameInfo").takeIf { it.exists() } ?: return@mapNotNull null
                val lastModMs = maxOf(mainFile.lastModified(), infoFile.lastModified())
                mapOf("slotId" to slotDir.name, "lastModifiedMs" to lastModMs)
            } ?: emptyList()
    }

    override fun readSave(slotId: String, savesPath: String?): ByteArray {
        val slotDir = File(savesDir(savesPath), slotId)
        val mainFile = File(slotDir, slotId).takeIf { it.exists() }
            ?: throw RuntimeException("Main save file not found in $slotId")
        val infoFile = File(slotDir, "SaveGameInfo").takeIf { it.exists() }
            ?: throw RuntimeException("SaveGameInfo not found in $slotId")

        val buf = ByteArrayOutputStream()
        ZipOutputStream(buf).use { zip ->
            fun addEntry(file: File, name: String) {
                zip.putNextEntry(ZipEntry(name))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            addEntry(mainFile, slotId)
            addEntry(infoFile, "SaveGameInfo")
        }
        return buf.toByteArray()
    }

    override fun writeSave(slotId: String, data: ByteArray, savesPath: String?) {
        val savesRoot = savesDir(savesPath)
        savesRoot.mkdirs()

        val existing = File(savesRoot, slotId)
        if (existing.exists()) existing.renameTo(File(savesRoot, "$slotId.bak.${System.currentTimeMillis()}"))

        val newDir = File(savesRoot, slotId).also { it.mkdirs() }
        ZipInputStream(ByteArrayInputStream(data)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                File(newDir, entry.name).outputStream().use { zip.copyTo(it) }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    override fun getSlotModifiedMs(slotId: String, savesPath: String?): Long {
        val slotDir = File(savesDir(savesPath), slotId)
        val mainFile = File(slotDir, slotId)
        val infoFile = File(slotDir, "SaveGameInfo")
        if (!mainFile.exists() || !infoFile.exists()) return 0L
        return maxOf(mainFile.lastModified(), infoFile.lastModified())
    }
}

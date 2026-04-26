package com.stardewsync

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.PluginRegistry
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class SafPlugin : FlutterPlugin, MethodChannel.MethodCallHandler,
    ActivityAware, PluginRegistry.ActivityResultListener {

    private lateinit var channel: MethodChannel
    private var activity: Activity? = null
    private var pendingResult: MethodChannel.Result? = null

    companion object {
        private const val CHANNEL = "com.stardewsync/saf"
        private const val REQUEST_MANAGE_STORAGE = 42002
        private const val STARDEW_PACKAGE = "com.chucklefish.stardewvalley"

        fun defaultSavesPath(): String =
            File(
                Environment.getExternalStorageDirectory(),
                "Android/data/$STARDEW_PACKAGE/files/Saves"
            ).absolutePath

        private fun savesDir(customPath: String?): File =
            if (!customPath.isNullOrBlank()) File(customPath) else File(defaultSavesPath())
    }

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(binding.binaryMessenger, CHANNEL)
        channel.setMethodCallHandler(this)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
        binding.addActivityResultListener(this)
    }

    override fun onDetachedFromActivityForConfigChanges() { activity = null }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activity = binding.activity
        binding.addActivityResultListener(this)
    }

    override fun onDetachedFromActivity() { activity = null }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "checkAndRequestPermission" -> checkAndRequestPermission(result)
            "hasPermission"             -> result.success(hasManageStoragePermission())
            "getDefaultSavesPath"       -> result.success(defaultSavesPath())
            "listSaves"                 -> listSaves(call, result)
            "readSave"                  -> readSave(call, result)
            "writeSave"                 -> writeSave(call, result)
            "getSlotModifiedMs"         -> getSlotModifiedMs(call, result)
            else                        -> result.notImplemented()
        }
    }

    private fun hasManageStoragePermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
            Environment.isExternalStorageManager()

    private fun checkAndRequestPermission(result: MethodChannel.Result) {
        if (hasManageStoragePermission()) {
            result.success(true)
            return
        }
        val act = activity ?: return result.error("NO_ACTIVITY", "No activity", null)
        pendingResult = result
        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
            data = Uri.parse("package:${act.packageName}")
        }
        act.startActivityForResult(intent, REQUEST_MANAGE_STORAGE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != REQUEST_MANAGE_STORAGE) return false
        val res = pendingResult ?: return false
        pendingResult = null
        res.success(hasManageStoragePermission())
        return true
    }

    private fun listSaves(call: MethodCall, result: MethodChannel.Result) {
        val dir = savesDir(call.argument<String>("savesPath"))
        if (!dir.exists()) return result.success(emptyList<Any>())

        val slots = dir.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { slotDir ->
                val mainFile = File(slotDir, slotDir.name).takeIf { it.exists() } ?: return@mapNotNull null
                val infoFile = File(slotDir, "SaveGameInfo").takeIf { it.exists() } ?: return@mapNotNull null
                val lastModMs = maxOf(mainFile.lastModified(), infoFile.lastModified())
                mapOf("slotId" to slotDir.name, "lastModifiedMs" to lastModMs)
            } ?: emptyList()

        result.success(slots)
    }

    private fun readSave(call: MethodCall, result: MethodChannel.Result) {
        val slotId = call.argument<String>("slotId") ?: return result.error("NO_SLOT", "slotId required", null)

        val slotDir = File(savesDir(call.argument<String>("savesPath")), slotId)
        val mainFile = File(slotDir, slotId).takeIf { it.exists() }
            ?: return result.error("NOT_FOUND", "Main save file not found in $slotId", null)
        val infoFile = File(slotDir, "SaveGameInfo").takeIf { it.exists() }
            ?: return result.error("NOT_FOUND", "SaveGameInfo not found in $slotId", null)

        val buf = ByteArrayOutputStream()
        ZipOutputStream(buf).use { zip ->
            fun addEntry(file: File, entryName: String) {
                zip.putNextEntry(ZipEntry(entryName))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            addEntry(mainFile, slotId)
            addEntry(infoFile, "SaveGameInfo")
        }
        result.success(buf.toByteArray())
    }

    private fun writeSave(call: MethodCall, result: MethodChannel.Result) {
        val slotId = call.argument<String>("slotId") ?: return result.error("NO_SLOT", "slotId required", null)
        val data = call.argument<ByteArray>("data") ?: return result.error("NO_DATA", "data required", null)

        val savesRoot = savesDir(call.argument<String>("savesPath"))
        savesRoot.mkdirs()

        val existing = File(savesRoot, slotId)
        if (existing.exists()) {
            val backupName = "$slotId.bak.${System.currentTimeMillis()}"
            existing.renameTo(File(savesRoot, backupName))
        }

        val newDir = File(savesRoot, slotId)
        newDir.mkdirs()

        ZipInputStream(ByteArrayInputStream(data)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val outFile = File(newDir, entry.name)
                outFile.outputStream().use { zip.copyTo(it) }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        result.success(null)
    }

    private fun getSlotModifiedMs(call: MethodCall, result: MethodChannel.Result) {
        val slotId = call.argument<String>("slotId") ?: return result.error("NO_SLOT", "slotId required", null)

        val slotDir = File(savesDir(call.argument<String>("savesPath")), slotId)
        val mainFile = File(slotDir, slotId)
        val infoFile = File(slotDir, "SaveGameInfo")

        if (!mainFile.exists() || !infoFile.exists()) {
            result.success(0L)
            return
        }
        result.success(maxOf(mainFile.lastModified(), infoFile.lastModified()))
    }
}

package com.stardewsync

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.documentfile.provider.DocumentFile
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.PluginRegistry
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Kotlin platform channel for Android Storage Access Framework operations.
 *
 * All file access to Android/data/com.chucklefish.stardewvalley/files/Saves
 * must go through DocumentFile on Android 11+ (API 30+).
 */
class SafPlugin : FlutterPlugin, MethodChannel.MethodCallHandler,
    ActivityAware, PluginRegistry.ActivityResultListener {

    private lateinit var channel: MethodChannel
    private var activity: Activity? = null
    private var pendingResult: MethodChannel.Result? = null

    companion object {
        private const val CHANNEL = "com.stardewsync/saf"
        private const val REQUEST_OPEN_DIR = 42001
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

    override fun onDetachedFromActivityForConfigChanges() {
        activity = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activity = binding.activity
        binding.addActivityResultListener(this)
    }

    override fun onDetachedFromActivity() {
        activity = null
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "openDirectoryPicker" -> openDirectoryPicker(result)
            "listSaves"           -> listSaves(call, result)
            "readSave"            -> readSave(call, result)
            "writeSave"           -> writeSave(call, result)
            "getSlotModifiedMs"   -> getSlotModifiedMs(call, result)
            else                  -> result.notImplemented()
        }
    }

    private fun openDirectoryPicker(result: MethodChannel.Result) {
        val act = activity ?: return result.error("NO_ACTIVITY", "No activity", null)
        pendingResult = result
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }
        act.startActivityForResult(intent, REQUEST_OPEN_DIR)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != REQUEST_OPEN_DIR) return false
        val res = pendingResult ?: return false
        pendingResult = null
        if (resultCode != Activity.RESULT_OK || data == null) {
            res.success(null)
            return true
        }
        val uri = data.data ?: return run { res.success(null); true }
        // Persist the permission across reboots
        activity?.contentResolver?.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        res.success(uri.toString())
        return true
    }

    private fun listSaves(call: MethodCall, result: MethodChannel.Result) {
        val uriStr = call.argument<String>("uri") ?: return result.error("NO_URI", "uri required", null)
        val ctx = activity ?: return result.error("NO_ACTIVITY", "No activity", null)
        val tree = DocumentFile.fromTreeUri(ctx, Uri.parse(uriStr))
            ?: return result.error("BAD_URI", "Cannot open tree", null)

        val slots = mutableListOf<Map<String, Any>>()
        for (dir in tree.listFiles()) {
            if (!dir.isDirectory) continue
            val name = dir.name ?: continue
            val mainFile = dir.findFile(name) ?: continue
            val infoFile = dir.findFile("SaveGameInfo") ?: continue
            val lastModMs = maxOf(mainFile.lastModified(), infoFile.lastModified())
            slots.add(mapOf("slotId" to name, "lastModifiedMs" to lastModMs))
        }
        result.success(slots)
    }

    private fun readSave(call: MethodCall, result: MethodChannel.Result) {
        val uriStr = call.argument<String>("uri") ?: return result.error("NO_URI", "uri required", null)
        val slotId = call.argument<String>("slotId") ?: return result.error("NO_SLOT", "slotId required", null)
        val ctx = activity ?: return result.error("NO_ACTIVITY", "No activity", null)

        val tree = DocumentFile.fromTreeUri(ctx, Uri.parse(uriStr))
            ?: return result.error("BAD_URI", "Cannot open tree", null)
        val slotDir = tree.findFile(slotId)
            ?: return result.error("NOT_FOUND", "Slot not found: $slotId", null)

        val mainFile = slotDir.findFile(slotId)
            ?: return result.error("NOT_FOUND", "Main save file not found in $slotId", null)
        val infoFile = slotDir.findFile("SaveGameInfo")
            ?: return result.error("NOT_FOUND", "SaveGameInfo not found in $slotId", null)

        val buf = ByteArrayOutputStream()
        ZipOutputStream(buf).use { zip ->
            fun addEntry(file: DocumentFile, entryName: String) {
                zip.putNextEntry(ZipEntry(entryName))
                ctx.contentResolver.openInputStream(file.uri)!!.use { it.copyTo(zip) }
                zip.closeEntry()
            }
            addEntry(mainFile, slotId)
            addEntry(infoFile, "SaveGameInfo")
        }
        result.success(buf.toByteArray())
    }

    private fun writeSave(call: MethodCall, result: MethodChannel.Result) {
        val uriStr = call.argument<String>("uri") ?: return result.error("NO_URI", "uri required", null)
        val slotId = call.argument<String>("slotId") ?: return result.error("NO_SLOT", "slotId required", null)
        val data = call.argument<ByteArray>("data") ?: return result.error("NO_DATA", "data required", null)
        val ctx = activity ?: return result.error("NO_ACTIVITY", "No activity", null)

        val tree = DocumentFile.fromTreeUri(ctx, Uri.parse(uriStr))
            ?: return result.error("BAD_URI", "Cannot open tree", null)

        // Backup existing slot if present
        val existing = tree.findFile(slotId)
        if (existing != null && existing.isDirectory) {
            val backupName = "$slotId.bak.${System.currentTimeMillis()}"
            existing.renameTo(backupName)
        }

        // Create new slot dir and extract ZIP
        val newDir = tree.createDirectory(slotId)
            ?: return result.error("IO_ERROR", "Cannot create directory $slotId", null)

        ZipInputStream(ByteArrayInputStream(data)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val outFile = newDir.createFile("application/octet-stream", entry.name)
                    ?: return result.error("IO_ERROR", "Cannot create file ${entry.name}", null)
                ctx.contentResolver.openOutputStream(outFile.uri)!!.use { out ->
                    zip.copyTo(out)
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        result.success(null)
    }

    private fun getSlotModifiedMs(call: MethodCall, result: MethodChannel.Result) {
        val uriStr = call.argument<String>("uri") ?: return result.error("NO_URI", "uri required", null)
        val slotId = call.argument<String>("slotId") ?: return result.error("NO_SLOT", "slotId required", null)
        val ctx = activity ?: return result.error("NO_ACTIVITY", "No activity", null)

        val tree = DocumentFile.fromTreeUri(ctx, Uri.parse(uriStr))
            ?: return result.error("BAD_URI", "Cannot open tree", null)
        val slotDir = tree.findFile(slotId) ?: return result.success(0L)
        val mainFile = slotDir.findFile(slotId) ?: return result.success(0L)
        val infoFile = slotDir.findFile("SaveGameInfo") ?: return result.success(0L)

        result.success(maxOf(mainFile.lastModified(), infoFile.lastModified()))
    }
}

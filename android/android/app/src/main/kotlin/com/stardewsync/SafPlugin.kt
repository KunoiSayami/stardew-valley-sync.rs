package com.stardewsync

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.PluginRegistry

class SafPlugin : FlutterPlugin, MethodChannel.MethodCallHandler,
    ActivityAware, PluginRegistry.ActivityResultListener {

    private lateinit var channel: MethodChannel
    private lateinit var prefs: SharedPreferences

    private val manageBackend = ManageStorageBackend()
    private val shizukuBackend = ShizukuBackend()

    private var currentMode: FileAccessMode = FileAccessMode.MANAGE_STORAGE
    private var pendingDartResult: MethodChannel.Result? = null

    private val activeBackend: FileAccessBackend
        get() = when (currentMode) {
            FileAccessMode.SHIZUKU -> shizukuBackend
            else -> manageBackend
        }

    companion object {
        private const val CHANNEL = "com.stardewsync/saf"
        private const val PREFS_NAME = "stardewsync_prefs"
        private const val PREFS_MODE_KEY = "file_access_mode"
    }

    // ── FlutterPlugin ────────────────────────────────────────────────────────

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        prefs = binding.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        currentMode = loadMode()
        channel = MethodChannel(binding.binaryMessenger, CHANNEL)
        channel.setMethodCallHandler(this)
        shizukuBackend.registerListeners()
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        shizukuBackend.unregisterListeners()
        channel.setMethodCallHandler(null)
    }

    // ── ActivityAware ────────────────────────────────────────────────────────

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        manageBackend.activity = binding.activity
        binding.addActivityResultListener(this)
    }

    override fun onDetachedFromActivityForConfigChanges() { manageBackend.activity = null }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        manageBackend.activity = binding.activity
        binding.addActivityResultListener(this)
    }

    override fun onDetachedFromActivity() { manageBackend.activity = null }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean =
        manageBackend.handleActivityResult(requestCode, resultCode, data)

    // ── MethodCallHandler ────────────────────────────────────────────────────

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "getFileAccessMode" -> result.success(currentMode.name)
            "setFileAccessMode" -> {
                val raw = call.argument<String>("mode")
                    ?: return result.error("NO_MODE", "mode required", null)
                val mode = runCatching { FileAccessMode.valueOf(raw) }.getOrNull()
                    ?: return result.error("BAD_MODE", "Unknown mode: $raw", null)
                currentMode = mode
                saveMode(mode)
                result.success(null)
            }
            "isShizukuAvailable" -> result.success(shizukuBackend.isShizukuAvailable())
            "checkAndRequestPermission" -> {
                if (pendingDartResult != null) {
                    result.error("IN_PROGRESS", "Permission request already in progress", null)
                    return
                }
                pendingDartResult = result
                activeBackend.requestPermission { granted ->
                    val res = pendingDartResult ?: return@requestPermission
                    pendingDartResult = null
                    Handler(Looper.getMainLooper()).post { res.success(granted) }
                }
            }
            "hasPermission"       -> result.success(activeBackend.hasPermission())
            "getDefaultSavesPath" -> result.success(activeBackend.getDefaultSavesPath())
            "listDirectory"       -> dispatch(result) {
                val path = call.argument<String>("path")
                    ?: throw IllegalArgumentException("path required")
                activeBackend.listDirectory(path)
            }
            "savesDirExists"      -> dispatch(result) {
                activeBackend.savesDirExists(call.argument<String>("savesPath"))
            }
            "listSaves"           -> dispatch(result) {
                activeBackend.listSaves(call.argument<String>("savesPath"))
            }
            "readSave"            -> dispatch(result) {
                val slotId = call.argument<String>("slotId")
                    ?: throw IllegalArgumentException("slotId required")
                activeBackend.readSave(slotId, call.argument<String>("savesPath"))
            }
            "writeSave"           -> dispatch(result) {
                val slotId = call.argument<String>("slotId")
                    ?: throw IllegalArgumentException("slotId required")
                val data = call.argument<ByteArray>("data")
                    ?: throw IllegalArgumentException("data required")
                activeBackend.writeSave(slotId, data, call.argument<String>("savesPath"))
            }
            "getSlotModifiedMs"   -> dispatch(result) {
                val slotId = call.argument<String>("slotId")
                    ?: throw IllegalArgumentException("slotId required")
                activeBackend.getSlotModifiedMs(slotId, call.argument<String>("savesPath"))
            }
            else -> result.notImplemented()
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun dispatch(result: MethodChannel.Result, block: () -> Any?) {
        Thread {
            try {
                val value = block()
                Handler(Looper.getMainLooper()).post { result.success(value) }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    result.error("FILE_ACCESS_ERROR", e.message, null)
                }
            }
        }.start()
    }

    private fun loadMode(): FileAccessMode =
        prefs.getString(PREFS_MODE_KEY, null)
            ?.let { runCatching { FileAccessMode.valueOf(it) }.getOrNull() }
            ?: FileAccessMode.MANAGE_STORAGE

    private fun saveMode(mode: FileAccessMode) =
        prefs.edit().putString(PREFS_MODE_KEY, mode.name).apply()
}

package com.stardewsync

import android.content.pm.PackageManager
import android.os.Environment
import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.lang.reflect.Method
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess

class ShizukuBackend : FileAccessBackend {

    companion object {
        private const val SHIZUKU_REQ_CODE = 42003
        private const val STARDEW_PACKAGE = "com.chucklefish.stardewvalley"
        private val DEFAULT_PATH = Environment.getExternalStorageDirectory().path +
            "/Android/data/$STARDEW_PACKAGE/files/Saves"
    }

    private var pendingCallback: ((Boolean) -> Unit)? = null

    private val permissionListener =
        Shizuku.OnRequestPermissionResultListener { code, result ->
            if (code == SHIZUKU_REQ_CODE) {
                val granted = result == PackageManager.PERMISSION_GRANTED
                pendingCallback?.invoke(granted)
                pendingCallback = null
            }
        }

    fun registerListeners() {
        Shizuku.addRequestPermissionResultListener(permissionListener)
    }

    fun unregisterListeners() {
        Shizuku.removeRequestPermissionResultListener(permissionListener)
    }

    fun isShizukuAvailable(): Boolean =
        try { Shizuku.pingBinder() } catch (_: Exception) { false }

    override fun hasPermission(): Boolean =
        isShizukuAvailable() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED

    override fun requestPermission(onResult: (Boolean) -> Unit) {
        if (!isShizukuAvailable()) { onResult(false); return }
        if (hasPermission()) { onResult(true); return }
        pendingCallback = onResult
        Shizuku.requestPermission(SHIZUKU_REQ_CODE)
    }

    override fun getDefaultSavesPath(): String = DEFAULT_PATH

    // ── Shell helpers ────────────────────────────────────────────────────────

    // Shizuku.newProcess is @RestrictTo(LIBRARY_GROUP_PREFIX) — access via reflection
    // since the restriction is compile-time only and the method is public at runtime.
    private val newProcessMethod: Method by lazy {
        Shizuku::class.java
            .getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
            .also { it.isAccessible = true }
    }

    private fun newProcess(cmd: Array<String>): ShizukuRemoteProcess =
        newProcessMethod.invoke(null, cmd, null, null) as ShizukuRemoteProcess

    private fun shell(cmd: String): String {
        val p = newProcess(arrayOf("sh", "-c", cmd))
        val out = p.inputStream.bufferedReader().readText()
        val err = p.errorStream.bufferedReader().readText()
        if (p.waitFor() != 0) throw RuntimeException("Shell error: $err\ncmd: $cmd")
        return out
    }

    private fun String.esc() = replace("'", "'\\''")

    private fun savesRoot(savesPath: String?): String =
        if (!savesPath.isNullOrBlank()) savesPath else DEFAULT_PATH

    // ── FileAccessBackend ────────────────────────────────────────────────────

    override fun listDirectory(path: String): List<Map<String, String>> {
        val out = shell("ls -1ap '${path.esc()}' 2>/dev/null | grep '/' | sed 's|/\$||'")
        return out.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "." && it != ".." }
            .map { name -> mapOf("name" to name, "path" to "$path/$name") }
    }

    override fun savesDirExists(savesPath: String?): Boolean {
        val root = savesRoot(savesPath)
        val result = shell("[ -d '${root.esc()}' ] && echo yes || echo no")
        return result.trim() == "yes"
    }

    override fun listSaves(savesPath: String?): List<Map<String, Any>> {
        val root = savesRoot(savesPath)
        val exists = shell("[ -d '${root.esc()}' ] && echo yes || echo no").trim()
        if (exists != "yes") return emptyList()

        val dirList = shell("ls -1 '${root.esc()}' 2>/dev/null").lines()
            .map { it.trim() }.filter { it.isNotEmpty() }

        return dirList.mapNotNull { slotId ->
            val mainPath = "$root/$slotId/$slotId"
            val infoPath = "$root/$slotId/SaveGameInfo"
            val check = shell(
                "[ -f '${mainPath.esc()}' ] && [ -f '${infoPath.esc()}' ] && " +
                    "stat -c '%Y' '${mainPath.esc()}' && " +
                    "stat -c '%Y' '${infoPath.esc()}' || true"
            ).trim()
            val lines = check.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.size < 2) return@mapNotNull null
            val mainSec = lines[0].toLongOrNull() ?: return@mapNotNull null
            val infoSec = lines[1].toLongOrNull() ?: return@mapNotNull null
            mapOf("slotId" to slotId, "lastModifiedMs" to maxOf(mainSec, infoSec) * 1000L)
        }
    }

    override fun readSave(slotId: String, savesPath: String?): ByteArray {
        val root = savesRoot(savesPath)
        val mainPath = "$root/$slotId/$slotId"
        val infoPath = "$root/$slotId/SaveGameInfo"

        val mainB64 = shell("base64 -w 0 '${mainPath.esc()}'").trim()
        val infoB64 = shell("base64 -w 0 '${infoPath.esc()}'").trim()
        val mainBytes = Base64.decode(mainB64, Base64.DEFAULT)
        val infoBytes = Base64.decode(infoB64, Base64.DEFAULT)

        val buf = ByteArrayOutputStream()
        ZipOutputStream(buf).use { zip ->
            zip.putNextEntry(ZipEntry(slotId))
            zip.write(mainBytes)
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("SaveGameInfo"))
            zip.write(infoBytes)
            zip.closeEntry()
        }
        return buf.toByteArray()
    }

    override fun writeSave(slotId: String, data: ByteArray, savesPath: String?, lastModifiedMs: Long?) {
        val root = savesRoot(savesPath)
        val slotDir = "$root/$slotId"
        val bakDir = "$root/$slotId.bak.${System.currentTimeMillis()}"

        shell("[ -d '${slotDir.esc()}' ] && mv '${slotDir.esc()}' '${bakDir.esc()}' || true")
        shell("mkdir -p '${slotDir.esc()}'")

        ZipInputStream(ByteArrayInputStream(data)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val fileBytes = zip.readBytes()
                val b64 = Base64.encodeToString(fileBytes, Base64.NO_WRAP)
                val destPath = "$slotDir/${entry.name}"
                val tmpFile = "/data/local/tmp/stardewsync_${System.currentTimeMillis()}.b64"
                val writeProc = newProcess(arrayOf("sh", "-c", "cat > '$tmpFile'"))
                writeProc.outputStream.use { it.write(b64.toByteArray()) }
                writeProc.waitFor()
                shell("base64 -d '$tmpFile' > '${destPath.esc()}' && rm '$tmpFile'")
                if (lastModifiedMs != null) {
                    val sec = lastModifiedMs / 1000
                    shell("touch -t $(date -d @$sec '+%Y%m%d%H%M.%S') '${destPath.esc()}'")
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    override fun getSlotModifiedMs(slotId: String, savesPath: String?): Long {
        val root = savesRoot(savesPath)
        val mainPath = "$root/$slotId/$slotId"
        val infoPath = "$root/$slotId/SaveGameInfo"
        val out = shell(
            "stat -c '%Y' '${mainPath.esc()}' 2>/dev/null && " +
                "stat -c '%Y' '${infoPath.esc()}' 2>/dev/null || true"
        ).trim()
        val lines = out.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size < 2) return 0L
        val a = lines[0].toLongOrNull() ?: return 0L
        val b = lines[1].toLongOrNull() ?: return 0L
        return maxOf(a, b) * 1000L
    }
}

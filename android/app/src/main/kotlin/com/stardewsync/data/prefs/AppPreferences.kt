package com.stardewsync.data.prefs

import android.content.Context
import com.stardewsync.FileAccessMode

class AppPreferences(context: Context) {

    private val prefs = context.getSharedPreferences("stardewsync_prefs", Context.MODE_PRIVATE)

    var serverIp: String?
        get() = prefs.getString("server_ip", null)
        set(v) = prefs.edit().putString("server_ip", v).apply()

    var serverPort: String
        get() = prefs.getString("server_port", "24742") ?: "24742"
        set(v) = prefs.edit().putString("server_port", v).apply()

    var serverPin: String?
        get() = prefs.getString("server_pin", null)
        set(v) = prefs.edit().putString("server_pin", v).apply()

    var savesPath: String?
        get() = prefs.getString("saves_path", null)
        set(v) = prefs.edit().putString("saves_path", v).apply()

    var fileAccessMode: FileAccessMode
        get() = when (prefs.getString("file_access_mode", "MANAGE_STORAGE")) {
            "SHIZUKU" -> FileAccessMode.SHIZUKU
            else -> FileAccessMode.MANAGE_STORAGE
        }
        set(v) = prefs.edit().putString("file_access_mode", v.name).apply()

    fun clearServerCredentials() {
        prefs.edit()
            .remove("server_ip")
            .remove("server_port")
            .remove("server_pin")
            .apply()
    }
}

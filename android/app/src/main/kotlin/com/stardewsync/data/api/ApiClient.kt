package com.stardewsync.data.api

import com.stardewsync.data.model.SaveSlotInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiClient(private val baseUrl: String, private val pin: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun Request.Builder.withPin() = header("x-sync-pin", pin)

    suspend fun health(): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$baseUrl/health").get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Health check failed: ${response.code}")
            JSONObject(response.body.string())
        }
    }

    suspend fun listSaves(): List<SaveSlotInfo> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/api/v1/saves")
            .withPin()
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("List saves failed: ${response.code}")
            val array = JSONObject(response.body.string()).getJSONArray("slots")
            (0 until array.length()).map { SaveSlotInfo.fromJson(array.getJSONObject(it)) }
        }
    }

    // Returns Pair(zipBytes, serverLastModifiedMs)
    suspend fun downloadSave(slotId: String): Pair<ByteArray, Long> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/api/v1/saves/$slotId/download")
            .withPin()
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Download failed: ${response.code}")
            val ts = response.header("x-slot-last-modified-ms")?.toLongOrNull() ?: 0L
            Pair(response.body.bytes(), ts)
        }
    }

    suspend fun uploadSave(
        slotId: String,
        zipBytes: ByteArray,
        clientLastModifiedMs: Long,
        force: Boolean = false,
    ) = withContext(Dispatchers.IO) {
        val url = "$baseUrl/api/v1/saves/$slotId/upload" + if (force) "?force=true" else ""
        val body = zipBytes.toRequestBody("application/octet-stream".toMediaType())
        val request = Request.Builder()
            .url(url)
            .withPin()
            .header("x-client-last-modified-ms", clientLastModifiedMs.toString())
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 409) {
                val json = JSONObject(response.body.string())
                throw ConflictException(
                    serverLastModifiedMs = json.getLong("server_last_modified_ms"),
                    clientLastModifiedMs = json.optLong("client_last_modified_ms", clientLastModifiedMs),
                )
            }
            if (!response.isSuccessful) throw IOException("Upload failed: ${response.code}")
        }
    }

    suspend fun deleteSlot(slotId: String) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/api/v1/saves/$slotId")
            .withPin()
            .delete()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Delete failed: ${response.code}")
        }
    }
}

package com.stardewsync.data.model

import org.json.JSONObject

data class SaveSlotInfo(
    val slotId: String,
    val displayName: String,
    val lastModifiedMs: Long,
    val sizeBytes: Long,
) {
    val formattedSize: String
        get() = when {
            sizeBytes < 1_024 -> "$sizeBytes B"
            sizeBytes < 1_048_576 -> "${"%.1f".format(sizeBytes / 1_024.0)} KB"
            else -> "${"%.1f".format(sizeBytes / 1_048_576.0)} MB"
        }

    companion object {
        fun fromJson(obj: JSONObject) = SaveSlotInfo(
            slotId = obj.getString("slot_id"),
            displayName = obj.getString("display_name"),
            lastModifiedMs = obj.getLong("last_modified_ms"),
            sizeBytes = obj.getLong("size_bytes"),
        )
    }
}

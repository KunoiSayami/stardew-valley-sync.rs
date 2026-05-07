package com.stardewsync.data.api

class ConflictException(
    val serverLastModifiedMs: Long,
    val clientLastModifiedMs: Long,
) : Exception("409 Conflict: server copy is newer")

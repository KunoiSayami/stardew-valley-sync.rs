package com.stardewsync.data.model

data class DiscoveredServer(val host: String, val port: Int) {
    val baseUrl: String get() = "http://$host:$port"
    override fun toString() = "$host:$port"
}

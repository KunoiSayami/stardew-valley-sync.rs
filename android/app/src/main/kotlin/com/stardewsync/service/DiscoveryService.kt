package com.stardewsync.service

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import com.stardewsync.data.model.DiscoveredServer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors

class DiscoveryService(context: Context) {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val resolveExecutor = Executors.newSingleThreadExecutor()

    suspend fun discoverServers(timeoutMs: Long = 5_000L): List<DiscoveredServer> {
        val found = Channel<DiscoveredServer>(Channel.UNLIMITED)
        val results = mutableListOf<DiscoveredServer>()

        fun makeResolveListener() = object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceResolved(info: NsdServiceInfo) {
                val host = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    info.hostAddresses.firstOrNull()?.hostAddress
                } else {
                    @Suppress("DEPRECATION")
                    info.host?.hostAddress
                } ?: return
                found.trySend(DiscoveredServer(host, info.port))
            }
        }

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}
            override fun onDiscoveryStopped(regType: String) { found.close() }
            override fun onStartDiscoveryFailed(regType: String, errorCode: Int) { found.close() }
            override fun onStopDiscoveryFailed(regType: String, errorCode: Int) {}
            override fun onServiceFound(info: NsdServiceInfo) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    nsdManager.resolveService(info, resolveExecutor, makeResolveListener())
                } else {
                    @Suppress("DEPRECATION")
                    nsdManager.resolveService(info, makeResolveListener())
                }
            }
            override fun onServiceLost(info: NsdServiceInfo) {}
        }

        nsdManager.discoverServices("_stardewsync._tcp", NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        try {
            withTimeoutOrNull(timeoutMs) {
                for (server in found) {
                    results.add(server)
                }
            }
        } finally {
            try { nsdManager.stopServiceDiscovery(discoveryListener) } catch (_: Exception) {}
        }
        return results
    }
}

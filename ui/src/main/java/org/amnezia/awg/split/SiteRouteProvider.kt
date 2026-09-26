/*
 * Copyright © 2026 AykQ. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.amnezia.awg.split

import android.content.Context
import android.util.Log
import kotlinx.coroutines.runBlocking
import org.amnezia.awg.backend.GoBackend
import org.amnezia.awg.config.Config
import org.amnezia.awg.config.InetNetwork
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * Turns the site list into routes that bypass the tunnel.
 *
 * Each domain is resolved twice, through DoH and through the system resolver, and the union is
 * excluded. Apps resolve through the system resolver (Private DNS), and CDNs answer differently
 * per resolver, so excluding only one view would let the other one leak into the tunnel.
 * Fresh answers are merged into a cache, so a failed or slow lookup falls back to known IPs.
 */
class SiteRouteProvider(private val context: Context) : GoBackend.ExcludedRoutesProvider {
    private val executor = Executors.newCachedThreadPool()

    override fun getExcludedRoutes(config: Config): Collection<InetNetwork> {
        val settings = runBlocking { SiteStore.snapshot() }
        val routes = LinkedHashSet<InetNetwork>()
        val hosts = LinkedHashSet<String>()
        var siteCount = 0

        if (settings.enabled) {
            siteCount = settings.sites.size
            for (entry in settings.sites) {
                if (SiteStore.isNetwork(entry)) {
                    routes += InetNetwork.parse(entry)
                } else {
                    hosts += entry
                    if (!entry.startsWith("www."))
                        hosts += "www.$entry"
                }
            }
        }

        val detected = PrivateDns.detectHost(context)
        if ((detected ?: "") != settings.lastDetectedPrivateDns)
            runBlocking { SiteStore.setLastDetectedPrivateDns(detected ?: "") }
        if (settings.privateDnsBypass) {
            val host = settings.privateDnsHost.ifBlank { detected ?: "" }
            if (host.isNotBlank())
                hosts += host
        }

        if (hosts.isNotEmpty()) {
            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(LOOKUP_BUDGET_MS)
            val futures = hosts.associateWith { host ->
                executor.submit(Callable { lookup(host, settings.dohUrl) })
            }
            val fresh = futures.mapValues { (host, future) -> await(host, future, deadline) }
            val merged = runBlocking { SiteStore.mergeCache(fresh) }
            for (host in hosts) {
                for (ip in merged[host] ?: emptyList())
                    toHostRoute(ip)?.let { routes += it }
            }
        }

        Log.i(TAG, "Bypass routes: ${routes.size} for $siteCount site(s), ${hosts.size} lookup host(s) incl. www. and Private DNS")
        return routes
    }

    private fun lookup(host: String, dohUrl: String): List<String> {
        val addresses = LinkedHashSet<InetAddress>()
        try {
            addresses += DohResolver.resolve(dohUrl, host)
        } catch (e: Exception) {
            Log.w(TAG, "DoH lookup failed for $host: ${e.message}")
        }
        try {
            addresses += InetAddress.getAllByName(host)
        } catch (e: Exception) {
            Log.w(TAG, "System lookup failed for $host: ${e.message}")
        }
        return addresses.map { it.hostAddress!!.substringBefore('%') }
    }

    private fun await(host: String, future: Future<List<String>>, deadline: Long): List<String> {
        val remaining = deadline - System.nanoTime()
        return try {
            future.get(remaining.coerceAtLeast(0), TimeUnit.NANOSECONDS)
        } catch (e: Exception) {
            Log.w(TAG, "Lookup for $host did not finish in time, using cache")
            future.cancel(true)
            emptyList()
        }
    }

    private fun toHostRoute(ip: String): InetNetwork? = try {
        val bits = if (InetAddress.getByName(ip) is Inet4Address) 32 else 128
        InetNetwork.parse("$ip/$bits")
    } catch (_: Exception) {
        null
    }

    companion object {
        private const val TAG = "Switchyard/SiteRoutes"
        private const val LOOKUP_BUDGET_MS = 5000L
    }
}

/*
 * Copyright © 2026 AykQ. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.amnezia.awg.split

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.runBlocking
import org.amnezia.awg.backend.GoBackend
import org.amnezia.awg.backend.SplitPlan
import org.amnezia.awg.config.Config
import java.net.InetAddress
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

// Resolve via both DoH and the system resolver; CDNs answer per resolver and one view alone leaks.
class SplitRouteProvider(private val context: Context) : GoBackend.SplitTunnelProvider {
    private val executor = Executors.newCachedThreadPool()

    override fun getPlan(config: Config): SplitPlan {
        val settings = runBlocking { SplitStore.snapshot() }
        val sites = if (settings.enabled) settings.activeSites else emptyList()
        val networks = sites.filter(SplitStore::isNetwork)
        val hosts = LinkedHashSet<String>()
        for (entry in sites) {
            if (SplitStore.isNetwork(entry)) continue
            hosts += entry
            if (!entry.startsWith("www.")) hosts += "www.$entry"
        }

        val detected = PrivateDns.detectHost(context)
        if ((detected ?: "") != settings.lastDetectedPrivateDns)
            runBlocking { SplitStore.setLastDetectedPrivateDns(detected ?: "") }
        val bypassHost = detected?.takeIf { settings.privateDnsBypass }

        val lookups = hosts + listOfNotNull(bypassHost)
        val merged = if (lookups.isEmpty()) emptyMap() else resolveAll(lookups, settings.dohUrl)

        val plan = SplitPlanner.plan(SplitPlanner.Input(
            enabled = settings.enabled,
            appsMode = settings.appsMode,
            apps = settings.activeApps,
            sitesMode = settings.sitesMode,
            siteNetworks = networks,
            resolved = hosts.associateWith { merged[it] ?: emptyList() },
            bypassIps = bypassHost?.let { merged[it] } ?: emptyList(),
            dnsServers = config.`interface`.dnsServers.map { it.hostAddress!! },
            ownPackage = context.packageName,
            isInstalled = ::isInstalled,
        ))
        if (settings.enabled && settings.sitesMode == SplitMode.INCLUDE && plan.includedRoutes == null)
            Log.w(TAG, "Include site list resolved to nothing, routing everything through the tunnel")
        val routeCount = plan.includedRoutes?.size ?: plan.excludedRoutes.size
        Log.i(TAG, "Split plan: apps=${settings.appsMode.key}(${plan.excludedApps.size + plan.includedApps.size}) " +
                "sites=${settings.sitesMode.key}(${sites.size} site, ${hosts.size} host, $routeCount route)")
        return plan
    }

    private fun isInstalled(pkg: String) = try {
        context.packageManager.getApplicationInfo(pkg, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun resolveAll(hosts: Collection<String>, dohUrl: String): Map<String, List<String>> {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(LOOKUP_BUDGET_MS)
        val futures = hosts.associateWith { host -> executor.submit(Callable { lookup(host, dohUrl) }) }
        val fresh = futures.mapValues { (host, future) -> await(host, future, deadline) }
        return runBlocking { SplitStore.mergeCache(fresh) }
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

    companion object {
        private const val TAG = "Janus/SiteRoutes"
        private const val LOOKUP_BUDGET_MS = 5000L
    }
}

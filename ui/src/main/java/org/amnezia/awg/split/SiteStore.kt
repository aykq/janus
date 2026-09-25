/*
 * Copyright © 2026 AykQ. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.amnezia.awg.split

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.amnezia.awg.Application
import org.amnezia.awg.config.InetAddresses
import org.amnezia.awg.config.InetNetwork
import org.json.JSONArray
import org.json.JSONObject

/**
 * Global settings for site-based split tunneling: the list of sites/IPs that bypass the tunnel,
 * the DoH resolver used to find their addresses, and the Private DNS bypass.
 */
object SiteStore {
    const val DEFAULT_DOH_URL = "https://freedns.controld.com/p2"

    /** Resolved addresses kept per domain; older ones fall off so CDN churn stays bounded. */
    private const val MAX_CACHED_PER_HOST = 64

    private val ENABLED = booleanPreferencesKey("split_sites_enabled")
    private val SITES = stringSetPreferencesKey("split_sites")
    private val CACHE = stringPreferencesKey("split_sites_cache")
    private val DOH_URL = stringPreferencesKey("split_doh_url")
    private val PRIVATE_DNS_BYPASS = booleanPreferencesKey("split_private_dns_bypass")
    private val PRIVATE_DNS_HOST = stringPreferencesKey("split_private_dns_host")
    private val LAST_DETECTED_PRIVATE_DNS = stringPreferencesKey("split_last_detected_private_dns")

    data class Settings(
        val enabled: Boolean,
        val sites: List<String>,
        val dohUrl: String,
        val privateDnsBypass: Boolean,
        val privateDnsHost: String,
        val lastDetectedPrivateDns: String,
        val cache: Map<String, List<String>>,
    )

    private fun Preferences.toSettings() = Settings(
        enabled = this[ENABLED] ?: true,
        sites = (this[SITES] ?: emptySet()).sorted(),
        dohUrl = this[DOH_URL]?.takeIf { it.isNotBlank() } ?: DEFAULT_DOH_URL,
        privateDnsBypass = this[PRIVATE_DNS_BYPASS] ?: true,
        privateDnsHost = this[PRIVATE_DNS_HOST] ?: "",
        lastDetectedPrivateDns = this[LAST_DETECTED_PRIVATE_DNS] ?: "",
        cache = decodeCache(this[CACHE]),
    )

    val settings: Flow<Settings>
        get() = Application.getPreferencesDataStore().data.map { it.toSettings() }

    suspend fun snapshot(): Settings = settings.first()

    suspend fun setEnabled(enabled: Boolean) = edit { it[ENABLED] = enabled }

    suspend fun setDohUrl(url: String) = edit { it[DOH_URL] = url.trim() }

    suspend fun setPrivateDnsBypass(enabled: Boolean) = edit { it[PRIVATE_DNS_BYPASS] = enabled }

    suspend fun setPrivateDnsHost(host: String) = edit { it[PRIVATE_DNS_HOST] = host.trim().lowercase() }

    suspend fun setLastDetectedPrivateDns(host: String) = edit { it[LAST_DETECTED_PRIVATE_DNS] = host }

    /** Returns the normalized entry, or null if the input is neither a hostname nor an IP/CIDR. */
    suspend fun addSite(raw: String): String? {
        val entry = normalize(raw) ?: return null
        edit { it[SITES] = (it[SITES] ?: emptySet()) + entry }
        return entry
    }

    suspend fun removeSite(entry: String) = edit {
        it[SITES] = (it[SITES] ?: emptySet()) - entry
        val cache = decodeCache(it[CACHE]).toMutableMap()
        cache.remove(entry)
        cache.remove("www.$entry")
        it[CACHE] = encodeCache(cache)
    }

    /** Merges fresh lookups into the cache (newest first) and returns the merged view. */
    suspend fun mergeCache(fresh: Map<String, List<String>>): Map<String, List<String>> {
        var merged: Map<String, List<String>> = emptyMap()
        edit {
            val cache = decodeCache(it[CACHE]).toMutableMap()
            for ((host, ips) in fresh) {
                if (ips.isEmpty()) continue
                cache[host] = (ips + (cache[host] ?: emptyList())).distinct().take(MAX_CACHED_PER_HOST)
            }
            it[CACHE] = encodeCache(cache)
            merged = cache
        }
        return merged
    }

    fun isNetwork(entry: String) = try {
        InetNetwork.parse(entry)
        true
    } catch (_: Exception) {
        false
    }

    fun normalize(raw: String): String? {
        var s = raw.trim().lowercase()
        s = s.substringAfter("://")
        s = s.substringBefore('/').let { if (s.contains('/') && isNetwork(s)) s else it }
        if (isNetwork(s)) return InetNetwork.parse(s).toString()
        s = s.substringBefore('?').substringBefore('#').substringAfter('@')
        if (s.startsWith('[')) return null
        s = s.substringBefore(':').trimEnd('.')
        if (s.startsWith("*.")) s = s.removePrefix("*.")
        return s.takeIf { it.contains('.') && InetAddresses.isHostname(it) }
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        Application.getPreferencesDataStore().edit { block(it) }
    }

    private fun decodeCache(json: String?): Map<String, List<String>> {
        if (json.isNullOrBlank()) return emptyMap()
        return try {
            val obj = JSONObject(json)
            obj.keys().asSequence().associateWith { key ->
                val arr = obj.getJSONArray(key)
                List(arr.length()) { arr.getString(it) }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun encodeCache(cache: Map<String, List<String>>): String {
        val obj = JSONObject()
        for ((host, ips) in cache) obj.put(host, JSONArray(ips))
        return obj.toString()
    }
}

/*
 * Copyright © 2026 AykQ. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.amnezia.awg.split

import androidx.datastore.preferences.core.MutablePreferences
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

object SplitStore {
    const val DEFAULT_DOH_URL = "https://freedns.controld.com/p2"

    private const val MAX_CACHED_PER_HOST = 64

    // Key names are persisted on devices; renaming them drops the user's lists.
    private val ENABLED = booleanPreferencesKey("split_sites_enabled")
    private val SITES_EXCLUDED = stringSetPreferencesKey("split_sites")
    private val SITES_INCLUDED = stringSetPreferencesKey("split_sites_included")
    private val SITES_MODE = stringPreferencesKey("split_sites_mode")
    private val APPS_EXCLUDED = stringSetPreferencesKey("split_apps_excluded")
    private val APPS_INCLUDED = stringSetPreferencesKey("split_apps_included")
    private val APPS_MODE = stringPreferencesKey("split_apps_mode")
    private val CACHE = stringPreferencesKey("split_sites_cache")
    private val DOH_URL = stringPreferencesKey("split_doh_url")
    private val PRIVATE_DNS_BYPASS = booleanPreferencesKey("split_private_dns_bypass")
    private val LAST_DETECTED_PRIVATE_DNS = stringPreferencesKey("split_last_detected_private_dns")

    data class Settings(
        val enabled: Boolean,
        val appsMode: SplitMode,
        val appsExcluded: Set<String>,
        val appsIncluded: Set<String>,
        val sitesMode: SplitMode,
        val sitesExcluded: List<String>,
        val sitesIncluded: List<String>,
        val dohUrl: String,
        val privateDnsBypass: Boolean,
        val lastDetectedPrivateDns: String,
        val cache: Map<String, List<String>>,
    ) {
        val activeApps get() = if (appsMode == SplitMode.EXCLUDE) appsExcluded else appsIncluded
        val activeSites get() = if (sitesMode == SplitMode.EXCLUDE) sitesExcluded else sitesIncluded

        fun ipCount(entry: String): Int? {
            if (isNetwork(entry)) return null
            return ((cache[entry] ?: emptyList()) + (cache["www.$entry"] ?: emptyList())).distinct().size
        }
    }

    private fun Preferences.toSettings() = Settings(
        enabled = this[ENABLED] ?: true,
        appsMode = SplitMode.of(this[APPS_MODE]),
        appsExcluded = this[APPS_EXCLUDED] ?: emptySet(),
        appsIncluded = this[APPS_INCLUDED] ?: emptySet(),
        sitesMode = SplitMode.of(this[SITES_MODE]),
        sitesExcluded = (this[SITES_EXCLUDED] ?: emptySet()).sorted(),
        sitesIncluded = (this[SITES_INCLUDED] ?: emptySet()).sorted(),
        dohUrl = this[DOH_URL]?.takeIf { it.isNotBlank() } ?: DEFAULT_DOH_URL,
        privateDnsBypass = this[PRIVATE_DNS_BYPASS] ?: false,
        lastDetectedPrivateDns = this[LAST_DETECTED_PRIVATE_DNS] ?: "",
        cache = decodeCache(this[CACHE]),
    )

    val settings: Flow<Settings>
        get() = Application.getPreferencesDataStore().data.map { it.toSettings() }

    suspend fun snapshot(): Settings = settings.first()

    suspend fun setEnabled(enabled: Boolean) = edit { it[ENABLED] = enabled }

    suspend fun setAppsMode(mode: SplitMode) = edit { it[APPS_MODE] = mode.key }

    suspend fun setSitesMode(mode: SplitMode) = edit { it[SITES_MODE] = mode.key }

    private fun MutablePreferences.appsKey() =
        if (SplitMode.of(this[APPS_MODE]) == SplitMode.EXCLUDE) APPS_EXCLUDED else APPS_INCLUDED

    private fun MutablePreferences.sitesKey() =
        if (SplitMode.of(this[SITES_MODE]) == SplitMode.EXCLUDE) SITES_EXCLUDED else SITES_INCLUDED

    suspend fun setAppSelected(pkg: String, selected: Boolean) = edit {
        val key = it.appsKey()
        val current = it[key] ?: emptySet()
        it[key] = if (selected) current + pkg else current - pkg
    }

    suspend fun removeApp(pkg: String) = setAppSelected(pkg, false)

    suspend fun importApps(excluded: Set<String>, included: Set<String>) = edit {
        if (excluded.isNotEmpty()) it[APPS_EXCLUDED] = (it[APPS_EXCLUDED] ?: emptySet()) + excluded
        if (included.isNotEmpty()) {
            it[APPS_INCLUDED] = (it[APPS_INCLUDED] ?: emptySet()) + included
            if (it[APPS_MODE] == null) it[APPS_MODE] = SplitMode.INCLUDE.key
        }
    }

    suspend fun setDohUrl(url: String) = edit { it[DOH_URL] = url.trim() }

    suspend fun setPrivateDnsBypass(enabled: Boolean) = edit { it[PRIVATE_DNS_BYPASS] = enabled }

    suspend fun setLastDetectedPrivateDns(host: String) = edit { it[LAST_DETECTED_PRIVATE_DNS] = host }

    suspend fun addSite(raw: String): String? {
        val entry = normalize(raw) ?: return null
        restoreSite(entry)
        return entry
    }

    suspend fun restoreSite(entry: String) = edit {
        val key = it.sitesKey()
        it[key] = (it[key] ?: emptySet()) + entry
    }

    suspend fun removeSite(entry: String) = edit {
        val key = it.sitesKey()
        it[key] = (it[key] ?: emptySet()) - entry
        val otherKey = if (key == SITES_EXCLUDED) SITES_INCLUDED else SITES_EXCLUDED
        if (entry !in (it[otherKey] ?: emptySet())) {
            val cache = decodeCache(it[CACHE]).toMutableMap()
            cache.remove(entry)
            cache.remove("www.$entry")
            it[CACHE] = encodeCache(cache)
        }
    }

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

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
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

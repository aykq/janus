/*
 * Copyright © 2026 AykQ. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.amnezia.awg.ui.split

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.amnezia.awg.backend.Tunnel
import org.amnezia.awg.split.PrivateDns
import org.amnezia.awg.split.SplitMode
import org.amnezia.awg.split.SplitStore
import org.amnezia.awg.util.ErrorMessages
import org.amnezia.awg.Application as JanusApplication

data class SplitUiState(
    val settings: SplitStore.Settings? = null,
    val apps: List<AppEntry>? = null,
    val query: String = "",
    val showSystem: Boolean = false,
    val pendingApply: Boolean = false,
    val detectedPrivateDns: String = "",
    val message: String? = null,
)

class SplitTunnelViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(SplitUiState())
    val state: StateFlow<SplitUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            SplitStore.settings.collect { s ->
                val detected = PrivateDns.detectHost(getApplication()) ?: s.lastDetectedPrivateDns
                _state.update { it.copy(settings = s, detectedPrivateDns = detected) }
            }
        }
        viewModelScope.launch {
            try {
                val apps = withContext(Dispatchers.IO) { InstalledApps.load(getApplication()) }
                _state.update { it.copy(apps = apps) }
            } catch (e: Throwable) {
                _state.update { it.copy(apps = emptyList(), message = ErrorMessages[e]) }
            }
        }
    }

    private suspend fun markPending() {
        val anyUp = JanusApplication.getTunnelManager().getTunnels().any { it.state == Tunnel.State.UP }
        if (anyUp) _state.update { it.copy(pendingApply = true) }
    }

    private fun changed(block: suspend () -> Unit) {
        viewModelScope.launch {
            block()
            markPending()
        }
    }

    fun setEnabled(on: Boolean) = changed { SplitStore.setEnabled(on) }
    fun setAppsMode(mode: SplitMode) = changed { SplitStore.setAppsMode(mode) }
    fun setSitesMode(mode: SplitMode) = changed { SplitStore.setSitesMode(mode) }
    fun setAppSelected(pkg: String, selected: Boolean) = changed { SplitStore.setAppSelected(pkg, selected) }
    fun removeSite(entry: String) = changed { SplitStore.removeSite(entry) }
    fun restoreSite(entry: String) = changed { SplitStore.restoreSite(entry) }
    fun setPrivateDnsBypass(on: Boolean) = changed { SplitStore.setPrivateDnsBypass(on) }

    suspend fun addSite(raw: String): Boolean {
        val ok = SplitStore.addSite(raw) != null
        if (ok) markPending()
        return ok
    }

    fun saveDohUrl(url: String): Boolean {
        val trimmed = url.trim()
        if (trimmed.isNotEmpty() && !trimmed.startsWith("https://")) return false
        changed { SplitStore.setDohUrl(trimmed) }
        return true
    }

    fun setQuery(q: String) = _state.update { it.copy(query = q) }
    fun setShowSystem(on: Boolean) = _state.update { it.copy(showSystem = on) }
    fun clearMessage() = _state.update { it.copy(message = null) }

    fun applyNow(appliedMessage: String) {
        viewModelScope.launch {
            val active = JanusApplication.getTunnelManager().getTunnels().filter { it.state == Tunnel.State.UP }
            try {
                for (tunnel in active) {
                    tunnel.setStateAsync(Tunnel.State.DOWN)
                    delay(300)
                    tunnel.setStateAsync(Tunnel.State.UP)
                }
                _state.update { it.copy(pendingApply = false, message = appliedMessage) }
            } catch (e: Throwable) {
                _state.update { it.copy(message = ErrorMessages[e]) }
            }
        }
    }
}

/*
 * Copyright © 2026 AykQ. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.amnezia.awg.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.amnezia.awg.Application
import org.amnezia.awg.backend.Tunnel
import org.amnezia.awg.model.ObservableTunnel
import org.amnezia.awg.split.SplitStore
import org.amnezia.awg.util.ErrorMessages

enum class Connection { OFF, CONNECTING, CONNECTED }

data class HomeState(
    val loaded: Boolean = false,
    val tunnelNames: List<String> = emptyList(),
    val selected: String? = null,
    val connection: Connection = Connection.OFF,
    val connectedSinceMillis: Long? = null,
    val rxBytes: Long = 0,
    val txBytes: Long = 0,
    val split: SplitStore.Settings? = null,
    val dpiJunkCount: Int? = null,
    val nowMillis: Long = 0,
    val error: String? = null,
)

class HomeViewModel : ViewModel() {
    private val manager = Application.getTunnelManager()
    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()
    private var busy = false

    init {
        viewModelScope.launch { SplitStore.settings.collect { s -> _state.update { it.copy(split = s) } } }
        viewModelScope.launch {
            while (true) {
                refresh()
                delay(1000)
            }
        }
    }

    private suspend fun selectedTunnel(): ObservableTunnel? {
        val tunnels = manager.getTunnels()
        return manager.lastUsedTunnel?.takeIf { it in tunnels } ?: tunnels.firstOrNull()
    }

    fun refresh() {
        viewModelScope.launch {
            val tunnels = manager.getTunnels()
            val tunnel = selectedTunnel()
            val up = tunnel?.state == Tunnel.State.UP
            val connection = when {
                busy -> Connection.CONNECTING
                !up -> Connection.OFF
                tunnel?.connectionStatus == ObservableTunnel.ConnectionStatus.CONNECTED -> Connection.CONNECTED
                else -> Connection.CONNECTING
            }
            val stats = if (up) runCatching { tunnel!!.getStatisticsAsync() }.getOrNull() else null
            val junk = tunnel?.let { t -> runCatching { t.getConfigAsync().`interface`.junkPacketCount.orElse(0) }.getOrNull() }
            _state.update {
                it.copy(
                    loaded = true,
                    tunnelNames = tunnels.map { t -> t.name },
                    selected = tunnel?.name,
                    connection = connection,
                    connectedSinceMillis = tunnel?.let { t -> manager.connectedAt[t.name] }?.takeIf { up },
                    rxBytes = stats?.totalRx() ?: 0,
                    txBytes = stats?.totalTx() ?: 0,
                    dpiJunkCount = junk,
                    nowMillis = System.currentTimeMillis(),
                )
            }
        }
    }

    fun toggle() {
        viewModelScope.launch {
            val tunnel = selectedTunnel() ?: return@launch
            val target = if (tunnel.state == Tunnel.State.UP) Tunnel.State.DOWN else Tunnel.State.UP
            busy = true
            refresh()
            try {
                tunnel.setStateAsync(target)
            } catch (e: Throwable) {
                _state.update { it.copy(error = ErrorMessages[e]) }
            }
            busy = false
            refresh()
        }
    }

    fun select(name: String) {
        viewModelScope.launch {
            val tunnels = manager.getTunnels()
            val next = tunnels[name] ?: return@launch
            val current = selectedTunnel()
            if (current == next) return@launch
            val wasUp = current?.state == Tunnel.State.UP
            busy = wasUp
            manager.selectTunnel(next)
            refresh()
            try {
                if (wasUp) {
                    current!!.setStateAsync(Tunnel.State.DOWN)
                    next.setStateAsync(Tunnel.State.UP)
                }
            } catch (e: Throwable) {
                _state.update { it.copy(error = ErrorMessages[e]) }
            }
            busy = false
            refresh()
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
}

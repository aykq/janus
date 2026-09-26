/*
 * Copyright © 2026 AykQ. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.amnezia.awg.ui.home

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.amnezia.awg.Application
import org.amnezia.awg.R
import org.amnezia.awg.activity.LogViewerActivity
import org.amnezia.awg.activity.MainActivity
import org.amnezia.awg.activity.SettingsActivity
import org.amnezia.awg.activity.TunnelEditActivity
import org.amnezia.awg.backend.GoBackend
import org.amnezia.awg.split.SplitMode
import org.amnezia.awg.split.SplitStore
import org.amnezia.awg.util.ErrorMessages
import java.util.Locale

@Composable
fun HomeRoute(viewModel: HomeViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) viewModel.toggle()
    }
    val prepareError = stringResource(R.string.home_error_prepare)

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.clearError()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                viewModel.refresh()
                delay(1000)
            }
        }
    }

    HomeScreen(
        state = state,
        snackbar = snackbar,
        onToggle = {
            scope.launch {
                val intent = try {
                    if (Application.getBackend() is GoBackend) GoBackend.VpnService.prepare(context) else null
                } catch (e: Throwable) {
                    snackbar.showSnackbar(prepareError.format(ErrorMessages[e]))
                    return@launch
                }
                if (intent != null) permission.launch(intent) else viewModel.toggle()
            }
        },
        onSelect = viewModel::select,
        onOpenSplit = { openSplit(context) },
        onOpenDpi = { state.selected?.let { context.startActivity(TunnelEditActivity.intent(context, it)) } },
        onAddTunnel = { context.startActivity(Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_ADD_TUNNEL, true)) },
        onManage = { context.startActivity(Intent(context, MainActivity::class.java)) },
        onLog = { context.startActivity(Intent(context, LogViewerActivity::class.java)) },
        onSettings = { context.startActivity(Intent(context, SettingsActivity::class.java)) },
    )
}

internal fun openSplit(context: Context) {
    context.startActivity(Intent(context, SettingsActivity::class.java))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeState,
    snackbar: SnackbarHostState,
    onToggle: () -> Unit,
    onSelect: (String) -> Unit,
    onOpenSplit: () -> Unit,
    onOpenDpi: () -> Unit,
    onAddTunnel: () -> Unit,
    onManage: () -> Unit,
    onLog: () -> Unit,
    onSettings: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = null) }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.home_add_tunnel)) }, onClick = { menuOpen = false; onAddTunnel() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.home_manage_tunnels)) }, onClick = { menuOpen = false; onManage() })
                        if (state.selected != null)
                            DropdownMenuItem(text = { Text(stringResource(R.string.home_edit_tunnel)) }, onClick = { menuOpen = false; onOpenDpi() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.home_log)) }, onClick = { menuOpen = false; onLog() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.home_settings)) }, onClick = { menuOpen = false; onSettings() })
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!state.loaded) {
                Spacer(Modifier.height(96.dp))
                CircularProgressIndicator()
                return@Column
            }
            if (state.tunnelNames.isEmpty()) {
                Spacer(Modifier.height(96.dp))
                Text(stringResource(R.string.home_no_tunnel), style = MaterialTheme.typography.titleMedium)
                Button(onClick = onAddTunnel) { Text(stringResource(R.string.home_add_tunnel)) }
                return@Column
            }
            Spacer(Modifier.height(24.dp))
            ConnectButton(state.connection, onToggle)
            StatusText(state)
            TunnelPicker(state, onSelect)
            SplitCard(state.split, onOpenSplit)
            DpiCard(state.dpiJunkCount, onOpenDpi)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ConnectButton(connection: Connection, onToggle: () -> Unit) {
    val color by animateColorAsState(
        when (connection) {
            Connection.OFF -> MaterialTheme.colorScheme.surfaceVariant
            Connection.CONNECTING -> MaterialTheme.colorScheme.secondaryContainer
            Connection.CONNECTED -> MaterialTheme.colorScheme.primary
        },
        label = "connectColor",
    )
    val iconTint = when (connection) {
        Connection.CONNECTED -> MaterialTheme.colorScheme.onPrimary
        Connection.CONNECTING -> MaterialTheme.colorScheme.onSecondaryContainer
        Connection.OFF -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val description = stringResource(R.string.home_toggle_description)
    Surface(
        onClick = onToggle,
        enabled = connection != Connection.CONNECTING,
        shape = CircleShape,
        color = color,
        shadowElevation = 6.dp,
        modifier = Modifier
            .size(168.dp)
            .semantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (connection == Connection.CONNECTING)
                CircularProgressIndicator(modifier = Modifier.size(168.dp), strokeWidth = 4.dp)
            Icon(painterResource(R.drawable.ic_power), contentDescription = null, tint = iconTint, modifier = Modifier.size(64.dp))
        }
    }
}

private fun formatElapsed(elapsedMillis: Long): String {
    val s = (elapsedMillis / 1000).coerceAtLeast(0)
    return String.format(Locale.ROOT, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
}

@Composable
private fun StatusText(state: HomeState) {
    val context = LocalContext.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val since = state.connectedSinceMillis
        Text(
            when (state.connection) {
                Connection.OFF -> stringResource(R.string.home_status_off)
                Connection.CONNECTING -> stringResource(R.string.home_status_connecting)
                Connection.CONNECTED -> if (since == null) stringResource(R.string.home_status_connected)
                else stringResource(R.string.home_status_connected_since, formatElapsed(state.nowMillis - since))
            },
            style = MaterialTheme.typography.titleLarge,
        )
        if (state.connection == Connection.CONNECTED)
            Text(
                stringResource(R.string.home_traffic, Formatter.formatShortFileSize(context, state.rxBytes), Formatter.formatShortFileSize(context, state.txBytes)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TunnelPicker(state: HomeState, onSelect: (String) -> Unit) {
    val selected = state.selected ?: return
    if (state.tunnelNames.size < 2) {
        Card(modifier = Modifier.fillMaxWidth()) {
            ListItem(overlineContent = { Text(stringResource(R.string.home_tunnel_label)) }, headlineContent = { Text(selected) })
        }
        return
    }
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.home_tunnel_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (name in state.tunnelNames)
                DropdownMenuItem(text = { Text(name) }, onClick = { expanded = false; onSelect(name) })
        }
    }
}

@Composable
private fun SplitCard(split: SplitStore.Settings?, onClick: () -> Unit) {
    val summary = when {
        split == null -> ""
        !split.enabled -> stringResource(R.string.home_split_off)
        split.appsMode == split.sitesMode && split.appsMode == SplitMode.EXCLUDE ->
            stringResource(R.string.home_split_summary_exclude, split.activeApps.size, split.activeSites.size)
        split.appsMode == split.sitesMode ->
            stringResource(R.string.home_split_summary_include, split.activeApps.size, split.activeSites.size)
        else -> stringResource(
            R.string.home_split_summary_mixed,
            if (split.appsMode == SplitMode.EXCLUDE) stringResource(R.string.home_split_apps_exclude, split.activeApps.size)
            else stringResource(R.string.home_split_apps_include, split.activeApps.size),
            if (split.sitesMode == SplitMode.EXCLUDE) stringResource(R.string.home_split_sites_exclude, split.activeSites.size)
            else stringResource(R.string.home_split_sites_include, split.activeSites.size),
        )
    }
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.home_split_title)) },
            supportingContent = { Text(summary) },
            trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        )
    }
}

@Composable
private fun DpiCard(junkCount: Int?, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.home_dpi_title)) },
            supportingContent = {
                Text(if (junkCount != null && junkCount > 0) stringResource(R.string.home_dpi_on, junkCount) else stringResource(R.string.home_dpi_off))
            },
            trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        )
    }
}

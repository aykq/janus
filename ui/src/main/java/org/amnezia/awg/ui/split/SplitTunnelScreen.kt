/*
 * Copyright © 2026 AykQ. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.amnezia.awg.ui.split

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.amnezia.awg.R
import org.amnezia.awg.split.SplitMode
import org.amnezia.awg.split.SplitStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitTunnelScreen(viewModel: SplitTunnelViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val applied = stringResource(R.string.split_applied)
    // Must outlive rows: a removed row's own scope is cancelled before its undo snackbar finishes.
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.split_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                },
            )
        },
        bottomBar = {
            if (state.pendingApply)
                Surface(tonalElevation = 3.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.split_pending), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { viewModel.applyNow(applied) }) { Text(stringResource(R.string.split_apply_now)) }
                    }
                }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val settings = state.settings
        if (settings == null) {
            CircularProgressIndicator(modifier = Modifier.padding(padding).padding(32.dp))
            return@Scaffold
        }
        LazyColumn(modifier = Modifier
            .fillMaxSize()
            .padding(padding)) {
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.split_enabled)) },
                    supportingContent = { Text(stringResource(R.string.split_description)) },
                    trailingContent = { Switch(checked = settings.enabled, onCheckedChange = viewModel::setEnabled) },
                )
                PrimaryTabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.split_tab_apps)) })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.split_tab_sites)) })
                }
                ModeRow(if (tab == 0) settings.appsMode else settings.sitesMode) {
                    if (tab == 0) viewModel.setAppsMode(it) else viewModel.setSitesMode(it)
                }
                if (settings.appsMode == SplitMode.INCLUDE && settings.sitesMode == SplitMode.INCLUDE)
                    Note(stringResource(R.string.split_both_include_note))
            }
            if (tab == 0) appsTab(state, settings, viewModel)
            else sitesTab(state, settings, viewModel, snackbar, scope)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeRow(mode: SplitMode, onChange: (SplitMode) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp)) {
        SegmentedButton(
            selected = mode == SplitMode.EXCLUDE,
            onClick = { onChange(SplitMode.EXCLUDE) },
            shape = SegmentedButtonDefaults.itemShape(0, 2),
        ) { Text(stringResource(R.string.split_mode_exclude)) }
        SegmentedButton(
            selected = mode == SplitMode.INCLUDE,
            onClick = { onChange(SplitMode.INCLUDE) },
            shape = SegmentedButtonDefaults.itemShape(1, 2),
        ) { Text(stringResource(R.string.split_mode_include)) }
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

private fun LazyListScope.appsTab(state: SplitUiState, settings: SplitStore.Settings, viewModel: SplitTunnelViewModel) {
    item {
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text(stringResource(R.string.split_apps_search)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )
        Row(modifier = Modifier.padding(horizontal = 16.dp)) {
            FilterChip(
                selected = state.showSystem,
                onClick = { viewModel.setShowSystem(!state.showSystem) },
                label = { Text(stringResource(R.string.split_apps_show_system)) },
            )
        }
    }
    val apps = state.apps
    if (apps == null) {
        item { CircularProgressIndicator(modifier = Modifier.padding(32.dp)) }
        return
    }
    val selected = settings.activeApps
    val known = apps.map { it.pkg }.toSet()
    val missing = selected.filter { it !in known }.sorted()
    items(missing, key = { "missing:$it" }) { pkg ->
        ListItem(
            headlineContent = { Text(pkg) },
            supportingContent = { Text(stringResource(R.string.split_app_missing)) },
            trailingContent = {
                IconButton(onClick = { viewModel.setAppSelected(pkg, false) }) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.split_delete))
                }
            },
        )
    }
    val q = state.query.trim()
    val visible = apps
        .filter { state.showSystem || !it.system || it.pkg in selected }
        .filter { q.isEmpty() || it.label.contains(q, ignoreCase = true) || it.pkg.contains(q, ignoreCase = true) }
        .sortedByDescending { it.pkg in selected }
    items(visible, key = { it.pkg }) { app ->
        val checked = app.pkg in selected
        ListItem(
            leadingContent = { Image(app.icon, contentDescription = null, modifier = Modifier.size(40.dp)) },
            headlineContent = { Text(app.label) },
            supportingContent = { Text(app.pkg, style = MaterialTheme.typography.bodySmall) },
            trailingContent = { Checkbox(checked = checked, onCheckedChange = { viewModel.setAppSelected(app.pkg, it) }) },
            modifier = Modifier.clickable { viewModel.setAppSelected(app.pkg, !checked) },
        )
    }
}

private fun LazyListScope.sitesTab(state: SplitUiState, settings: SplitStore.Settings, viewModel: SplitTunnelViewModel, snackbar: SnackbarHostState, scope: CoroutineScope) {
    if (settings.sitesMode == SplitMode.EXCLUDE && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
        item { Note(stringResource(R.string.sites_api_warning)) }
    item { AddSiteRow(viewModel, snackbar) }
    val sites = settings.activeSites
    if (sites.isEmpty())
        item { Note(stringResource(R.string.split_sites_empty)) }
    items(sites, key = { it }) { entry -> SiteRow(entry, settings.ipCount(entry), viewModel, snackbar, scope) }
    item { AdvancedSection(state, settings, viewModel) }
}

@Composable
private fun AddSiteRow(viewModel: SplitTunnelViewModel, snackbar: SnackbarHostState) {
    var text by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val invalid = stringResource(R.string.sites_invalid)
    val submit = {
        val raw = text
        if (raw.isNotBlank()) scope.launch {
            if (viewModel.addSite(raw)) text = "" else snackbar.showSnackbar(invalid.format(raw))
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text(stringResource(R.string.split_site_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.weight(1f),
        )
        Button(onClick = { submit() }) { Text(stringResource(R.string.split_site_add)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SiteRow(entry: String, ipCount: Int?, viewModel: SplitTunnelViewModel, snackbar: SnackbarHostState, scope: CoroutineScope) {
    val removedText = stringResource(R.string.split_site_removed, entry)
    val undo = stringResource(R.string.split_undo)
    val remove = {
        viewModel.removeSite(entry)
        scope.launch {
            if (snackbar.showSnackbar(removedText, undo, duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed)
                viewModel.restoreSite(entry)
        }
    }
    val dismiss = rememberSwipeToDismissBoxState(confirmValueChange = {
        if (it != SwipeToDismissBoxValue.Settled) remove()
        it != SwipeToDismissBoxValue.Settled
    })
    SwipeToDismissBox(state = dismiss, backgroundContent = {}) {
        ListItem(
            headlineContent = { Text(entry) },
            supportingContent = {
                Text(when (ipCount) {
                    null -> stringResource(R.string.sites_entry_network)
                    0 -> stringResource(R.string.sites_entry_unresolved)
                    else -> pluralStringResource(R.plurals.sites_entry_ips, ipCount, ipCount)
                })
            },
            trailingContent = {
                IconButton(onClick = { remove() }) { Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.split_delete)) }
            },
        )
    }
}

@Composable
private fun AdvancedSection(state: SplitUiState, settings: SplitStore.Settings, viewModel: SplitTunnelViewModel) {
    var open by rememberSaveable { mutableStateOf(false) }
    var doh by rememberSaveable(settings.dohUrl) { mutableStateOf(settings.dohUrl) }
    var dohError by remember { mutableStateOf(false) }
    Column {
        HorizontalDivider(modifier = Modifier.padding(top = 16.dp))
        ListItem(
            headlineContent = { Text(stringResource(R.string.split_advanced)) },
            trailingContent = { Icon(if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null) },
            modifier = Modifier.clickable { open = !open },
        )
        if (!open) return@Column
        OutlinedTextField(
            value = doh,
            onValueChange = { doh = it; dohError = false },
            label = { Text(stringResource(R.string.split_doh_label)) },
            supportingText = { Text(stringResource(if (dohError) R.string.sites_doh_invalid else R.string.split_doh_description)) },
            isError = dohError,
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )
        Row(modifier = Modifier.padding(horizontal = 16.dp)) {
            TextButton(onClick = { dohError = !viewModel.saveDohUrl(doh) }) { Text(stringResource(R.string.split_doh_save)) }
        }
        ListItem(
            headlineContent = { Text(stringResource(R.string.split_private_dns_bypass)) },
            supportingContent = {
                Column {
                    Text(stringResource(R.string.split_private_dns_description))
                    Text(
                        if (state.detectedPrivateDns.isBlank()) stringResource(R.string.sites_private_dns_not_detected)
                        else stringResource(R.string.sites_private_dns_detected, state.detectedPrivateDns),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            trailingContent = { Switch(checked = settings.privateDnsBypass, onCheckedChange = viewModel::setPrivateDnsBypass) },
        )
    }
}

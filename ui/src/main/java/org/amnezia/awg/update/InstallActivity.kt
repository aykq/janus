/*
 * Copyright © 2026 AykQ. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.amnezia.awg.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.withContext
import org.amnezia.awg.R
import org.amnezia.awg.ui.theme.JanusTheme
import java.io.File
import java.io.IOException

class InstallActivity : AppCompatActivity() {
    private sealed interface State {
        data class Downloading(val progress: Float) : State
        data object Verifying : State
        data object Waiting : State
        data object NeedsPermission : State
        data class Error(val message: String) : State
    }

    private var state by mutableStateOf<State>(State.Downloading(0f))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { JanusTheme { Screen() } }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                InstallEvents.latest.collect { if (it != null) consumeLatestEvent() }
            }
        }
        start()
    }

    override fun onResume() {
        super.onResume()
        InstallEvents.activityVisible = true
        consumeLatestEvent()
        if (state == State.NeedsPermission && canInstall()) start()
    }

    override fun onPause() {
        super.onPause()
        InstallEvents.activityVisible = false
    }

    private fun consumeLatestEvent() {
        when (val event = InstallEvents.latest.getAndUpdate { null }) {
            is InstallEvent.NeedsConfirmation -> startActivity(event.confirm)
            is InstallEvent.Failed -> state = State.Error(event.message)
            InstallEvent.Cancelled -> finish()
            null -> Unit
        }
    }

    private fun canInstall() = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || packageManager.canRequestPackageInstalls()

    private suspend fun failWith(message: String) {
        withContext(Dispatchers.IO) { File(cacheDir, UpdateInstaller.CACHE_DIR).deleteRecursively() }
        state = State.Error(message)
    }

    private fun start() {
        if (!canInstall()) {
            state = State.NeedsPermission
            return
        }
        val versionCode = intent.getLongExtra(EXTRA_VERSION_CODE, 0)
        val sha256 = intent.getStringExtra(EXTRA_SHA256)
        val assetUrl = intent.getStringExtra(EXTRA_ASSET_URL)
        if (versionCode <= 0 || sha256 == null || assetUrl == null) {
            finish()
            return
        }
        InstallEvents.latest.value = null
        state = State.Downloading(0f)
        lifecycleScope.launch {
            var lastPercent = -1
            try {
                val apk = UpdateInstaller.download(this@InstallActivity, versionCode, assetUrl, sha256) { p ->
                    val percent = (p * 100).toInt()
                    if (percent != lastPercent) {
                        lastPercent = percent
                        runOnUiThread { state = if (p >= 1f) State.Verifying else State.Downloading(p) }
                    }
                }
                state = State.Waiting
                try {
                    UpdateInstaller.install(this@InstallActivity, apk)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failWith(getString(R.string.update_error_install, e.message ?: e.javaClass.simpleName))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: UpdateInstaller.ChecksumMismatch) {
                failWith(getString(R.string.update_error_checksum))
            } catch (e: GitHubClient.HttpError) {
                failWith(getString(R.string.update_error_download, "HTTP ${e.code}"))
            } catch (e: IOException) {
                failWith(getString(R.string.update_error_download, e.message ?: e.javaClass.simpleName))
            } catch (e: IllegalArgumentException) {
                failWith(getString(R.string.update_error_download, e.message ?: "invalid URL"))
            } catch (e: Exception) {
                failWith(getString(R.string.update_error_download, e.message ?: e.javaClass.simpleName))
            }
        }
    }

    @Composable
    private fun Screen() {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.update_install_title), style = MaterialTheme.typography.headlineSmall)
                when (val s = state) {
                    is State.Downloading -> {
                        Text(stringResource(R.string.update_install_downloading, (s.progress * 100).toInt()))
                        LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                    }
                    State.Verifying -> Text(stringResource(R.string.update_install_verifying))
                    State.Waiting -> Text(stringResource(R.string.update_install_waiting))
                    State.NeedsPermission -> {
                        Text(stringResource(R.string.update_install_permission))
                        Button(onClick = {
                            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
                        }) { Text(stringResource(R.string.update_install_open_settings)) }
                    }
                    is State.Error -> {
                        Text(s.message, color = MaterialTheme.colorScheme.error)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = ::start) { Text(stringResource(R.string.update_install_retry)) }
                            TextButton(onClick = ::finish) { Text(stringResource(R.string.update_install_close)) }
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_VERSION_CODE = "version_code"
        const val EXTRA_SHA256 = "sha256"
        const val EXTRA_ASSET_URL = "asset_url"

        fun intent(context: Context, release: UpdateRelease): Intent =
            Intent(context, InstallActivity::class.java)
                .putExtra(EXTRA_VERSION_CODE, release.versionCode)
                .putExtra(EXTRA_SHA256, release.sha256)
                .putExtra(EXTRA_ASSET_URL, release.assetUrl)
    }
}

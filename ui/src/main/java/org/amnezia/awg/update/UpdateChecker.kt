/*
 * Copyright © 2026 AykQ. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.amnezia.awg.update

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

object UpdateChecker {
    suspend fun check(context: Context, notify: Boolean, force: Boolean): CheckStatus = withContext(Dispatchers.IO) {
        val token = TokenStore.get()
        val status = try {
            val releases = ReleaseParser.parse(GitHubClient(token).fetchReleases())
            val newest = UpdatePolicy.newest(releases, installedVersionCode(context), UpdateState.candidatesEnabled())
            if (newest == null) CheckStatus.UpToDate else CheckStatus.Available(newest)
        } catch (e: GitHubClient.HttpError) {
            CheckStatus.fromHttp(e.code, e.rateLimitRemaining, token != null)
        } catch (_: IOException) {
            CheckStatus.NoConnection
        } catch (e: IllegalArgumentException) {
            CheckStatus.Failed(e.message ?: "Invalid response")
        }
        UpdateState.recordCheck(System.currentTimeMillis(), StatusCodec.encode(status.kind, status.arg))
        if (notify && status is CheckStatus.Available &&
            (force || UpdatePolicy.shouldNotify(status.release, UpdateState.lastNotified()))
        ) {
            if (UpdateNotifier.notifyAvailable(context, status.release)) UpdateState.setLastNotified(status.release.versionCode)
        }
        status
    }

    fun installedVersionCode(context: Context): Long =
        PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))
}

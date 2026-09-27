/*
 * Copyright © 2026 AykQ. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.amnezia.awg.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat
import org.amnezia.awg.R
import java.io.File

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                if (confirm == null) {
                    File(context.cacheDir, UpdateInstaller.CACHE_DIR).deleteRecursively()
                    InstallEvents.latest.value = InstallEvent.Failed(context.getString(R.string.update_error_install, "missing confirmation intent"))
                    if (!InstallEvents.activityVisible) UpdateNotifier.notifyError(context, context.getString(R.string.update_error_install, "missing confirmation intent"))
                    return
                }
                InstallEvents.latest.value = InstallEvent.NeedsConfirmation(confirm)
                if (!InstallEvents.activityVisible) UpdateNotifier.notifyConfirm(context, confirm)
            }
            PackageInstaller.STATUS_SUCCESS -> {
                File(context.cacheDir, UpdateInstaller.CACHE_DIR).deleteRecursively()
            }
            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                File(context.cacheDir, UpdateInstaller.CACHE_DIR).deleteRecursively()
                InstallEvents.latest.value = InstallEvent.Cancelled
            }
            else -> {
                File(context.cacheDir, UpdateInstaller.CACHE_DIR).deleteRecursively()
                val message = if (status == PackageInstaller.STATUS_FAILURE_CONFLICT)
                    context.getString(R.string.update_error_signature)
                else
                    context.getString(R.string.update_error_install, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "status $status")
                InstallEvents.latest.value = InstallEvent.Failed(message)
                if (!InstallEvents.activityVisible) UpdateNotifier.notifyError(context, message)
            }
        }
    }
}

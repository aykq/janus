/*
 * Copyright © 2026 AykQ. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.amnezia.awg.ui.split

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

data class AppEntry(val pkg: String, val label: String, val icon: ImageBitmap, val system: Boolean)

object InstalledApps {
    fun load(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val size = (40 * context.resources.displayMetrics.density).toInt()
        return packagesWithInternet(pm)
            .filter { it.packageName != context.packageName }
            .map { info ->
                val app = info.applicationInfo
                AppEntry(
                    pkg = info.packageName,
                    label = app?.loadLabel(pm)?.toString() ?: info.packageName,
                    icon = (app?.loadIcon(pm) ?: pm.defaultActivityIcon).toBitmap(size, size).asImageBitmap(),
                    system = app != null && (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0 && (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0,
                )
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    private fun packagesWithInternet(pm: PackageManager): List<PackageInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            pm.getPackagesHoldingPermissions(arrayOf(Manifest.permission.INTERNET), PackageManager.PackageInfoFlags.of(0L))
        else
            @Suppress("DEPRECATION")
            pm.getPackagesHoldingPermissions(arrayOf(Manifest.permission.INTERNET), 0)
}

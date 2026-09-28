/*
 * Copyright © 2026 AykQ. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.amnezia.awg.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

object UpdateInstaller {
    const val CACHE_DIR = "updates"

    class ChecksumMismatch : IOException("Checksum mismatch")

    suspend fun download(context: Context, versionCode: Long, assetUrl: String, sha256: String, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, CACHE_DIR)
            dir.deleteRecursively()
            dir.mkdirs()
            val file = File(dir, "janus-$versionCode.apk")
            GitHubClient.download(assetUrl, file) { done, total ->
                if (total > 0) onProgress(done.toFloat() / total)
            }
            if (Checksums.sha256(file) != sha256) {
                file.delete()
                throw ChecksumMismatch()
            }
            file
        }

    suspend fun install(context: Context, apk: File) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        val sessionId = installer.createSession(params)
        val session = installer.openSession(sessionId)
        session.use {
            try {
                apk.inputStream().use { input ->
                    session.openWrite("janus.apk", 0, apk.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
            } catch (e: Exception) {
                session.abandon()
                throw e
            }
            // The installer fills in status extras, so the PendingIntent must stay mutable on S+.
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            val callback = PendingIntent.getBroadcast(
                context, sessionId, Intent(context, InstallResultReceiver::class.java).setPackage(context.packageName), flags,
            )
            session.commit(callback.intentSender)
        }
    }
}

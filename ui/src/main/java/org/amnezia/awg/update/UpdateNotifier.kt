/*
 * Copyright © 2026 AykQ. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.amnezia.awg.update

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.amnezia.awg.R

object UpdateNotifier {
    private const val CHANNEL_ID = "updates"
    private const val AVAILABLE_ID = 0x4a55
    private const val ERROR_ID = 0x4a56

    private fun ensureChannel(context: Context) {
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.update_channel_name))
                .build()
        )
    }

    @SuppressLint("MissingPermission")
    fun notifyAvailable(context: Context, release: UpdateRelease): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        ensureChannel(context)
        val install = PendingIntent.getActivity(
            context, release.versionCode.toInt(), InstallActivity.intent(context, release),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = if (release.channel == UpdateChannel.CANDIDATE)
            context.getString(R.string.update_notification_candidate, release.pr)
        else
            context.getString(R.string.update_notification_stable, release.versionCode.toInt())
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle(title)
            .setContentText(release.title ?: context.getString(R.string.update_notification_text))
            .setContentIntent(install)
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.update_action_install), install)
        release.prUrl?.let { url ->
            val open = PendingIntent.getActivity(
                context, release.versionCode.toInt(), Intent(Intent.ACTION_VIEW, Uri.parse(url)),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.addAction(0, context.getString(R.string.update_action_open_pr), open)
        }
        manager.notify(AVAILABLE_ID, builder.build())
        return true
    }

    @SuppressLint("MissingPermission")
    fun notifyError(context: Context, message: String) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        ensureChannel(context)
        manager.notify(
            ERROR_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_tile)
                .setContentTitle(context.getString(R.string.update_error_title))
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setAutoCancel(true)
                .build()
        )
    }
}

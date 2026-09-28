/*
 * Copyright © 2026 AykQ. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.amnezia.awg.update

import android.content.Context
import android.text.format.DateUtils
import android.util.AttributeSet
import androidx.preference.Preference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.amnezia.awg.R
import org.amnezia.awg.util.lifecycleScope

class UpdateCheckPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {
    private var summaryText: String = context.getString(R.string.update_check_never)
    private var available: UpdateRelease? = null
    private var running = false

    override fun getSummary(): CharSequence = summaryText

    init {
        lifecycleScope.launch {
            val (at, encoded) = UpdateState.lastCheck()
            val decoded = StatusCodec.decode(encoded)?.let { (kind, arg) ->
                // A stored "available" result is stale once that build (or newer) is installed.
                val installed = UpdateChecker.installedVersionCode(context)
                if (kind == StatusKind.AVAILABLE && (arg?.toLongOrNull() ?: 0L) <= installed) StatusKind.UP_TO_DATE to null else kind to arg
            }
            if (at > 0 && decoded != null) {
                summaryText = context.getString(
                    R.string.update_check_last,
                    DateUtils.getRelativeTimeSpanString(at).toString(),
                    UpdateStatusText.describe(context, decoded.first, decoded.second),
                )
                notifyChanged()
            }
        }
    }

    override fun onClick() {
        available?.let {
            context.startActivity(InstallActivity.intent(context, it))
            return
        }
        if (running) return
        running = true
        summaryText = context.getString(R.string.update_check_running)
        notifyChanged()
        lifecycleScope.launch {
            try {
                val status = try {
                    UpdateChecker.check(context.applicationContext, notify = true, force = true)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    CheckStatus.Failed(e.message ?: e.javaClass.simpleName)
                }
                available = (status as? CheckStatus.Available)?.release
                summaryText = UpdateStatusText.describe(context, status.kind, status.arg)
            } finally {
                running = false
                notifyChanged()
            }
        }
    }
}

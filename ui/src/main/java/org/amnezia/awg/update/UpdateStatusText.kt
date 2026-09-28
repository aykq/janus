/*
 * Copyright © 2026 AykQ. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.amnezia.awg.update

import android.content.Context
import org.amnezia.awg.R

object UpdateStatusText {
    fun describe(context: Context, kind: StatusKind, arg: String?): String = when (kind) {
        StatusKind.UP_TO_DATE -> context.getString(R.string.update_status_up_to_date)
        StatusKind.AVAILABLE -> context.getString(R.string.update_status_available, arg ?: "")
        StatusKind.NO_CONNECTION -> context.getString(R.string.update_status_no_connection)
        StatusKind.NOT_REACHABLE -> context.getString(R.string.update_status_not_reachable)
        StatusKind.RATE_LIMITED -> context.getString(R.string.update_status_rate_limited)
        StatusKind.FAILED -> context.getString(R.string.update_status_failed, arg ?: "")
    }
}

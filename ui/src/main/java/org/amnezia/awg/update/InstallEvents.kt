/*
 * Copyright © 2026 AykQ. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.amnezia.awg.update

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow

sealed interface InstallEvent {
    data class NeedsConfirmation(val confirm: Intent) : InstallEvent
    data class Failed(val message: String) : InstallEvent
    data object Cancelled : InstallEvent
}

object InstallEvents {
    val latest = MutableStateFlow<InstallEvent?>(null)
    @Volatile var activityVisible = false
}

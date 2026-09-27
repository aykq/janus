/*
 * Copyright © 2026 AykQ. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.amnezia.awg.update

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import org.amnezia.awg.Application

object UpdateState {
    const val CANDIDATES_KEY = "update_candidates"
    private val CANDIDATES = booleanPreferencesKey(CANDIDATES_KEY)
    private val LAST_NOTIFIED = longPreferencesKey("update_last_notified")
    private val LAST_CHECK_AT = longPreferencesKey("update_last_check_at")
    private val LAST_STATUS = stringPreferencesKey("update_last_status")
    private val NOTIFICATION_ASKED = booleanPreferencesKey("update_notification_asked")

    private suspend fun prefs() = Application.getPreferencesDataStore().data.first()

    suspend fun candidatesEnabled(): Boolean = prefs()[CANDIDATES] ?: true

    suspend fun lastNotified(): Long = prefs()[LAST_NOTIFIED] ?: 0L

    suspend fun setLastNotified(code: Long) {
        Application.getPreferencesDataStore().edit { it[LAST_NOTIFIED] = code }
    }

    suspend fun recordCheck(at: Long, encodedStatus: String) {
        Application.getPreferencesDataStore().edit {
            it[LAST_CHECK_AT] = at
            it[LAST_STATUS] = encodedStatus
        }
    }

    suspend fun lastCheck(): Pair<Long, String?> = prefs().let { (it[LAST_CHECK_AT] ?: 0L) to it[LAST_STATUS] }

    suspend fun notificationAsked(): Boolean = prefs()[NOTIFICATION_ASKED] ?: false

    suspend fun setNotificationAsked() {
        Application.getPreferencesDataStore().edit { it[NOTIFICATION_ASKED] = true }
    }
}

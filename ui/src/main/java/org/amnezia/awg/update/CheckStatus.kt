/*
 * Copyright © 2026 AykQ. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.amnezia.awg.update

enum class StatusKind { UP_TO_DATE, AVAILABLE, NO_CONNECTION, NOT_REACHABLE, RATE_LIMITED, FAILED }

sealed interface CheckStatus {
    data object UpToDate : CheckStatus
    data class Available(val release: UpdateRelease) : CheckStatus
    data object NoConnection : CheckStatus
    data object NotReachable : CheckStatus
    data object RateLimited : CheckStatus
    data class Failed(val detail: String) : CheckStatus

    companion object {
        fun fromHttp(code: Int, rateLimitRemaining: String?): CheckStatus = when {
            (code == 403 || code == 429) && rateLimitRemaining == "0" -> RateLimited
            code == 403 || code == 404 -> NotReachable
            else -> Failed("HTTP $code")
        }
    }
}

val CheckStatus.kind: StatusKind
    get() = when (this) {
        CheckStatus.UpToDate -> StatusKind.UP_TO_DATE
        is CheckStatus.Available -> StatusKind.AVAILABLE
        CheckStatus.NoConnection -> StatusKind.NO_CONNECTION
        CheckStatus.NotReachable -> StatusKind.NOT_REACHABLE
        CheckStatus.RateLimited -> StatusKind.RATE_LIMITED
        is CheckStatus.Failed -> StatusKind.FAILED
    }

val CheckStatus.arg: String?
    get() = when (this) {
        is CheckStatus.Available -> release.versionCode.toString()
        is CheckStatus.Failed -> detail
        else -> null
    }

object StatusCodec {
    fun encode(kind: StatusKind, arg: String?): String = if (arg == null) kind.name else "${kind.name}|$arg"

    fun decode(value: String?): Pair<StatusKind, String?>? {
        if (value.isNullOrEmpty()) return null
        val sep = value.indexOf('|')
        val name = if (sep < 0) value else value.substring(0, sep)
        val kind = StatusKind.entries.firstOrNull { it.name == name } ?: return null
        return kind to (if (sep < 0) null else value.substring(sep + 1))
    }
}

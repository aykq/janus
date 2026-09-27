/*
 * Copyright © 2026 AykQ. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.amnezia.awg.update

enum class UpdateChannel { STABLE, CANDIDATE }

data class UpdateRelease(
    val versionCode: Long,
    val sha256: String,
    val channel: UpdateChannel,
    val pr: Int?,
    val title: String?,
    val prUrl: String?,
    val assetName: String,
    val assetUrl: String,
)

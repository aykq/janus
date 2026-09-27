/*
 * Copyright © 2026 AykQ. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.amnezia.awg.update

object UpdatePolicy {
    fun newest(releases: List<UpdateRelease>, installedVersionCode: Long, includeCandidates: Boolean): UpdateRelease? =
        releases.asSequence()
            .filter { includeCandidates || it.channel == UpdateChannel.STABLE }
            .filter { it.versionCode > installedVersionCode }
            .sortedWith(compareByDescending<UpdateRelease> { it.versionCode }.thenBy { it.channel != UpdateChannel.STABLE })
            .firstOrNull()

    fun shouldNotify(release: UpdateRelease, lastNotifiedVersionCode: Long) =
        release.versionCode > lastNotifiedVersionCode

    fun normalizeToken(raw: String): String? =
        raw.filterNot { it.isWhitespace() }.takeIf { it.isNotEmpty() }
}

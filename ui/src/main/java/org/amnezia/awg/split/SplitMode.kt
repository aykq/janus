/*
 * Copyright © 2026 AykQ. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.amnezia.awg.split

enum class SplitMode(val key: String) {
    EXCLUDE("exclude"),
    INCLUDE("include");

    companion object {
        fun of(raw: String?) = if (raw == INCLUDE.key) INCLUDE else EXCLUDE
    }
}

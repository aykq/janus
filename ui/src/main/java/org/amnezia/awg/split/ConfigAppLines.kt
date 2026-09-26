/*
 * Copyright © 2026 AykQ. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.amnezia.awg.split

object ConfigAppLines {
    data class Result(val text: String, val excluded: Set<String>, val included: Set<String>) {
        val hasApps get() = excluded.isNotEmpty() || included.isNotEmpty()
    }

    fun strip(text: String): Result {
        val excluded = LinkedHashSet<String>()
        val included = LinkedHashSet<String>()
        var inInterface = false
        val kept = text.split('\n').filter { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("[")) {
                inInterface = trimmed.equals("[Interface]", ignoreCase = true)
                return@filter true
            }
            val key = trimmed.substringBefore('=').trim().lowercase()
            if (!inInterface || !trimmed.contains('=')) return@filter true
            val target = when (key) {
                "excludedapplications" -> excluded
                "includedapplications" -> included
                else -> return@filter true
            }
            trimmed.substringAfter('=').split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { target += it }
            false
        }
        if (excluded.isEmpty() && included.isEmpty()) return Result(text, excluded, included)
        return Result(kept.joinToString("\n"), excluded, included)
    }
}

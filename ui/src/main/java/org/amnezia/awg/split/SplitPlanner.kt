/*
 * Copyright © 2026 AykQ. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.amnezia.awg.split

import org.amnezia.awg.backend.SplitPlan

object SplitPlanner {
    data class Input(
        val enabled: Boolean,
        val appsMode: SplitMode,
        val apps: Set<String>,
        val sitesMode: SplitMode,
        val siteNetworks: List<String>,
        val resolved: Map<String, List<String>>,
        val bypassIps: List<String>,
        val dnsServers: List<String>,
        val ownPackage: String,
        val isInstalled: (String) -> Boolean,
    )

    fun plan(input: Input): SplitPlan {
        val apps = if (input.enabled)
            input.apps.filter { it != input.ownPackage && input.isInstalled(it) }.toSet()
        else emptySet()
        val siteRoutes = if (input.enabled)
            (input.siteNetworks + input.resolved.values.flatten().map(::hostRoute)).distinct()
        else emptyList()
        val sitesInclude = input.enabled && input.sitesMode == SplitMode.INCLUDE

        val excludedRoutes = ((if (sitesInclude) emptyList() else siteRoutes) + input.bypassIps.map(::hostRoute)).distinct()
        val includedRoutes = if (sitesInclude && siteRoutes.isNotEmpty())
            (siteRoutes + input.dnsServers.map(::hostRoute)).distinct()
        else null

        return SplitPlan(
            if (input.appsMode == SplitMode.EXCLUDE) apps else emptySet(),
            if (input.appsMode == SplitMode.INCLUDE) apps else emptySet(),
            excludedRoutes,
            includedRoutes,
        )
    }

    fun hostRoute(ip: String) = if (ip.contains(':')) "$ip/128" else "$ip/32"
}

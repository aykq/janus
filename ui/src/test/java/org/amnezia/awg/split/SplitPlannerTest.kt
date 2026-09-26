package org.amnezia.awg.split

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitPlannerTest {
    private fun input(
        enabled: Boolean = true,
        appsMode: SplitMode = SplitMode.EXCLUDE,
        apps: Set<String> = emptySet(),
        sitesMode: SplitMode = SplitMode.EXCLUDE,
        siteNetworks: List<String> = emptyList(),
        resolved: Map<String, List<String>> = emptyMap(),
        bypassIps: List<String> = emptyList(),
        dnsServers: List<String> = listOf("10.2.0.1"),
        privateDnsIps: List<String> = emptyList(),
        installed: Set<String> = apps,
    ) = SplitPlanner.Input(
        enabled = enabled,
        appsMode = appsMode,
        apps = apps,
        sitesMode = sitesMode,
        siteNetworks = siteNetworks,
        resolved = resolved,
        bypassIps = bypassIps,
        dnsServers = dnsServers,
        privateDnsIps = privateDnsIps,
        ownPackage = "tr.aykq.janus",
        isInstalled = { it in installed },
    )

    @Test
    fun excludeModeExcludesAppsAndSiteIps() {
        val plan = SplitPlanner.plan(input(
            apps = setOf("com.brave.browser"),
            siteNetworks = listOf("1.2.3.0/24"),
            resolved = mapOf("ifconfig.me" to listOf("34.160.111.145", "2600:1901::1")),
        ))
        assertEquals(setOf("com.brave.browser"), plan.excludedApps)
        assertTrue(plan.includedApps.isEmpty())
        assertEquals(listOf("1.2.3.0/24", "34.160.111.145/32", "2600:1901::1/128"), plan.excludedRoutes)
        assertNull(plan.includedRoutes)
    }

    @Test
    fun includeAppModeWithEmptyListIncludesNothing() {
        val plan = SplitPlanner.plan(input(appsMode = SplitMode.INCLUDE))
        assertTrue(plan.includedApps.isEmpty())
        assertTrue(plan.excludedApps.isEmpty())
    }

    @Test
    fun includeAppModeIncludesOnlyListedApps() {
        val plan = SplitPlanner.plan(input(appsMode = SplitMode.INCLUDE, apps = setOf("org.mozilla.firefox")))
        assertEquals(setOf("org.mozilla.firefox"), plan.includedApps)
        assertTrue(plan.excludedApps.isEmpty())
    }

    @Test
    fun uninstalledAndOwnPackagesAreDropped() {
        val plan = SplitPlanner.plan(input(
            apps = setOf("gone.app", "tr.aykq.janus", "com.brave.browser"),
            installed = setOf("tr.aykq.janus", "com.brave.browser"),
        ))
        assertEquals(setOf("com.brave.browser"), plan.excludedApps)
    }

    @Test
    fun includeSiteModeRoutesSitesAndDnsOnly() {
        val plan = SplitPlanner.plan(input(
            sitesMode = SplitMode.INCLUDE,
            resolved = mapOf("ipinfo.io" to listOf("34.117.59.81")),
        ))
        assertEquals(listOf("34.117.59.81/32", "10.2.0.1/32"), plan.includedRoutes)
    }

    @Test
    fun includeSiteModeRoutesPrivateDnsThroughTunnel() {
        val plan = SplitPlanner.plan(input(
            sitesMode = SplitMode.INCLUDE,
            resolved = mapOf("ipinfo.io" to listOf("34.117.59.81")),
            privateDnsIps = listOf("76.76.2.11"),
        ))
        assertEquals(listOf("34.117.59.81/32", "10.2.0.1/32", "76.76.2.11/32"), plan.includedRoutes)
    }

    @Test
    fun excludeSiteModeIgnoresPrivateDnsIps() {
        val plan = SplitPlanner.plan(input(
            resolved = mapOf("ifconfig.me" to listOf("34.160.111.145")),
            privateDnsIps = listOf("76.76.2.11"),
        ))
        assertEquals(listOf("34.160.111.145/32"), plan.excludedRoutes)
        assertNull(plan.includedRoutes)
    }

    @Test
    fun includeSiteModeFallsBackToFullTunnelWhenNothingResolves() {
        val plan = SplitPlanner.plan(input(
            sitesMode = SplitMode.INCLUDE,
            resolved = mapOf("ipinfo.io" to emptyList()),
        ))
        assertNull(plan.includedRoutes)
    }

    @Test
    fun disabledKeepsOnlyPrivateDnsBypass() {
        val plan = SplitPlanner.plan(input(
            enabled = false,
            apps = setOf("com.brave.browser"),
            resolved = mapOf("ifconfig.me" to listOf("34.160.111.145")),
            bypassIps = listOf("76.76.2.11"),
        ))
        assertTrue(plan.excludedApps.isEmpty())
        assertEquals(listOf("76.76.2.11/32"), plan.excludedRoutes)
        assertNull(plan.includedRoutes)
    }

    @Test
    fun mergeLetsConfigIncludeWinOverPlanExclude() {
        val plan = SplitPlanner.plan(input(apps = setOf("com.brave.browser")))
            .merge(emptySet(), setOf("org.mozilla.firefox"))
        assertEquals(setOf("org.mozilla.firefox"), plan.includedApps)
        assertTrue(plan.excludedApps.isEmpty())
    }
}

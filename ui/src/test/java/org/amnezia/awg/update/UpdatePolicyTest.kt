package org.amnezia.awg.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePolicyTest {
    private fun r(code: Long, channel: UpdateChannel = UpdateChannel.STABLE) = UpdateRelease(
        versionCode = code, sha256 = "a".repeat(64), channel = channel,
        pr = if (channel == UpdateChannel.CANDIDATE) 1 else null, title = null, prUrl = null,
        assetName = "janus-$code.apk", assetUrl = "https://api.github.com/repos/aykq/janus/releases/assets/$code",
    )

    @Test
    fun picksHighestAboveInstalled() {
        val result = UpdatePolicy.newest(listOf(r(40), r(45, UpdateChannel.CANDIDATE), r(43)), 41, includeCandidates = true)
        assertEquals(45L, result?.versionCode)
    }

    @Test
    fun candidatesExcludedWhenDisabled() {
        val result = UpdatePolicy.newest(listOf(r(43), r(45, UpdateChannel.CANDIDATE)), 41, includeCandidates = false)
        assertEquals(43L, result?.versionCode)
    }

    @Test
    fun tiePrefersStable() {
        val result = UpdatePolicy.newest(listOf(r(50, UpdateChannel.CANDIDATE), r(50)), 41, includeCandidates = true)
        assertEquals(UpdateChannel.STABLE, result?.channel)
    }

    @Test
    fun noneWhenInstalledIsNewest() {
        assertNull(UpdatePolicy.newest(listOf(r(40), r(45, UpdateChannel.CANDIDATE)), 45, includeCandidates = true))
    }

    @Test
    fun notifiesOncePerVersion() {
        assertTrue(UpdatePolicy.shouldNotify(r(45), 44))
        assertFalse(UpdatePolicy.shouldNotify(r(45), 45))
    }
}

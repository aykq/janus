package org.amnezia.awg.split

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ConfigAppLinesTest {
    @Test
    fun stripsExcludedApplications() {
        val text = "[Interface]\nAddress = 10.2.0.2/32\nExcludedApplications = com.brave.browser, org.a\nJc = 4\n\n[Peer]\nAllowedIPs = 0.0.0.0/0\n"
        val result = ConfigAppLines.strip(text)
        assertEquals(setOf("com.brave.browser", "org.a"), result.excluded)
        assertEquals("[Interface]\nAddress = 10.2.0.2/32\nJc = 4\n\n[Peer]\nAllowedIPs = 0.0.0.0/0\n", result.text)
    }

    @Test
    fun stripsIncludedApplicationsCaseInsensitive() {
        val result = ConfigAppLines.strip("[Interface]\nincludedapplications=org.mozilla.firefox\n")
        assertEquals(setOf("org.mozilla.firefox"), result.included)
        assertEquals("[Interface]\n", result.text)
    }

    @Test
    fun leavesConfigWithoutAppsUntouched() {
        val text = "[Interface]\nAddress = 10.2.0.2/32\n"
        val result = ConfigAppLines.strip(text)
        assertFalse(result.hasApps)
        assertEquals(text, result.text)
    }

    @Test
    fun ignoresSameKeyOutsideInterface() {
        val text = "[Peer]\nExcludedApplications = x\n"
        assertFalse(ConfigAppLines.strip(text).hasApps)
    }
}

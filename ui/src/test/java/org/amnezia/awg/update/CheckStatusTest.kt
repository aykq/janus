package org.amnezia.awg.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CheckStatusTest {
    @Test
    fun classifiesHttpErrors() {
        assertEquals(CheckStatus.RateLimited, CheckStatus.fromHttp(403, "0"))
        assertEquals(CheckStatus.RateLimited, CheckStatus.fromHttp(429, "0"))
        assertEquals(CheckStatus.NotReachable, CheckStatus.fromHttp(403, "12"))
        assertEquals(CheckStatus.NotReachable, CheckStatus.fromHttp(404, null))
        assertEquals(CheckStatus.Failed("HTTP 401"), CheckStatus.fromHttp(401, null))
        assertEquals(CheckStatus.Failed("HTTP 500"), CheckStatus.fromHttp(500, null))
    }

    @Test
    fun kindAndArg() {
        assertEquals(StatusKind.NOT_REACHABLE, CheckStatus.NotReachable.kind)
        assertEquals("HTTP 502", CheckStatus.Failed("HTTP 502").arg)
        assertNull(CheckStatus.UpToDate.arg)
    }

    @Test
    fun codecRoundTrips() {
        assertEquals(StatusKind.UP_TO_DATE to null, StatusCodec.decode(StatusCodec.encode(StatusKind.UP_TO_DATE, null)))
        assertEquals(StatusKind.AVAILABLE to "142", StatusCodec.decode(StatusCodec.encode(StatusKind.AVAILABLE, "142")))
        assertEquals(StatusKind.FAILED to "a|b: c", StatusCodec.decode(StatusCodec.encode(StatusKind.FAILED, "a|b: c")))
    }

    @Test
    fun codecRejectsGarbage() {
        assertNull(StatusCodec.decode(null))
        assertNull(StatusCodec.decode("NOPE"))
    }

    @Test
    fun ignoresStatusesStoredByOlderVersions() {
        assertNull(StatusCodec.decode("TOKEN_REJECTED"))
        assertNull(StatusCodec.decode("NOT_REACHABLE_WITH_TOKEN"))
    }
}

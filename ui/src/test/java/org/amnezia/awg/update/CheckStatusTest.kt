package org.amnezia.awg.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CheckStatusTest {
    @Test
    fun classifiesHttpErrors() {
        assertEquals(CheckStatus.TokenRejected, CheckStatus.fromHttp(401, null, hadToken = true))
        assertEquals(CheckStatus.RateLimited, CheckStatus.fromHttp(403, "0", hadToken = false))
        assertEquals(CheckStatus.RateLimited, CheckStatus.fromHttp(429, "0", hadToken = true))
        assertEquals(CheckStatus.TokenRejected, CheckStatus.fromHttp(403, "12", hadToken = true))
        assertEquals(CheckStatus.NotReachable(false), CheckStatus.fromHttp(403, "12", hadToken = false))
        assertEquals(CheckStatus.NotReachable(false), CheckStatus.fromHttp(404, null, hadToken = false))
        assertEquals(CheckStatus.NotReachable(true), CheckStatus.fromHttp(404, null, hadToken = true))
        assertEquals(CheckStatus.Failed("HTTP 500"), CheckStatus.fromHttp(500, null, hadToken = false))
    }

    @Test
    fun kindAndArg() {
        assertEquals(StatusKind.NOT_REACHABLE_WITH_TOKEN, CheckStatus.NotReachable(true).kind)
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
}

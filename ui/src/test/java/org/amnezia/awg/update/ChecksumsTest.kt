package org.amnezia.awg.update

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class ChecksumsTest {
    @Test
    fun sha256OfKnownContent() {
        val file = File.createTempFile("janus", ".bin")
        try {
            file.writeText("abc")
            assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Checksums.sha256(file))
        } finally {
            file.delete()
        }
    }
}

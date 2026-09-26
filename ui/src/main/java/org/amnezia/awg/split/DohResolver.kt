/*
 * Copyright © 2026 AykQ. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.amnezia.awg.split

import android.net.TrafficStats
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.IDN
import java.net.InetAddress
import java.net.URL

/**
 * Minimal RFC 8484 DNS-over-HTTPS client (POST, application/dns-message).
 * Only A and AAAA answers are extracted; CNAME chains are followed by the resolver itself.
 */
object DohResolver {
    private const val TYPE_A = 1
    private const val TYPE_AAAA = 28
    private const val TIMEOUT_MS = 3000
    private const val TRAFFIC_TAG = 0x4d4b // "MK"

    fun resolve(url: String, host: String): List<InetAddress> {
        val name = IDN.toASCII(host.trimEnd('.'))
        val result = ArrayList<InetAddress>()
        var lastError: IOException? = null
        for (type in intArrayOf(TYPE_A, TYPE_AAAA)) {
            try {
                result += query(url, name, type)
            } catch (e: IOException) {
                lastError = e
            }
        }
        if (result.isEmpty() && lastError != null)
            throw lastError
        return result
    }

    private fun query(url: String, name: String, type: Int): List<InetAddress> {
        val body = buildQuery(name, type)
        TrafficStats.setThreadStatsTag(TRAFFIC_TAG) // untagged sockets trip StrictMode in debug
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/dns-message")
            conn.setRequestProperty("Accept", "application/dns-message")
            conn.outputStream.use { it.write(body) }
            if (conn.responseCode != HttpURLConnection.HTTP_OK)
                throw IOException("DoH HTTP ${conn.responseCode}")
            val response = conn.inputStream.use { it.readBytes() }
            return parseAnswers(response, type)
        } finally {
            conn.disconnect()
            TrafficStats.clearThreadStatsTag()
        }
    }

    private fun buildQuery(name: String, type: Int): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeShort(0) // ID 0, as RFC 8484 recommends for cacheability
            out.writeShort(0x0100) // RD
            out.writeShort(1) // QDCOUNT
            out.writeShort(0)
            out.writeShort(0)
            out.writeShort(0)
            for (label in name.split('.')) {
                if (label.isEmpty()) continue
                val raw = label.toByteArray(Charsets.US_ASCII)
                if (raw.size > 63) throw IOException("Label too long: $label")
                out.writeByte(raw.size)
                out.write(raw)
            }
            out.writeByte(0)
            out.writeShort(type)
            out.writeShort(1) // IN
        }
        return bytes.toByteArray()
    }

    private fun parseAnswers(msg: ByteArray, wantedType: Int): List<InetAddress> {
        if (msg.size < 12) throw IOException("DNS response too short")
        val rcode = u16(msg, 2) and 0xF
        if (rcode != 0) return emptyList() // NXDOMAIN etc.
        val qdCount = u16(msg, 4)
        val anCount = u16(msg, 6)
        var pos = 12
        repeat(qdCount) { pos = skipName(msg, pos) + 4 }
        val result = ArrayList<InetAddress>()
        repeat(anCount) {
            pos = skipName(msg, pos)
            if (pos + 10 > msg.size) throw IOException("Truncated answer")
            val type = u16(msg, pos)
            val rdLength = u16(msg, pos + 8)
            pos += 10
            if (pos + rdLength > msg.size) throw IOException("Truncated rdata")
            val expectedLength = if (wantedType == TYPE_A) 4 else 16
            if (type == wantedType && rdLength == expectedLength)
                result += InetAddress.getByAddress(msg.copyOfRange(pos, pos + rdLength))
            pos += rdLength
        }
        return result
    }

    private fun skipName(msg: ByteArray, start: Int): Int {
        var pos = start
        while (true) {
            if (pos >= msg.size) throw IOException("Truncated name")
            val len = msg[pos].toInt() and 0xFF
            when {
                len == 0 -> return pos + 1
                len and 0xC0 == 0xC0 -> return pos + 2
                else -> pos += len + 1
            }
        }
    }

    private fun u16(msg: ByteArray, pos: Int) =
        ((msg[pos].toInt() and 0xFF) shl 8) or (msg[pos + 1].toInt() and 0xFF)
}

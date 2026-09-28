/*
 * Copyright © 2026 AykQ. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.amnezia.awg.update

import android.net.TrafficStats
import org.amnezia.awg.BuildConfig
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object GitHubClient {
    class HttpError(val code: Int, val rateLimitRemaining: String?) : IOException("HTTP $code")

    const val REPO = "aykq/janus"
    private const val API_PREFIX = "https://api.github.com/repos/$REPO/"
    private const val RELEASES_URL = "${API_PREFIX}releases?per_page=30"
    private const val TIMEOUT_MS = 15_000
    private const val TRAFFIC_TAG = 0x4a55

    fun fetchReleases(): String = tagged {
        val conn = open(RELEASES_URL, "application/vnd.github+json", followRedirects = true)
        try {
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw HttpError(code, conn.getHeaderField("X-RateLimit-Remaining"))
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    fun download(assetUrl: String, dest: File, onProgress: (Long, Long) -> Unit) = tagged {
        require(assetUrl.startsWith(API_PREFIX)) { "Unexpected asset URL" }
        var conn = open(assetUrl, "application/octet-stream", followRedirects = false)
        try {
            var code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location")
                if (location == null || !location.startsWith("https://")) throw IOException("Bad redirect")
                conn.disconnect()
                conn = open(location, "application/octet-stream", followRedirects = true)
                code = conn.responseCode
            }
            if (code != HttpURLConnection.HTTP_OK) throw HttpError(code, conn.getHeaderField("X-RateLimit-Remaining"))
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                dest.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        onProgress(done, total)
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun open(url: String, accept: String, followRedirects: Boolean): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = TIMEOUT_MS
        conn.readTimeout = TIMEOUT_MS
        conn.instanceFollowRedirects = followRedirects
        conn.setRequestProperty("Accept", accept)
        conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        conn.setRequestProperty("User-Agent", "Janus/${BuildConfig.VERSION_NAME}")
        return conn
    }

    private inline fun <T> tagged(block: () -> T): T {
        TrafficStats.setThreadStatsTag(TRAFFIC_TAG)
        try {
            return block()
        } finally {
            TrafficStats.clearThreadStatsTag()
        }
    }
}

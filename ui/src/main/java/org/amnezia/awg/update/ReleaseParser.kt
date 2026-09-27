/*
 * Copyright © 2026 AykQ. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.amnezia.awg.update

import org.json.JSONArray
import org.json.JSONException

object ReleaseParser {
    private val BLOCK = Regex("<!--\\s*janus-update\\s*(.*?)-->", RegexOption.DOT_MATCHES_ALL)
    private val SHA256 = Regex("^[0-9a-f]{64}$")

    fun parse(json: String): List<UpdateRelease> {
        val array = try {
            JSONArray(json)
        } catch (e: JSONException) {
            throw IllegalArgumentException("Malformed releases response", e)
        }
        val result = mutableListOf<UpdateRelease>()
        for (i in 0 until array.length()) {
            val release = array.optJSONObject(i) ?: continue
            if (release.optBoolean("draft")) continue
            parseRelease(release.optString("body"), release.optJSONArray("assets"))?.let(result::add)
        }
        return result
    }

    private fun fields(body: String): Map<String, String>? {
        val block = BLOCK.find(body)?.groupValues?.get(1) ?: return null
        return block.lineSequence()
            .map { it.trim() }
            .mapNotNull { line ->
                val colon = line.indexOf(':')
                if (colon <= 0) null else line.substring(0, colon).trim() to line.substring(colon + 1).trim()
            }
            .toMap()
    }

    private fun parseRelease(body: String, assets: JSONArray?): UpdateRelease? {
        val f = fields(body) ?: return null
        val versionCode = f["versionCode"]?.toLongOrNull()?.takeIf { it > 0 } ?: return null
        val sha256 = f["sha256"]?.lowercase()?.takeIf { SHA256.matches(it) } ?: return null
        val channel = when (f["channel"]) {
            "stable" -> UpdateChannel.STABLE
            "candidate" -> UpdateChannel.CANDIDATE
            else -> return null
        }
        val pr = f["pr"]?.toIntOrNull()
        if (channel == UpdateChannel.CANDIDATE && pr == null) return null
        val prefix = "janus-$versionCode-"
        var assetName: String? = null
        var assetUrl: String? = null
        if (assets != null) {
            for (j in 0 until assets.length()) {
                val asset = assets.optJSONObject(j) ?: continue
                val name = asset.optString("name")
                if (name.startsWith(prefix) && name.endsWith(".apk")) {
                    assetName = name
                    assetUrl = asset.optString("url").takeIf { it.isNotEmpty() }
                    break
                }
            }
        }
        if (assetName == null || assetUrl == null) return null
        return UpdateRelease(
            versionCode = versionCode,
            sha256 = sha256,
            channel = channel,
            pr = pr,
            title = f["title"]?.takeIf { it.isNotEmpty() },
            prUrl = f["url"]?.takeIf { it.startsWith("https://github.com/") },
            assetName = assetName,
            assetUrl = assetUrl,
        )
    }
}

package org.amnezia.awg.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseParserTest {
    private val sha = "a".repeat(64)

    private fun block(vararg lines: String) =
        "Notes\n\n<!-- janus-update\n" + lines.joinToString("\n") + "\n-->"

    private fun release(body: String, assets: List<String>, draft: Boolean = false): String {
        val assetJson = assets.joinToString(",") {
            """{"name":"$it","url":"https://api.github.com/repos/aykq/janus/releases/assets/${it.hashCode()}"}"""
        }
        val escaped = body.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        return """{"draft":$draft,"body":"$escaped","assets":[$assetJson]}"""
    }

    private fun list(vararg releases: String) = "[" + releases.joinToString(",") + "]"

    private fun stable(code: Int, s: String = sha) =
        release(block("versionCode: $code", "sha256: $s", "channel: stable"), listOf("janus-$code-stable.apk"))

    @Test
    fun parsesStableAndCandidate() {
        val json = list(
            stable(40),
            release(
                block("versionCode: 42", "sha256: $sha", "channel: candidate", "pr: 12",
                    "title: Bump amneziawg-go", "url: https://github.com/aykq/janus/pull/12"),
                listOf("janus-42-pr12.apk"),
            ),
        )
        val result = ReleaseParser.parse(json)
        assertEquals(2, result.size)
        val s = result[0]
        assertEquals(40L, s.versionCode)
        assertEquals(UpdateChannel.STABLE, s.channel)
        assertEquals("janus-40-stable.apk", s.assetName)
        assertTrue(s.assetUrl.startsWith("https://api.github.com/repos/aykq/janus/releases/assets/"))
        val c = result[1]
        assertEquals(UpdateChannel.CANDIDATE, c.channel)
        assertEquals(12, c.pr)
        assertEquals("Bump amneziawg-go", c.title)
        assertEquals("https://github.com/aykq/janus/pull/12", c.prUrl)
    }

    @Test
    fun skipsReleaseWithoutBlockOrDraft() {
        val json = list(
            release("Just notes", listOf("janus-1-stable.apk")),
            release(block("versionCode: 2", "sha256: $sha", "channel: stable"), listOf("janus-2-stable.apk"), draft = true),
        )
        assertTrue(ReleaseParser.parse(json).isEmpty())
    }

    @Test
    fun skipsReleaseWithMissingOrForeignAsset() {
        val json = list(
            release(block("versionCode: 5", "sha256: $sha", "channel: stable"), emptyList()),
            release(block("versionCode: 6", "sha256: $sha", "channel: stable"), listOf("janus-7-stable.apk")),
            release(block("versionCode: 8", "sha256: $sha", "channel: stable"), listOf("janus-8-stable.txt")),
        )
        assertTrue(ReleaseParser.parse(json).isEmpty())
    }

    @Test
    fun skipsInvalidFields() {
        val json = list(
            release(block("versionCode: x", "sha256: $sha", "channel: stable"), listOf("janus-x-stable.apk")),
            release(block("versionCode: 3", "sha256: abc", "channel: stable"), listOf("janus-3-stable.apk")),
            release(block("versionCode: 4", "sha256: $sha", "channel: beta"), listOf("janus-4-stable.apk")),
            release(block("versionCode: 9", "sha256: $sha", "channel: candidate"), listOf("janus-9-pr1.apk")),
        )
        assertTrue(ReleaseParser.parse(json).isEmpty())
    }

    @Test
    fun uppercaseShaIsNormalized() {
        val upper = "ABCDEF0123".repeat(6) + "ABCD"
        val result = ReleaseParser.parse(list(stable(10, upper)))
        assertEquals(upper.lowercase(), result.single().sha256)
    }

    @Test
    fun titleWithColonKeepsRest() {
        val json = list(release(
            block("versionCode: 11", "sha256: $sha", "channel: candidate", "pr: 3",
                "title: deps: bump x from 1 to 2", "url: https://github.com/aykq/janus/pull/3"),
            listOf("janus-11-pr3.apk"),
        ))
        assertEquals("deps: bump x from 1 to 2", ReleaseParser.parse(json).single().title)
    }

    @Test
    fun nonGithubUrlIsDropped() {
        val json = list(release(
            block("versionCode: 12", "sha256: $sha", "channel: candidate", "pr: 4", "url: https://evil.example/pr/4"),
            listOf("janus-12-pr4.apk"),
        ))
        assertNull(ReleaseParser.parse(json).single().prUrl)
    }

    @Test(expected = IllegalArgumentException::class)
    fun malformedJsonThrows() {
        ReleaseParser.parse("{not json")
    }
}

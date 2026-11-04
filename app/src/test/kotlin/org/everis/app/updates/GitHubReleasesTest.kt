package org.everis.app.updates

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GitHubReleasesTest {
    private val repository = "Rembos1k/Everis"
    private fun release(tag: String = "v0.2-beta", debug: Boolean = false, abi: String = "arm64-v8a"): JSONObject {
        val name = "Everis-${tag.removePrefix("v")}-$abi-${if (debug) "debug" else "release"}.apk"
        return JSONObject().put("tag_name", tag).put("draft", false).put("prerelease", "-" in tag)
            .put("body", "Fixed schedule\n\nImproved animations")
            .put("assets", JSONArray().put(JSONObject().put("name", name).put("state", "uploaded").put("size", 100)
                .put("browser_download_url", "https://github.com/$repository/releases/download/$tag/$name")))
    }
    private fun update(vararg releases: JSONObject, installed: String = "0.1-beta", debug: Boolean = false) =
        findAppUpdate(JSONArray(releases.toList()).toString(), repository, installed, debug, "arm64-v8a")

    @Test fun betaIsNewerThanPreButOlderThanStable() {
        val stages = listOf("0.1-prototype", "0.1-pre", "0.1-alpha", "0.1-beta", "0.1-rc", "0.1")
        stages.zipWithNext().forEach { (a, b) -> assertTrue("$a < $b", AppVersion.parse(a)!! < AppVersion.parse(b)!!) }
    }
    @Test fun missingPatchAndBuildMetadataDoNotChangeOrder() {
        assertEquals(0, AppVersion.parse("v0.1.0+build.6")!!.compareTo(AppVersion.parse("0.1")!!))
    }
    @Test fun numbersAreComparedNumerically() { assertTrue(AppVersion.parse("0.10-beta")!! > AppVersion.parse("0.9.99")!!) }
    @Test fun prereleaseIterationsAreNumeric() { assertTrue(AppVersion.parse("1.0-beta.12")!! > AppVersion.parse("1.0-beta2")!!) }
    @Test fun invalidVersionsAreRejected() {
        listOf("", "latest", "0", "1.0-nightly", "1.0-beta-evil", "21474836470.0", "1.2.3.4.5", "1.0-rc-1-2").forEach { assertNull(it, AppVersion.parse(it)) }
    }
    @Test fun newerBetaProvidesNotesAndArm64Release() {
        val actual = update(release())!!
        assertEquals("0.2-beta", actual.version)
        assertTrue(actual.notes.contains("Improved animations"))
        assertTrue(actual.downloadUrl.endsWith("arm64-v8a-release.apk"))
    }
    @Test fun sameAndOlderVersionsAreIgnored() {
        assertNull(update(release("v0.1-beta"), release("v0.1-pre"), release("v0.0.3-prototype")))
    }
    @Test fun newestVersionWinsRegardlessOfReleaseDateOrder() {
        assertEquals("0.12-beta", update(release("v0.2"), release("v0.12-beta"), release("v0.10"))!!.version)
    }
    @Test fun draftsAndUnrecognizedTagsAreIgnored() {
        assertNull(update(release().put("draft", true), release("nightly")))
    }
    @Test fun stableInstallsDoNotReceivePrereleases() { assertNull(update(release("v0.2-beta"), installed = "0.1")) }
    @Test fun betaInstallsCanUpgradeToStable() { assertEquals("0.1", update(release("v0.1"))!!.version) }
    @Test fun releaseDoesNotOfferDebugOrX86() { assertNull(update(release(debug = true), release(abi = "x86_64"))) }
    @Test fun debugDoesNotOfferReleaseWithDifferentPackageIdentity() {
        assertNull(update(release(), debug = true))
        assertNotNull(update(release(debug = true), debug = true))
    }
    @Test fun unpublishedAndEmptyAssetsAreIgnored() {
        val pending = release().also { it.getJSONArray("assets").getJSONObject(0).put("state", "new") }
        assertNull(update(pending, release().put("assets", JSONArray())))
    }
    @Test fun invalidAssetSizeIsIgnored() {
        val empty = release().also { it.getJSONArray("assets").getJSONObject(0).put("size", 0) }
        assertNull(update(empty))
    }
    @Test fun downloadMustBelongToThisRepository() {
        val wrong = release().also { it.getJSONArray("assets").getJSONObject(0).put("browser_download_url", "https://github.com/other/Everis/releases/download/v1/app.apk") }
        assertNull(update(wrong))
    }
    @Test fun untrustedUrlsAreRejected() {
        listOf("http://github.com/$repository/releases/download/v1/a.apk", "https://github.com.evil.test/$repository/releases/download/v1/a.apk",
            "https://evil@github.com/$repository/releases/download/v1/a.apk", "https://github.com/$repository/releases/download/../a.apk",
            "https://github.com/$repository/releases/download/v1/a.apk?token=secret", "file:///a.apk").forEach { assertFalse(it, trustedDownload(it, repository)) }
    }
    @Test fun releaseBodyIsBoundedAndDoesNotRenderCode() {
        val result = update(release().put("body", "a".repeat(40000)))!!
        assertEquals(32000, result.notes.length)
    }
    @Test fun emptyReleaseListIsCurrent() { assertNull(update()) }
    @Test fun malformedJsonFailsClosed() {
        try { findAppUpdate("{}", repository, "0.1-beta", false, "arm64-v8a"); fail() } catch (_: org.json.JSONException) { }
    }
    @Test fun automaticIntervalIsThreeDaysAndHandlesClockChanges() {
        val last = 1_000_000L
        assertTrue(automaticCheckDue(last, 0))
        assertFalse(automaticCheckDue(last + UPDATE_INTERVAL_MILLIS - 1, last))
        assertTrue(automaticCheckDue(last + UPDATE_INTERVAL_MILLIS, last))
        assertTrue(automaticCheckDue(last - 1, last))
    }
    @Test fun repositoryCannotInjectQueryOrAnotherHost() {
        listOf("../Everis", "owner/repo?x=1", "https://github.com/Rembos1k/Everis", "owner/repo/extra").forEach { assertFalse(validRepository(it)) }
        assertTrue(validRepository(repository))
    }
}

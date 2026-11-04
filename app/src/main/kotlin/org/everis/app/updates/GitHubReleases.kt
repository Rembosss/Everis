package org.everis.app.updates

import org.everis.app.data.validateJsonStructure
import org.json.JSONArray
import java.net.URI

data class AppVersion(val numbers: List<Int>, val stage: Int, val iteration: Int) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int {
        repeat(maxOf(numbers.size, other.numbers.size)) { index ->
            val order = (numbers.getOrElse(index) { 0 }).compareTo(other.numbers.getOrElse(index) { 0 })
            if (order != 0) return order
        }
        val order = stage.compareTo(other.stage)
        return if (order != 0) order else iteration.compareTo(other.iteration)
    }
    companion object {
        fun parse(tag: String): AppVersion? {
            if (tag.length !in 3..96) return null
            val match = Regex("""^v?(\d{1,9}(?:\.\d{1,9}){1,3})(?:-(prototype|pre|alpha|beta|rc)(?:[.-]?(\d{1,6}))?)?(?:\+[A-Za-z0-9.-]+)?$""")
                .matchEntire(tag) ?: return null
            val numbers = match.groupValues[1].split('.').map { it.toIntOrNull() ?: return null }
            val stage = listOf("prototype", "pre", "alpha", "beta", "rc", "").indexOf(match.groupValues[2])
            return AppVersion(numbers, stage, match.groupValues[3].toIntOrNull() ?: 0)
        }
    }
}

data class AppUpdate(val version: String, val notes: String, val downloadUrl: String)

fun validRepository(repository: String): Boolean = Regex("[A-Za-z0-9][A-Za-z0-9-]{0,38}/[A-Za-z0-9][A-Za-z0-9._-]{0,99}").matches(repository)

fun trustedDownload(url: String, repository: String): Boolean = runCatching {
    val uri = URI(url)
    validRepository(repository) && uri.scheme == "https" && uri.host == "github.com" && uri.userInfo == null &&
        uri.fragment == null && uri.query == null && (uri.port == -1 || uri.port == 443) &&
        uri.path.startsWith("/$repository/releases/download/") &&
        uri.path.split('/').none { it == "." || it == ".." } && uri.path.endsWith(".apk")
}.getOrDefault(false)

/** Public releases only. Beta installs can see beta releases; stable installs ignore prereleases. */
fun findAppUpdate(raw: String, repository: String, installed: String, debug: Boolean, abi: String): AppUpdate? {
    require(validRepository(repository) && abi == "arm64-v8a")
    require(raw.length <= 1024 * 1024)
    validateJsonStructure(raw)
    val current = requireNotNull(AppVersion.parse(installed))
    val releases = JSONArray(raw)
    require(releases.length() <= 20)
    val candidates = buildList {
        repeat(releases.length()) { index ->
            val release = releases.getJSONObject(index)
            if (release.optBoolean("draft") || (current.stage == 5 && release.optBoolean("prerelease"))) return@repeat
            val tag = release.optString("tag_name")
            val version = AppVersion.parse(tag) ?: return@repeat
            if (version <= current) return@repeat
            val assets = release.optJSONArray("assets") ?: return@repeat
            if (assets.length() > 100) return@repeat
            val expected = "Everis-${tag.removePrefix("v")}-$abi-${if (debug) "debug" else "release"}.apk"
            for (assetIndex in 0 until assets.length()) {
                val asset = assets.getJSONObject(assetIndex)
                val url = asset.optString("browser_download_url")
                if (asset.optString("name") != expected || asset.optString("state") != "uploaded" ||
                    asset.optLong("size") !in 1..150L * 1024 * 1024 || !trustedDownload(url, repository)) continue
                val notes = release.optString("body").takeUnless { it == "null" }.orEmpty()
                    .replace("\r\n", "\n").filter { it >= ' ' || it == '\n' || it == '\t' }.take(32_000).trim()
                add(version to AppUpdate(tag.removePrefix("v"), notes, url))
                break
            }
        }
    }
    return candidates.maxByOrNull { it.first }?.second
}

const val UPDATE_INTERVAL_MILLIS = 3L * 24 * 60 * 60 * 1000
fun automaticCheckDue(now: Long, last: Long): Boolean = last <= 0 || now < last || now - last >= UPDATE_INTERVAL_MILLIS

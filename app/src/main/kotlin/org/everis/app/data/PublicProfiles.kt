package org.everis.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.text.Html
import org.everis.sources.HttpResult
import org.everis.sources.PublicHttpClient
import org.json.JSONObject
import java.io.File
import java.net.URI

data class PublicProfile(val handle: String, val name: String = "@$handle", val avatar: Bitmap? = null)

/** Only public name/photo, no Telegram account, API key, biography or contact access. */
class PublicProfiles(context: Context) {
    private val directory = File(context.cacheDir, "public-profiles").apply { mkdirs() }
    private val hosts = setOf("t.me") + (1..20).map { "cdn$it.telesco.pe" }
    private val client = PublicHttpClient(hosts, maxBytes = 1024 * 1024, connectTimeoutMillis = 8_000, readTimeoutMillis = 10_000)
    private fun photo(handle: String): File = File(directory, "$handle.jpg")
    private fun meta(handle: String): File = File(directory, "$handle.json")
    fun cached(handle: String): PublicProfile {
        require(handle in setOf("Rembos1k", "Gods_Mask"))
        return runCatching {
            val saved = JSONObject(meta(handle).takeIf { it.length() in 1..4096 }?.readText() ?: "{}")
            PublicProfile(handle, saved.optString("name").takeIf { it.isNotBlank() } ?: "@$handle",
                photo(handle).takeIf { it.length() in 1..1024 * 1024 }?.readBytes()?.let(::decode))
        }.getOrDefault(PublicProfile(handle))
    }
    fun refresh(handle: String): PublicProfile {
        val cached = cached(handle)
        val now = System.currentTimeMillis()
        if (meta(handle).exists() && now - meta(handle).lastModified() in 0..7L * 24 * 60 * 60 * 1000) return cached
        return runCatching {
            val response = client.get(URI("https://t.me/$handle")) as? HttpResult.Body ?: return cached
            val html = response.bytes.toString(Charsets.UTF_8)
            val metadata = Regex("""<meta\b[^>]*>""", RegexOption.IGNORE_CASE).findAll(html).mapNotNull { tag ->
                val attributes = Regex("""([\w:-]+)\s*=\s*["']([^"']*)["']""").findAll(tag.value)
                    .associate { it.groupValues[1].lowercase() to Html.fromHtml(it.groupValues[2], Html.FROM_HTML_MODE_LEGACY).toString() }
                attributes["property"]?.let { it to attributes["content"].orEmpty() }
            }.toMap()
            val name = metadata["og:title"]?.filter { it >= ' ' }?.trim()?.take(100)?.takeIf { it.isNotBlank() } ?: cached.name
            val uri = metadata["og:image"]?.let { runCatching { URI(it) }.getOrNull() }
            var avatar = cached.avatar
            if (uri != null && uri.host in hosts && uri.host != "t.me") {
                val image = client.get(uri) as? HttpResult.Body
                val decoded = image?.takeIf { it.contentType?.startsWith("image/") == true }?.bytes?.let(::decode)
                if (decoded != null) {
                    val output = java.io.ByteArrayOutputStream()
                    decoded.compress(Bitmap.CompressFormat.JPEG, 90, output)
                    android.util.AtomicFile(photo(handle)).let { file ->
                        val stream = file.startWrite()
                        try { stream.write(output.toByteArray()); file.finishWrite(stream) }
                        catch (error: Exception) { file.failWrite(stream); throw error }
                    }
                    avatar = decoded
                }
            }
            if (avatar != null) meta(handle).writeText(JSONObject().put("name", name).toString())
            PublicProfile(handle, name, avatar)
        }.getOrDefault(cached)
    }
    private fun decode(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth !in 1..4096 || bounds.outHeight !in 1..4096) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 512) inSampleSize *= 2
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }
}

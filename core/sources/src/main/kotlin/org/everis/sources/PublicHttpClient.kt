package org.everis.sources

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.util.Locale
import java.util.zip.GZIPInputStream

sealed interface HttpResult {
    data class Body(val bytes: ByteArray, val contentType: String?, val etag: String?, val lastModified: String?, val statusCode: Int = 200) : HttpResult
    data object NotModified : HttpResult
    data class Failure(val kind: Kind, val statusCode: Int? = null) : HttpResult
    enum class Kind { DISALLOWED_URL, TOO_MANY_REDIRECTS, HTTP_ERROR, TOO_LARGE, UNSUPPORTED_ENCODING, NETWORK }
}

class PublicHttpClient(
    allowedHosts: Set<String>,
    private val maxBytes: Int = 8 * 1024 * 1024,
    private val connectTimeoutMillis: Int = 15_000,
    private val readTimeoutMillis: Int = 20_000,
    private val connection: (URI) -> HttpURLConnection = { it.toURL().openConnection() as HttpURLConnection },
) {
    private val hosts = allowedHosts.map { it.lowercase(Locale.ROOT) }.toSet()
    init { require(maxBytes > 0 && connectTimeoutMillis > 0 && readTimeoutMillis > 0 && hosts.isNotEmpty()) }

    private fun allowed(uri: URI): Boolean = uri.scheme.equals("https", ignoreCase = true) &&
        uri.host?.lowercase(Locale.ROOT) in hosts && uri.userInfo == null &&
        uri.fragment == null && (uri.port == -1 || uri.port == 443)

    // Blocking transport: invoke from the IO executor/coroutine, never the UI thread.
    fun get(initial: URI, etag: String? = null, lastModified: String? = null): HttpResult = request(initial, null, etag, lastModified)

    /** Public search APIs may use POST for a read-only filter; no account credentials are sent. */
    fun postJson(initial: URI, json: String): HttpResult {
        val body = json.toByteArray(Charsets.UTF_8)
        if (body.size > 64 * 1024) return HttpResult.Failure(HttpResult.Kind.TOO_LARGE)
        return request(initial, body, null, null)
    }

    fun postForm(initial: URI, fields: Map<String, String>): HttpResult {
        if (fields.size > 32) return HttpResult.Failure(HttpResult.Kind.TOO_LARGE)
        val body = fields.entries.joinToString("&") { (key, value) ->
            URLEncoder.encode(key, "UTF-8") + "=" + URLEncoder.encode(value, "UTF-8")
        }.toByteArray(Charsets.UTF_8)
        if (body.size > 64 * 1024) return HttpResult.Failure(HttpResult.Kind.TOO_LARGE)
        return request(initial, body, null, null, "application/x-www-form-urlencoded; charset=utf-8")
    }

    private fun request(initial: URI, initialBody: ByteArray?, etag: String?, lastModified: String?, contentType: String = "application/json; charset=utf-8"): HttpResult {
        var uri = initial
        var body = initialBody
        repeat(6) { redirect ->
            if (!allowed(uri)) return HttpResult.Failure(HttpResult.Kind.DISALLOWED_URL)
            val client = try { connection(uri) } catch (_: IOException) {
                return HttpResult.Failure(HttpResult.Kind.NETWORK)
            }
            try {
                client.requestMethod = if (body == null) "GET" else "POST"
                client.instanceFollowRedirects = false
                client.connectTimeout = connectTimeoutMillis
                client.readTimeout = readTimeoutMillis
                client.useCaches = false
                client.setRequestProperty("User-Agent", "Everis")
                client.setRequestProperty("Accept-Encoding", "identity")
                fun validator(value: String?): String? = value?.takeIf {
                    it.length <= 4096 && '\r' !in it && '\n' !in it
                }
                validator(etag)?.let { client.setRequestProperty("If-None-Match", it) }
                validator(lastModified)?.let { client.setRequestProperty("If-Modified-Since", it) }
                body?.let { bytes ->
                    client.doOutput = true
                    client.setRequestProperty("Content-Type", contentType)
                    client.setFixedLengthStreamingMode(bytes.size)
                    client.outputStream.use { it.write(bytes) }
                }
                val status = client.responseCode
                if (status == 304) return HttpResult.NotModified
                if (status in setOf(301, 302, 303, 307, 308)) {
                    if (redirect == 5) return HttpResult.Failure(HttpResult.Kind.TOO_MANY_REDIRECTS)
                    val location = client.getHeaderField("Location")
                        ?: return HttpResult.Failure(HttpResult.Kind.HTTP_ERROR, status)
                    uri = try { uri.resolve(location) } catch (_: IllegalArgumentException) {
                        return HttpResult.Failure(HttpResult.Kind.DISALLOWED_URL)
                    }
                    if (status == 303 || status == 301 || status == 302) body = null
                    return@repeat
                }
                if (status !in 200..299) return HttpResult.Failure(HttpResult.Kind.HTTP_ERROR, status)
                if (client.contentLengthLong > maxBytes) return HttpResult.Failure(HttpResult.Kind.TOO_LARGE)
                val encoding = client.getHeaderField("Content-Encoding")?.lowercase(Locale.ROOT)
                if (encoding != null && encoding !in setOf("identity", "gzip")) {
                    return HttpResult.Failure(HttpResult.Kind.UNSUPPORTED_ENCODING)
                }
                client.inputStream.use { raw ->
                    val input = if (encoding == "gzip") GZIPInputStream(raw) else raw
                    input.use {
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = it.read(buffer)
                            if (count < 0) break
                            if (count > maxBytes - output.size()) return HttpResult.Failure(HttpResult.Kind.TOO_LARGE)
                            output.write(buffer, 0, count)
                        }
                        return HttpResult.Body(output.toByteArray(), client.contentType,
                            client.getHeaderField("ETag"), client.getHeaderField("Last-Modified"), status)
                    }
                }
            } catch (_: IOException) {
                return HttpResult.Failure(HttpResult.Kind.NETWORK)
            } finally {
                client.disconnect()
            }
        }
        return HttpResult.Failure(HttpResult.Kind.TOO_MANY_REDIRECTS)
    }
}

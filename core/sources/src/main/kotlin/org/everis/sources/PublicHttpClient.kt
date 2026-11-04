package org.everis.sources

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.util.Locale
import java.util.zip.GZIPInputStream

sealed interface HttpResult {
    data class Body(val bytes: ByteArray, val contentType: String?, val etag: String?, val lastModified: String?) : HttpResult
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
    fun get(initial: URI, etag: String? = null, lastModified: String? = null): HttpResult {
        var uri = initial
        repeat(6) { redirect ->
            if (!allowed(uri)) return HttpResult.Failure(HttpResult.Kind.DISALLOWED_URL)
            val client = try { connection(uri) } catch (_: IOException) {
                return HttpResult.Failure(HttpResult.Kind.NETWORK)
            }
            try {
                client.requestMethod = "GET"
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
                val status = client.responseCode
                if (status == 304) return HttpResult.NotModified
                if (status in setOf(301, 302, 303, 307, 308)) {
                    if (redirect == 5) return HttpResult.Failure(HttpResult.Kind.TOO_MANY_REDIRECTS)
                    val location = client.getHeaderField("Location")
                        ?: return HttpResult.Failure(HttpResult.Kind.HTTP_ERROR, status)
                    uri = try { uri.resolve(location) } catch (_: IllegalArgumentException) {
                        return HttpResult.Failure(HttpResult.Kind.DISALLOWED_URL)
                    }
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
                            client.getHeaderField("ETag"), client.getHeaderField("Last-Modified"))
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

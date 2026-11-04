package org.everis.sources

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.util.zip.GZIPOutputStream

private class Response(
    private val status: Int = 200,
    private val payload: ByteArray = "ok".toByteArray(),
    private val headers: Map<String, String> = emptyMap(),
) : HttpURLConnection(URI("https://rasp.rsue.ru/").toURL()) {
    var closed = false
    override fun connect() = Unit
    override fun disconnect() { closed = true }
    override fun usingProxy() = false
    override fun getResponseCode() = status
    override fun getInputStream() = ByteArrayInputStream(payload)
    override fun getHeaderField(name: String): String? = headers[name]
    override fun getContentLengthLong() = -1L
}

fun main() {
    var passed = 0
    fun test(name: String, action: () -> Unit) {
        try { action(); passed++; println("PASS: $name") }
        catch (error: Throwable) { throw AssertionError(name, error) }
    }
    val uri = URI("https://rasp.rsue.ru/")
    test("HTTP is rejected before opening any connection") {
        val client = PublicHttpClient(setOf("rasp.rsue.ru"), connection = { error("must not connect") })
        check(client.get(URI("http://rasp.rsue.ru/")) == HttpResult.Failure(HttpResult.Kind.DISALLOWED_URL))
    }
    test("unrelated host is rejected") {
        val client = PublicHttpClient(setOf("rasp.rsue.ru"), connection = { error("must not connect") })
        check(client.get(URI("https://example.com/")) == HttpResult.Failure(HttpResult.Kind.DISALLOWED_URL))
    }
    test("public response is returned and connection closed") {
        val response = Response(headers = mapOf("ETag" to "revision-1"))
        val client = PublicHttpClient(setOf("rasp.rsue.ru"), connection = { response })
        val result = client.get(uri) as HttpResult.Body
        check(result.bytes.decodeToString() == "ok" && result.etag == "revision-1" && response.closed)
    }
    test("HTTP errors are not an empty successful schedule") {
        val response = Response(status = 503)
        check(PublicHttpClient(setOf("rasp.rsue.ru"), connection = { response }).get(uri) ==
            HttpResult.Failure(HttpResult.Kind.HTTP_ERROR, 503))
    }
    test("cache validation preserves 304") {
        check(PublicHttpClient(setOf("rasp.rsue.ru"), connection = { Response(status = 304) }).get(uri) == HttpResult.NotModified)
    }
    test("unknown-length response is bounded") {
        val response = Response(payload = ByteArray(20))
        check(PublicHttpClient(setOf("rasp.rsue.ru"), maxBytes = 10, connection = { response }).get(uri) ==
            HttpResult.Failure(HttpResult.Kind.TOO_LARGE))
        check(response.closed)
    }
    test("redirect cannot escape allowed hosts") {
        val client = PublicHttpClient(setOf("rasp.rsue.ru"), connection = {
            Response(status = 302, headers = mapOf("Location" to "https://elsewhere.invalid/"))
        })
        check(client.get(uri) == HttpResult.Failure(HttpResult.Kind.DISALLOWED_URL))
    }
    test("relative redirect succeeds") {
        var requests = 0
        val client = PublicHttpClient(setOf("rasp.rsue.ru"), connection = {
            requests++
            if (requests == 1) Response(status = 302, headers = mapOf("Location" to "/next")) else Response()
        })
        check(client.get(uri) is HttpResult.Body && requests == 2)
    }
    test("redirect loop is bounded") {
        val client = PublicHttpClient(setOf("rasp.rsue.ru"), connection = {
            Response(status = 302, headers = mapOf("Location" to "/again"))
        })
        check(client.get(uri) == HttpResult.Failure(HttpResult.Kind.TOO_MANY_REDIRECTS))
    }
    test("compressed response cannot exceed decompressed limit") {
        val compressed = ByteArrayOutputStream().also { out ->
            GZIPOutputStream(out).use { it.write(ByteArray(100)) }
        }.toByteArray()
        val response = Response(payload = compressed, headers = mapOf("Content-Encoding" to "gzip"))
        check(PublicHttpClient(setOf("rasp.rsue.ru"), maxBytes = 40, connection = { response }).get(uri) ==
            HttpResult.Failure(HttpResult.Kind.TOO_LARGE))
    }
    println("Source checks passed: $passed")
}

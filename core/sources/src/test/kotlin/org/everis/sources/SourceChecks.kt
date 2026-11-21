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
    val sent = ByteArrayOutputStream()
    override fun connect() = Unit
    override fun disconnect() { closed = true }
    override fun usingProxy() = false
    override fun getResponseCode() = status
    override fun getInputStream() = ByteArrayInputStream(payload)
    override fun getOutputStream() = sent
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
    test("public form encodes Cyrillic and separators without creating extra fields") {
        val response = Response()
        val result = PublicHttpClient(setOf("rasp.rsue.ru"), connection = { response })
            .postForm(uri, mapOf("do" to "schedule", "group" to "ПД 1&next=2+3"))
        check(result is HttpResult.Body && response.requestMethod == "POST")
        check(response.getRequestProperty("Content-Type").equals("application/x-www-form-urlencoded; charset=utf-8", true))
        val fields = response.sent.toString("UTF-8").split('&').associate { field ->
            val parts = field.split('=', limit = 2)
            java.net.URLDecoder.decode(parts[0], "UTF-8") to java.net.URLDecoder.decode(parts[1], "UTF-8")
        }
        check(fields == mapOf("do" to "schedule", "group" to "ПД 1&next=2+3") && response.closed)
    }
    test("oversized forms are rejected before opening any connection") {
        val client = PublicHttpClient(setOf("rasp.rsue.ru"), connection = { error("Must not open a connection") })
        check(client.postForm(uri, mapOf("group" to "я".repeat(12_000))) == HttpResult.Failure(HttpResult.Kind.TOO_LARGE))
        check(client.postForm(uri, (1..33).associate { it.toString() to "x" }) == HttpResult.Failure(HttpResult.Kind.TOO_LARGE))
    }
    test("form redirects retain the form content type and never forward to unknown hosts") {
        val first = Response(status = 307, headers = mapOf("Location" to "/next"))
        val next = Response()
        var calls = 0
        val client = PublicHttpClient(setOf("rasp.rsue.ru"), connection = { if (calls++ == 0) first else next })
        check(client.postForm(uri, mapOf("group" to "ПД101")) is HttpResult.Body)
        check(next.getRequestProperty("Content-Type").startsWith("application/x-www-form-urlencoded"))
        val redirect = Response(status = 307, headers = mapOf("Location" to "https://unknown.example/"))
        val denied = PublicHttpClient(setOf("rasp.rsue.ru"), connection = { redirect }).postForm(uri, mapOf("group" to "ПД101"))
        check(denied == HttpResult.Failure(HttpResult.Kind.DISALLOWED_URL) && redirect.closed)
    }
    test("public JSON search sends UTF-8 POST with bounded explicit content type") {
        val response = Response()
        val result = PublicHttpClient(setOf("rasp.rsue.ru"), connection = { response }).postJson(uri, "{\"группа\":\"КТбо1-1\"}")
        check(result is HttpResult.Body && response.requestMethod == "POST")
        check(response.sent.toString("UTF-8") == "{\"группа\":\"КТбо1-1\"}")
        check(response.getRequestProperty("Content-Type").equals("application/json; charset=utf-8", ignoreCase = true) && response.closed)
    }
    test("oversized JSON is rejected before opening a connection") {
        val client = PublicHttpClient(setOf("rasp.rsue.ru"), connection = { error("must not connect") })
        check(client.postJson(uri, "x".repeat(65537)) == HttpResult.Failure(HttpResult.Kind.TOO_LARGE))
    }
    test("POST 307 preserves a public filter and 303 switches to GET") {
        for (status in listOf(307, 303)) {
            val first = Response(status = status, headers = mapOf("Location" to "/next"))
            val next = Response()
            var calls = 0
            val client = PublicHttpClient(setOf("rasp.rsue.ru"), connection = { if (calls++ == 0) first else next })
            check(client.postJson(uri, "{}") is HttpResult.Body)
            check(next.requestMethod == if (status == 307) "POST" else "GET")
            check(next.sent.toString("UTF-8") == if (status == 307) "{}" else "")
        }
    }
    test("a POST redirect cannot forward a filter to an unrelated host") {
        var calls = 0
        val response = Response(status = 307, headers = mapOf("Location" to "https://example.com/"))
        val client = PublicHttpClient(setOf("rasp.rsue.ru"), connection = { calls++; response })
        check(client.postJson(uri, "{}") == HttpResult.Failure(HttpResult.Kind.DISALLOWED_URL) && calls == 1)
    }
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

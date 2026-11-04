package org.everis.app.data

import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Bound nesting before org.json recursion, including JSON strings that carry a raw schedule. */
fun validateJsonStructure(text: String) {
    require(text.length in 1..8 * 1024 * 1024)
    var depth = 0
    var quoted = false
    var escaped = false
    for (c in text) {
        if (quoted) {
            if (escaped) escaped = false
            else if (c == '\\') escaped = true
            else if (c == '"') quoted = false
        } else when (c) {
            '"' -> quoted = true
            '{', '[' -> { depth++; require(depth <= 64) }
            '}', ']' -> { depth--; require(depth >= 0) }
        }
    }
    require(!quoted && depth == 0)
}

fun portableJson(bytes: ByteArray): JSONObject {
    require(bytes.size in 1..8 * 1024 * 1024)
    val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    validateJsonStructure(text)
    return JSONObject(text)
}

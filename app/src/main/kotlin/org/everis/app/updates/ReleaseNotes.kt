package org.everis.app.updates

import java.util.Locale

/** GitHub displays all translations; the app selects one at render time, after a locale change too. */
fun localizedReleaseNotes(markdown: String, languageTag: String): String {
    val aliases = mapOf("english" to "en", "en" to "en", "what’s new" to "en", "what's new" to "en",
        "русский" to "ru", "russian" to "ru", "ru" to "ru", "что нового" to "ru",
        "українська" to "uk", "ukrainian" to "uk", "uk" to "uk",
        "беларуская" to "be", "belarusian" to "be", "be" to "be",
        "қазақша" to "kk", "kazakh" to "kk", "kk" to "kk")
    val language = languageTag.lowercase(Locale.ROOT).substringBefore('-').substringBefore('_')
    val sections = linkedMapOf<String, StringBuilder>()
    var current: StringBuilder? = null
    var fence: Char? = null
    var fenceLength = 0
    for (line in markdown.replace("\r\n", "\n").lineSequence()) {
        val trimmed = line.trimStart()
        val marker = trimmed.firstOrNull()?.takeIf { it == '`' || it == '~' }
        val run = marker?.let { ch -> trimmed.takeWhile { it == ch }.length } ?: 0
        if (run >= 3 && (fence == null || fence == marker && run >= fenceLength)) {
            if (fence == null) { fence = marker; fenceLength = run } else fence = null
            current?.appendLine(line)
            continue
        }
        val heading = if (fence == null) Regex("^#{1,2}\\s+(.+?)(?:\\s+#+)?\\s*$").matchEntire(line)?.groupValues?.get(1) else null
        val code = heading?.lowercase(Locale.ROOT)?.let(aliases::get)
        if (code != null) current = sections.getOrPut(code) { StringBuilder() }
        else current?.appendLine(line)
    }
    fun content(code: String) = sections[code]?.toString()?.trim()?.lines()
        ?.dropLastWhile { it.isBlank() || it.trim() in setOf("---", "***", "___") }
        ?.joinToString("\n")?.trim()?.takeIf { it.isNotBlank() }
    // Older, unsectioned releases remain readable; new releases always contain English.
    return content(language) ?: content("en") ?: if (sections.isEmpty()) markdown.trim() else ""
}

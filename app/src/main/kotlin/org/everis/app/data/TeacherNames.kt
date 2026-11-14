package org.everis.app.data

import java.text.Normalizer
import java.util.Locale

/** Match initials only when the official roster gives exactly one identity. Never guess a person. */
object TeacherNames {
    private fun normalized(value: String) = Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase(Locale.ROOT).replace('ё', 'е').trim()
    fun key(name: String): String? {
        val text = normalized(name)
        val short = Regex("([а-яa-z][а-яa-z'’-]+(?:-[а-яa-z]+)*)\\s*([а-яa-z])\\s*\\.\\s*([а-яa-z])?\\s*\\.?$").find(text)
        if (short != null) return short.groupValues.drop(1).joinToString(":")
        if ('.' in text) return null
        val words = text.split(Regex("\\s+"))
        return if (words.size in 2..4 && words.all { it.length >= 2 && it.none(Char::isDigit) })
            words.first() + ":" + words[1].take(1) + ":" + words.getOrNull(2).orEmpty().take(1) else null
    }
    fun index(names: List<String>): Map<String, String> = names.distinct().filter { name ->
        val words = name.trim().split(Regex("\\s+"))
        words.size in 2..4 && words.all { it.length >= 2 && it.matches(Regex("[\\p{L}][\\p{L}’'-]*")) }
    }.flatMap { name ->
        val key = key(name) ?: return@flatMap emptyList()
        listOf(key to name, (key.substringBeforeLast(':') + ":") to name).distinct()
    }
        .groupBy({ it.first }, { it.second }).mapNotNull { (key, values) -> values.distinct().singleOrNull()?.let { key to it } }.toMap()
    fun resolve(name: String, index: Map<String, String>): String = key(name)?.let(index::get) ?: name
}

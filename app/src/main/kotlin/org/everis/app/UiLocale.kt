package org.everis.app

import android.content.res.Configuration
import java.util.Locale

/** Match the language chooser and dates to the same supported locale as the UI resources. */
fun uiLocale(preferences: List<Locale>): Locale = preferences.firstOrNull {
    it.language in setOf("ru", "uk", "be", "kk", "en")
} ?: Locale.forLanguageTag("ru")

fun uiLocale(configuration: Configuration): Locale = uiLocale(
    (0 until configuration.locales.size()).map { configuration.locales[it] },
)

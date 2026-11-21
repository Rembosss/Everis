package org.everis.app.updates

import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseNotesTest {
    private val notes = "# Everis 0.3-beta\n\n## English\n\n### What’s new\n\n- Smoother motion.\n\n---\n\n## Русский\n\n### Что нового\n\n- Плавные анимации.\n"
    @Test fun selectsRussianWithoutEnglishOrLanguageHeading() {
        assertEquals("### Что нового\n\n- Плавные анимации.", localizedReleaseNotes(notes, "ru-RU"))
    }
    @Test fun fallsBackToEnglishForEveryMissingTranslation() {
        for (language in listOf("uk", "be", "kk", "de", "en_GB"))
            assertEquals("### What’s new\n\n- Smoother motion.", localizedReleaseNotes(notes, language))
    }
    @Test fun selectsAnAvailableTranslationRatherThanTheSystemLanguage() {
        assertEquals("- Оновлено.", localizedReleaseNotes(notes + "\n## Українська\n\n- Оновлено.\n", "uk-UA"))
    }
    @Test fun languageLikeTextInCodeDoesNotChangeSections() {
        val raw = "## English\n~~~md\n## Русский\n~~~\nEnglish text.\n## Русский\nРусский текст."
        assertEquals("~~~md\n## Русский\n~~~\nEnglish text.", localizedReleaseNotes(raw, "en"))
    }
    @Test fun handlesOlderBilingualHeadingNamesAndCrLf() {
        assertEquals("Fixed.", localizedReleaseNotes("## Что нового\r\nИсправлено.\r\n\r\n---\r\n\r\n## What’s new\r\nFixed.\r\n", "kk"))
    }
    @Test fun emptyTranslationFallsBackToEnglishAndLegacyTextIsPreserved() {
        assertEquals("Fixed.", localizedReleaseNotes("## English\nFixed.\n## Русский\n", "ru"))
        assertEquals("Legacy notes.", localizedReleaseNotes("Legacy notes.", "ru"))
    }
}

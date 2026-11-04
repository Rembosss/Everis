package org.everis.app

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class UiLocaleTest {
    @Test fun unsupportedPrimaryLanguageUsesTheNextSupportedPreference() {
        assertEquals("uk", uiLocale(listOf(Locale.FRENCH, Locale.forLanguageTag("uk"), Locale.ENGLISH)).language)
    }
    @Test fun unsupportedLanguagesFallBackToTheRussianResourceCatalogue() {
        assertEquals("ru", uiLocale(listOf(Locale.FRENCH, Locale.GERMAN)).language)
    }
    @Test fun supportedLocaleKeepsItsRegionalDateFormatting() {
        assertEquals(Locale.US, uiLocale(listOf(Locale.US)))
    }
    @Test fun anEmptyLanguageListHasASafeFallback() {
        assertEquals("ru", uiLocale(emptyList()).language)
    }
}

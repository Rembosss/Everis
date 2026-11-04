package org.everis.app

import org.junit.Assert.*
import org.junit.Test

class NavigationPolicyTest {
    @Test fun weekAndMonthTransitionsHaveOppositeDirectionsAtSameDate() {
        assertEquals(-1, calendarDirection(0, "2026-11-04", 1, "2026-11-04"))
        assertEquals(1, calendarDirection(1, "2026-11-04", 0, "2026-11-04"))
    }
    @Test fun periodNavigationUsesItsDateInEitherMode() {
        for (mode in 0..1) {
            assertEquals(1, calendarDirection(mode, "2026-11-04", mode, "2026-12-04"))
            assertEquals(-1, calendarDirection(mode, "2026-12-04", mode, "2026-11-04"))
        }
    }
    @Test fun modeChangeHasPriorityOverIncidentalDateChange() {
        assertEquals(1, calendarDirection(1, "2026-11-01", 0, "2026-11-04"))
    }
    @Test fun everyPairOfDifferentTabsReversesItsDirection() {
        for (first in 0..3) for (second in first+1..3) {
            assertEquals(1, pageDirection(first, second))
            assertEquals(-1, pageDirection(second, first))
        }
    }
    @Test fun rapidAlternatingDestinationsUseTheirPositionsRatherThanTheirVisitOrder() {
        val tabs = listOf(0, 3, 1, 2, 0, 2, 1, 3, 0)
        var selection = TabSelection(tabs.first())
        val directions = tabs.drop(1).map { target -> selection = selection.select(target); selection.direction }
        assertEquals(listOf(1, -1, 1, -1, 1, -1, 1, -1), directions)
    }
    @Test fun repeatedTapKeepsTheSameTargetAndDoesNotRestartItsTransition() {
        val selection = TabSelection(3, -1)
        assertSame(selection, selection.select(3))
    }
    @Test(expected = IllegalArgumentException::class) fun invalidTabIsRejected() { TabSelection(0).select(4) }
    @Test fun selectedTabSurvivesRecreationWithoutAHistory() {
        for (tab in 0..3) assertEquals(tab, restoreSelectedTab(tab))
    }
    @Test fun legacySavedHistoryRetainsOnlyItsCurrentScreen() {
        assertEquals(2, restoreSelectedTab(intArrayOf(0, 3, 2)))
        assertEquals(1, restoreSelectedTab(listOf(0, 2, 1)))
    }
    @Test fun malformedSavedNavigationFallsBackToTheHomeScreen() {
        for (saved in listOf(null, "2", -1, intArrayOf(), intArrayOf(0, 4), intArrayOf(0, 2, 2), listOf(0, "2")))
            assertEquals(0, restoreSelectedTab(saved))
    }
    @Test fun removedSettingsCategoryRestoresToARealPage() {
        assertEquals("language", restoreSettingsPage("general"))
        assertEquals("root", restoreSettingsPage("removed"))
        assertEquals("haptics", restoreSettingsPage("haptics"))
    }
}

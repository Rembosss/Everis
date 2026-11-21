package org.everis.app

import androidx.compose.ui.unit.*
import org.junit.Assert.*
import org.junit.Test

class AnchoredMenuPositionTest {
    private fun place(anchor: IntRect, window: IntSize, menu: IntSize, below: Boolean = true,
        direction: LayoutDirection = LayoutDirection.Ltr) = anchoredMenuPosition(anchor, window, menu, direction, 16, 32, below)

    @Test fun onboardingUsesTheVisibleFrameWithoutSubtractingTheKeyboardAgain() {
        val result = place(IntRect(64, 420, 1016, 588), IntSize(1080, 1390), IntSize(952, 600))
        assertEquals(754, result.availableHeight)
        assertEquals(IntOffset(64, 604), result.offset)
        assertFalse(result.above)
    }
    @Test fun firstTallMeasurementCannotMoveTheSuggestionsAboveTheirInput() {
        val result = place(IntRect(64, 780, 1016, 948), IntSize(1080, 1200), IntSize(952, 630))
        assertEquals(204, result.availableHeight)
        assertEquals(964, result.offset.y)
        assertFalse(result.above)
    }
    @Test fun ordinarySelectorsMayUseTheSpaceAboveWhenTheBottomIsTooSmall() {
        val result = place(IntRect(100, 900, 900, 1068), IntSize(1080, 1200), IntSize(800, 400), below = false)
        assertTrue(result.above)
        assertEquals(484, result.offset.y)
        assertEquals(852, result.availableHeight)
    }
    @Test fun movingTheInputUpAsTheKeyboardOpensKeepsTheMenuAttached() {
        val low = place(IntRect(64, 780, 1016, 948), IntSize(1080, 1390), IntSize(952, 600))
        val high = place(IntRect(64, 420, 1016, 588), IntSize(1080, 1390), IntSize(952, 600))
        assertEquals(360, low.offset.y - high.offset.y)
        assertEquals(360, high.availableHeight - low.availableHeight)
    }
    @Test fun rtlAlignmentAndConstrainedWindowsRemainInsideTheVisibleFrame() {
        val rtl = place(IntRect(100, 100, 900, 268), IntSize(1080, 1200), IntSize(600, 400), direction = LayoutDirection.Rtl)
        assertEquals(300, rtl.offset.x)
        val empty = place(IntRect(0, 0, 0, 0), IntSize.Zero, IntSize(600, 400))
        assertEquals(IntOffset.Zero, empty.offset)
        assertEquals(0, empty.availableHeight)
    }
}

package org.everis.app

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class LessonColorsTest {
    private fun luminance(argb: Long): Double {
        fun linear(shift: Int): Double {
            val c = ((argb shr shift) and 255).toDouble() / 255
            return if (c <= .04045) c / 12.92 else ((c + .055) / 1.055).pow(2.4)
        }
        return .2126 * linear(16) + .7152 * linear(8) + .0722 * linear(0)
    }
    @Test fun allLessonTypeLabelsHaveReadableContrastInBothThemes() {
        (lessonLightRoles + lessonDarkRoles).forEach { role ->
            val a = luminance(role.background); val b = luminance(role.foreground)
            val contrast = (maxOf(a,b) + .05) / (minOf(a,b) + .05)
            assertTrue("contrast=$contrast", contrast >= 4.5)
        }
    }
}

package org.everis.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.everis.core.LessonType

/** Semantic roles stay distinct when Monet's primary and secondary become nearly identical. */
data class LessonColorRole(val background: Long, val foreground: Long)
val lessonLightRoles = listOf(
    LessonColorRole(0xFFEBDDFF, 0xFF3F185F),
    LessonColorRole(0xFFD6E8FF, 0xFF123C64),
    LessonColorRole(0xFFFFDFB5, 0xFF633400),
    LessonColorRole(0xFFE4E2E7, 0xFF35343A),
)
val lessonDarkRoles = listOf(
    LessonColorRole(0xFF422659, 0xFFEBDDFF),
    LessonColorRole(0xFF173B5D, 0xFFD6E8FF),
    LessonColorRole(0xFF57320A, 0xFFFFDFB5),
    LessonColorRole(0xFF343238, 0xFFE8E2EB),
)
@Composable
fun lessonColors(type: LessonType): Pair<Color, Color> {
    val index = when (type) { LessonType.LECTURE -> 0; LessonType.PRACTICE -> 1; LessonType.LABORATORY -> 2; else -> 3 }
    val role = (if (MaterialTheme.colorScheme.background.luminance() < .2f) lessonDarkRoles else lessonLightRoles)[index]
    return Color(role.background) to Color(role.foreground)
}
fun lessonAbbreviation(type: LessonType): Int = when (type) {
    LessonType.LECTURE -> R.string.lecture_short
    LessonType.PRACTICE -> R.string.practice_short
    LessonType.LABORATORY -> R.string.lab_short
    else -> R.string.other_short
}

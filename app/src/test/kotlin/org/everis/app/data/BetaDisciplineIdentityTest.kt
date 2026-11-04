package org.everis.app.data

import org.junit.Assert.*
import org.junit.Test

class BetaDisciplineIdentityTest {
    @Test fun subgroupNumberAfterLabelDoesNotCreateAnotherCourse() {
        listOf("п/г 1", "п/г 2", "П/Г 1", "подгруппа 2", "подгр. №1", "subgroup 2").forEach { suffix ->
            assertEquals(suffix, "физика", disciplineKey("Физика ($suffix)"))
        }
    }
    @Test fun subgroupNumberBeforeLabelDoesNotCreateAnotherCourse() {
        assertEquals(disciplineKey("Физика 1 п/г"), disciplineKey("Физика 2 п/г"))
        assertEquals("Физика", disciplineTitle("Физика (1п/г)"))
    }
    @Test fun formatAndSubgroupSuffixOrderIsIrrelevant() {
        listOf("(лаб.) (п/г 1)", "(п/г 2) (лекция)", "(лаб., п/г 1)", "(подгруппа 2; практика)", "— п/г 1").forEach {
            assertEquals(it, "Математика", disciplineTitle("Математика $it"))
        }
    }
    @Test fun realCourseQualifiersAndRawNamesArePreserved() {
        assertNotEquals(disciplineKey("Математика 1"), disciplineKey("Математика 2"))
        assertNotEquals(disciplineKey("Язык (английский) (п/г 1)"), disciplineKey("Язык (немецкий) (п/г 2)"))
        assertEquals("Язык (английский)", disciplineTitle("Язык (английский) (п/г 1)"))
    }
}

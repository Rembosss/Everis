package org.everis.app.data

import org.junit.Assert.*
import org.junit.Test

class TeacherNamesTest {
    @Test fun resolvesAcademicPrefixAndMissingSpacesWithoutDroppingPatronymic() {
        val name = "Петров Пётр Павлович"
        val index = TeacherNames.index(listOf(name))
        assertEquals(name, TeacherNames.resolve("доц.ПетровП.П.", index))
        assertEquals(name, TeacherNames.resolve(" Петров П. П.  ", index))
    }
    @Test fun singleInitialIsOnlyExpandedWhenUnique() {
        val name = "Возиянов Дмитрий Михайлович"
        assertEquals(name, TeacherNames.resolve("Возиянов Д.", TeacherNames.index(listOf(name))))
        val short = "Возиянов Д."
        assertEquals(short, TeacherNames.resolve(short, TeacherNames.index(listOf(name, "Возиянов Денис Сергеевич"))))
    }
    @Test fun sameSurnameAndInitialsNeverSelectAnArbitraryPerson() {
        val short = "доц.Иванов И.И."
        val index = TeacherNames.index(listOf("Иванов Иван Иванович", "Иванов Илья Иванович"))
        assertEquals(short, TeacherNames.resolve(short, index))
    }
    @Test fun anAbbreviatedDirectoryEntryDoesNotObscureAnOfficialFullName() {
        val full = "Акопян Владимир Феликсович"
        assertEquals(full, TeacherNames.resolve("Акопян В. Ф.", TeacherNames.index(listOf(full, "Акопян В. Ф."))))
    }
    @Test fun normalizesYoAndHyphenatedSurnamesForMatchingOnly() {
        val full = "Семёнов-Иванов Пётр Алексеевич"
        assertEquals(full, TeacherNames.resolve("Семенов-Иванов П.А.", TeacherNames.index(listOf(full))))
    }
    @Test fun unknownAndMalformedAliasesRetainTheirSourceSpelling() {
        val index = TeacherNames.index(listOf("Петров Пётр Павлович"))
        assertEquals("Сидоров С.С.", TeacherNames.resolve("Сидоров С.С.", index))
        assertNull(TeacherNames.key("МЕН-144"))
        assertNull(TeacherNames.key("доц. неизвестный преподаватель"))
    }
}

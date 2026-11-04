package org.everis.core

data class CityRef(val id: String, val name: String)
data class UniversityRef(val id: String, val cityId: String, val sourceId: String, val name: String)
data class FacultyRef(val id: String, val universityId: String, val name: String)
data class GroupRef(val id: String, val universityId: String, val facultyId: String?, val name: String)

enum class SetupStep { CITY, UNIVERSITY, FACULTY, GROUP, CONFIRM }

data class CatalogRequestToken(val revision: Long, val step: SetupStep)

data class OnboardingDraft(
    val city: CityRef? = null,
    val university: UniversityRef? = null,
    val faculty: FacultyRef? = null,
    val group: GroupRef? = null,
    val step: SetupStep = SetupStep.CITY,
    val revision: Long = 0,
    val facultyAvailable: Boolean = true,
) {
    fun chooseCity(value: CityRef): OnboardingDraft =
        OnboardingDraft(city = value, step = SetupStep.UNIVERSITY, revision = revision + 1)

    fun chooseUniversity(value: UniversityRef): OnboardingDraft {
        require(value.cityId == city?.id) { "University belongs to another city" }
        return copy(university = value, faculty = null, group = null, step = SetupStep.FACULTY,
            facultyAvailable = true, revision = revision + 1)
    }

    fun withoutFaculty(): OnboardingDraft {
        require(university != null)
        return copy(faculty = null, group = null, step = SetupStep.GROUP,
            facultyAvailable = false, revision = revision + 1)
    }

    fun chooseFaculty(value: FacultyRef): OnboardingDraft {
        require(value.universityId == university?.id)
        return copy(faculty = value, group = null, step = SetupStep.GROUP,
            facultyAvailable = true, revision = revision + 1)
    }

    fun chooseGroup(value: GroupRef): OnboardingDraft {
        require(value.universityId == university?.id)
        require(!facultyAvailable || (faculty != null && value.facultyId == faculty.id))
        return copy(group = value, step = SetupStep.CONFIRM, revision = revision + 1)
    }

    fun requestToken(): CatalogRequestToken = CatalogRequestToken(revision, step)
    fun accepts(token: CatalogRequestToken): Boolean = token == requestToken()
}

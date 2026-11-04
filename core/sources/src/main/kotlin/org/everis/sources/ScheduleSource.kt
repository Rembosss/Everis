package org.everis.sources

import java.net.URI
import org.everis.core.CityRef
import org.everis.core.FacultyRef
import org.everis.core.FetchResult
import org.everis.core.GroupRef
import org.everis.core.UniversityRef

data class SourceDescriptor(val id: String, val entryPoint: URI, val allowedHosts: Set<String>)

sealed interface CatalogResult<out T> {
    data class Items<T>(val values: List<T>, val complete: Boolean) : CatalogResult<T>
    data object LevelNotSupported : CatalogResult<Nothing>
    data class Unavailable(val reason: String) : CatalogResult<Nothing>
}

data class InstitutionDirectory(val cities: List<CityRef>, val universities: List<UniversityRef>)

interface ScheduleSource {
    val descriptor: SourceDescriptor
    suspend fun institutions(): CatalogResult<InstitutionDirectory>
    suspend fun faculties(university: UniversityRef): CatalogResult<FacultyRef>
    suspend fun groups(university: UniversityRef, faculty: FacultyRef?, query: String): CatalogResult<GroupRef>
    suspend fun schedule(group: GroupRef): FetchResult
}

object KnownSources {
    // Only bootstrap addresses are bundled. User-facing catalogue data must be
    // obtained from these public sources after the response contract is audited.
    val rsue = SourceDescriptor("rsue", URI("https://rasp.rsue.ru/"),
        setOf("rasp.rsue.ru", "rasp-api.rsue.ru", "rsue.ru", "www.rsue.ru"))
}

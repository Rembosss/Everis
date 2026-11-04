package org.everis.app.data

import org.everis.core.Lesson
import java.text.Normalizer
import java.time.Instant
import java.util.Locale

// Strip only explicit teaching-format suffixes; keep meaningful qualifiers of the course.
private val teachingForm = """(?:лек(?:ция|ции|ц)?|лк|прак(?:тика|тические занятия|т)?|пр|пз|лаб(?:ораторн(?:ая(?: работа)?|ые (?:работы|занятия)))?|лр|л/р)"""
private val formatSuffix = Regex("""(?iu)\s*(?:\(\s*$teachingForm[.]?\s*\)|\[\s*$teachingForm[.]?\s*\]|[-–—:]\s*$teachingForm[.]?)\s*$""")
// Public sources sometimes repeat subgroup labels in the subject itself. These are
// occurrence metadata, not separate courses; retain real lesson IDs and subgroup fields.
private val subgroupWord = """(?:п\s*[/\\.]\s*г[.]?|подгр(?:уппа|уппы)?[.]?|subgroup)"""
private val subgroupNumber = """\d{1,2}(?:\s*[,/и&]\s*\d{1,2})?"""
// NFKC converts the numero sign № to "No".
private val subgroupLabel = """(?:$subgroupWord\s*(?:[:№#-]|No[.]?)?\s*$subgroupNumber|$subgroupNumber\s*$subgroupWord)"""
private val subgroupSuffix = Regex("""(?iu)\s*(?:\(\s*$subgroupLabel\s*\)|\[\s*$subgroupLabel\s*\]|(?:[-–—:,]\s*)?$subgroupLabel)\s*$""")
private val combinedSuffix = Regex("""(?iu)\s*\(\s*(?:$teachingForm[.]?\s*[,;/]\s*$subgroupLabel|$subgroupLabel\s*[,;/]\s*$teachingForm[.]?)\s*\)\s*$""")
fun disciplineTitle(subject: String): String {
    var title = Normalizer.normalize(subject, Normalizer.Form.NFKC)
        .replace('\u00a0', ' ').replace(Regex("\\s+"), " ").trim()
    // Both orders occur: "(лаб.) (п/г 1)" and "(п/г 1) (лаб.)".
    repeat(4) { title = title.replace(combinedSuffix, "").trim().replace(subgroupSuffix, "").trim().replace(formatSuffix, "").trim() }
    return title.ifBlank { subject.trim() }
}
fun disciplineKey(subject: String): String = disciplineTitle(subject).lowercase(Locale.ROOT).replace('ё', 'е')
fun disciplineTargets(lessons: List<Lesson>, subject: String, after: Instant): List<Lesson> = lessons
    .filter { !it.cancelled && disciplineKey(it.disciplineName) == disciplineKey(subject) && it.start.toInstant() >= after }
    .sortedWith(compareBy<Lesson> { it.start }.thenBy { it.subgroup.orEmpty() }.thenBy { it.id })

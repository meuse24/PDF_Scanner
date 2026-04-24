package info.meuse24.pdf_scanner.domain.service

import info.meuse24.pdf_scanner.domain.model.BusinessCard
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BusinessCardParser @Inject constructor() {
    private val emailRegex = Regex("""[\w.+-]+@[\w-]+\.[\w.-]+""")
    private val phoneRegex = Regex("""[+(]?\d[\d\s().\-/]{6,}""")
    private val urlRegex = Regex("""(?i)(https?://|www\.)[^\s]+""")
    private val organizationHints = listOf(
        "gmbh", "ag", "kg", "ug", "se", "ltd", "inc", "llc", "corp", "company"
    )
    private val jobTitleHints = listOf(
        "manager", "developer", "engineer", "director", "consultant", "sales",
        "marketing", "founder", "ceo", "cto", "cfo", "lead", "produkt", "vertrieb"
    )
    private val addressHints = listOf(
        "street", "str.", "strasse", "straße", "road", "rd", "avenue", "ave",
        "platz", "gasse", "lane", "boulevard", "blvd"
    )

    fun parse(text: String): BusinessCard {
        val lines = text.lines()
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()

        val emails = emailRegex.findAll(text).map { it.value }.distinct().toList()
        val phones = phoneRegex.findAll(text)
            .map { it.value.trim().replace(Regex("\\s+"), " ") }
            .filter { candidate -> candidate.count(Char::isDigit) >= 8 }
            .distinct()
            .toList()
        val urls = urlRegex.findAll(text)
            .map { match ->
                val value = match.value.trim()
                if (value.startsWith("http", ignoreCase = true)) value else "https://$value"
            }
            .distinct()
            .toList()

        val nonContactLines = lines.filterNot(::looksLikeContactLine)
        val organization = nonContactLines.firstOrNull(::looksLikeOrganization)
        val fullName = nonContactLines.firstOrNull { line ->
            looksLikeName(line) && line != organization
        }
        val jobTitle = nonContactLines.firstOrNull { line ->
            line != fullName && line != organization && looksLikeJobTitle(line)
        }
        val address = extractAddress(lines)

        return BusinessCard(
            fullName = fullName,
            organization = organization,
            jobTitle = jobTitle,
            emails = emails,
            phones = phones,
            urls = urls,
            address = address
        )
    }

    private fun looksLikeContactLine(line: String): Boolean =
        emailRegex.containsMatchIn(line) ||
            urlRegex.containsMatchIn(line) ||
            phoneRegex.containsMatchIn(line)

    private fun looksLikeOrganization(line: String): Boolean {
        val lower = line.lowercase()
        if (organizationHints.any(lower::contains)) return true
        val words = line.split(Regex("\\s+"))
        return words.size in 1..5 && words.count { word ->
            word.length > 1 && word.all { it.isUpperCase() || !it.isLetter() }
        } >= words.size.coerceAtLeast(1)
    }

    private fun looksLikeName(line: String): Boolean {
        val words = line.split(Regex("\\s+"))
        if (words.size !in 2..4) return false
        if (looksLikeOrganization(line)) return false
        return words.all { word ->
            val trimmed = word.trim('.', ',', ';')
            trimmed.isNotBlank() &&
                trimmed.first().isUpperCase() &&
                trimmed.drop(1).all { it.isLowerCase() || it == '-' }
        }
    }

    private fun looksLikeJobTitle(line: String): Boolean {
        val lower = line.lowercase()
        return jobTitleHints.any(lower::contains)
    }

    private fun extractAddress(lines: List<String>): String? {
        val candidates = lines.filter { line ->
            val lower = line.lowercase()
            addressHints.any(lower::contains) ||
                Regex("""\b\d{4,5}\b""").containsMatchIn(line)
        }
        return candidates.takeIf { it.isNotEmpty() }?.joinToString(", ")
    }
}

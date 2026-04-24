package info.meuse24.pdf_scanner.domain.service

import info.meuse24.pdf_scanner.domain.model.BusinessCard
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VCardBuilder @Inject constructor() {
    fun build(card: BusinessCard): String {
        val lines = mutableListOf(
            "BEGIN:VCARD",
            "VERSION:3.0"
        )

        card.fullName?.takeIf { it.isNotBlank() }?.let { lines += "FN:${escape(it)}" }
        card.organization?.takeIf { it.isNotBlank() }?.let { lines += "ORG:${escape(it)}" }
        card.jobTitle?.takeIf { it.isNotBlank() }?.let { lines += "TITLE:${escape(it)}" }
        card.phones.forEach { phone -> lines += "TEL;TYPE=CELL:${escape(phone)}" }
        card.emails.forEach { email -> lines += "EMAIL:${escape(email)}" }
        card.urls.forEach { url -> lines += "URL:${escape(url)}" }
        card.address?.takeIf { it.isNotBlank() }?.let { lines += "ADR:;;${escape(it)}" }

        lines += "END:VCARD"
        return lines.joinToString("\n")
    }

    private fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\n", "\\n")
}

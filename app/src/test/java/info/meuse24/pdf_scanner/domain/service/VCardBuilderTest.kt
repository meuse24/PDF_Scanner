package info.meuse24.pdf_scanner.domain.service

import info.meuse24.pdf_scanner.domain.model.BusinessCard
import org.junit.Assert.assertTrue
import org.junit.Test

class VCardBuilderTest {
    private val builder = VCardBuilder()

    @Test
    fun `escapes commas and semicolons`() {
        val vcard = builder.build(
            BusinessCard(
                fullName = "Doe, Jane",
                organization = "ACME; Labs",
                address = "Main Street 1\n12345 City"
            )
        )

        assertTrue(vcard.contains("FN:Doe\\, Jane"))
        assertTrue(vcard.contains("ORG:ACME\\; Labs"))
        assertTrue(vcard.contains("ADR:;;Main Street 1\\n12345 City"))
    }
}

package info.meuse24.pdf_scanner.domain.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BusinessCardParserTest {
    private val parser = BusinessCardParser()

    @Test
    fun `parses common business card fields`() {
        val result = parser.parse(
            """
            Max Mustermann
            ACME GmbH
            Lead Engineer
            max@acme.de
            +49 151 1234567
            www.acme.de
            Hauptstr. 12
            10115 Berlin
            """.trimIndent()
        )

        assertEquals("Max Mustermann", result.fullName)
        assertEquals("ACME GmbH", result.organization)
        assertEquals("Lead Engineer", result.jobTitle)
        assertEquals(listOf("max@acme.de"), result.emails)
        assertEquals(listOf("+49 151 1234567"), result.phones)
        assertEquals(listOf("https://www.acme.de"), result.urls)
        assertTrue(result.address?.contains("Berlin") == true)
    }
}

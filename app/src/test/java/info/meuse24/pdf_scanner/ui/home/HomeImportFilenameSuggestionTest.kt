package info.meuse24.pdf_scanner.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeImportFilenameSuggestionTest {

    @Test
    fun `removes pdf extension ignoring case`() {
        assertEquals("Invoice 2026", suggestedFilenameFromDisplayName("Invoice 2026.PDF"))
    }

    @Test
    fun `keeps names without pdf extension unchanged`() {
        assertEquals("Contract Draft", suggestedFilenameFromDisplayName("Contract Draft"))
    }

    @Test
    fun `returns empty string for blank display name`() {
        assertEquals("", suggestedFilenameFromDisplayName("   "))
    }
}

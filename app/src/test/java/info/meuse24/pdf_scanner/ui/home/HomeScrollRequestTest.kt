package info.meuse24.pdf_scanner.ui.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeScrollRequestTest {

    private val request = AddedDocumentScrollRequest(
        documentId = 23L,
        folderId = 1L
    )

    @Test
    fun `folder request matches its original folder`() {
        assertTrue(
            request.matchesArchiveContext(
                HomeArchiveUiState(currentFolderId = 1L)
            )
        )
    }

    @Test
    fun `folder request becomes stale after switching folders`() {
        assertFalse(
            request.matchesArchiveContext(
                HomeArchiveUiState(currentFolderId = 2L)
            )
        )
    }

    @Test
    fun `request becomes stale when a search is active`() {
        assertFalse(
            request.matchesArchiveContext(
                HomeArchiveUiState(
                    currentFolderId = 1L,
                    searchQuery = "invoice"
                )
            )
        )
    }
}

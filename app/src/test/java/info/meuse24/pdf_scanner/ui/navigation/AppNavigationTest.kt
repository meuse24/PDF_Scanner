package info.meuse24.pdf_scanner.ui.navigation

import android.net.Uri
import info.meuse24.pdf_scanner.ui.entry.AppEntryAction
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class AppNavigationTest {

    @Test
    fun `pending home action does not trigger archive navigation during cold start`() {
        assertFalse(
            shouldNavigatePendingHomeActionToArchive(
                pendingAppEntryAction = AppEntryAction.SharePdf(mock(Uri::class.java)),
                currentRoute = null
            )
        )
    }

    @Test
    fun `scan new action navigates back to archive from another screen`() {
        assertTrue(
            shouldNavigatePendingHomeActionToArchive(
                pendingAppEntryAction = AppEntryAction.ScanNew,
                currentRoute = Screen.Settings.route
            )
        )
    }

    @Test
    fun `pending home action navigates back to archive from another screen`() {
        assertTrue(
            shouldNavigatePendingHomeActionToArchive(
                pendingAppEntryAction = AppEntryAction.ShareImages(
                    listOf(mock(Uri::class.java))
                ),
                currentRoute = Screen.Viewer.createRoute(42L)
            )
        )
    }

    @Test
    fun `open trash is not handled by archive navigation guard`() {
        assertFalse(
            shouldNavigatePendingHomeActionToArchive(
                pendingAppEntryAction = AppEntryAction.OpenTrash,
                currentRoute = Screen.Settings.route
            )
        )
    }

    @Test
    fun `pending home action does not navigate when archive is already visible`() {
        assertFalse(
            shouldNavigatePendingHomeActionToArchive(
                pendingAppEntryAction = AppEntryAction.ImportImages,
                currentRoute = Screen.Ablage.route
            )
        )
    }
}

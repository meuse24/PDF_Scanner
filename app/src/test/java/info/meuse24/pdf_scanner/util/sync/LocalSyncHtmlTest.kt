package info.meuse24.pdf_scanner.util.sync

import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.testutil.FakeResourceProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSyncHtmlTest {

    private fun resourceProvider(lang: String = "en") = FakeResourceProvider(
        strings = mapOf(
            R.string.app_label to "PDF Scan",
            R.string.local_sync_pin_label to "PIN",
            R.string.local_sync_web_login_title to "Login",
            R.string.local_sync_web_login_heading to "PC connection",
            R.string.local_sync_web_login_instructions to "Enter the PIN.",
            R.string.local_sync_web_login_button to "Connect",
            R.string.local_sync_web_pin_digit_label to "PIN digit %1\$d of %2\$d",
            R.string.local_sync_web_documents_title to "Archive",
            R.string.local_sync_web_documents_heading to "Your archive",
            R.string.local_sync_web_empty_state to "No documents yet.",
            R.string.local_sync_web_upload_heading to "Upload PDF",
            R.string.local_sync_web_upload_hint to "Max 25 MB.",
            R.string.local_sync_web_upload_dropzone_label to "Choose a PDF or drop it here",
            R.string.local_sync_web_upload_button to "Upload",
            R.string.local_sync_web_html_lang to lang
        )
    )

    @Test
    fun `login page dir attribute follows the configured language`() {
        val ltrPage = renderLoginPage(resourceProvider(lang = "en"), error = null)
        val rtlPage = renderLoginPage(resourceProvider(lang = "ar"), error = null)

        assertTrue(ltrPage.contains("dir=\"ltr\""))
        assertTrue(rtlPage.contains("dir=\"rtl\""))
    }

    @Test
    fun `each otp box has a distinct, positional accessible name`() {
        val page = renderLoginPage(resourceProvider(), error = null)

        assertTrue(page.contains("aria-label=\"PIN digit 1 of 4\""))
        assertTrue(page.contains("aria-label=\"PIN digit 2 of 4\""))
        assertTrue(page.contains("aria-label=\"PIN digit 3 of 4\""))
        assertTrue(page.contains("aria-label=\"PIN digit 4 of 4\""))
    }

    @Test
    fun `file input stays keyboard focusable instead of being fully hidden`() {
        val page = renderDocumentsPage(resourceProvider(), emptyList())

        assertFalse(
            "a hidden file input is unreachable for keyboard and screen-reader users",
            page.contains("id=\"fileInput\" accept=\"application/pdf\" required hidden")
        )
        assertTrue(page.contains("class=\"visually-hidden\""))
        assertTrue(page.contains("Choose a PDF or drop it here"))
    }

    @Test
    fun `dropzone focus ring targets only the file input, not the whole form`() {
        val page = renderDocumentsPage(resourceProvider(), emptyList())

        assertTrue(page.contains("#fileInput:focus-visible + .dropzone-inner"))
        assertFalse(
            "a form-wide :focus-within selector would also ring the submit button when it is focused",
            page.contains(".dropzone:focus-within")
        )
    }

    @Test
    fun `upload success is announced as a status region`() {
        val page = renderDocumentsPage(resourceProvider(), emptyList(), UploadFeedback.Success("Imported."))

        assertTrue(page.contains("class=\"alert alert-success\" role=\"status\""))
        assertFalse(page.contains("role=\"alert\">Imported."))
    }

    @Test
    fun `upload failure is announced as an alert, not a status region`() {
        val page = renderDocumentsPage(resourceProvider(), emptyList(), UploadFeedback.Error("The file is not a valid PDF."))

        assertTrue(page.contains("class=\"alert alert-danger\" role=\"alert\""))
        assertFalse(page.contains("class=\"alert alert-success\""))
    }

    @Test
    fun `empty state is shown when there are no documents`() {
        val page = renderDocumentsPage(resourceProvider(), emptyList())

        assertTrue(page.contains("No documents yet."))
        assertFalse(page.contains("class=\"doc-list\""))
    }

    @Test
    fun `documents page dir attribute follows the configured language`() {
        assertEquals(true, renderDocumentsPage(resourceProvider(lang = "ar"), emptyList()).contains("dir=\"rtl\""))
        assertEquals(true, renderDocumentsPage(resourceProvider(lang = "en"), emptyList()).contains("dir=\"ltr\""))
    }
}

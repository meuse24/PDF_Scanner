package info.meuse24.pdf_scanner.util

import com.tom_roush.pdfbox.pdmodel.PDDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PdfEditorRealIntegrationTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val editor = PdfEditor()

    @Test
    fun `protect and unlock roundtrip keeps page signatures and encryption state`() {
        val expectedPages = listOf(
            PdfPageSignature(width = 401, height = 501, rotation = 0),
            PdfPageSignature(width = 421, height = 531, rotation = 0)
        )
        val input = TestPdfFactory.createPdf(
            tmpFolder.newFile("roundtrip.pdf"),
            listOf(
                TestPdfPage(width = 401f, height = 501f),
                TestPdfPage(width = 421f, height = 531f)
            )
        )

        val protected = editor.protectPdf(input, tmpFolder.root, "secret123")

        assertPdfEncrypted(editor, protected)
        assertPdfPageSignatures(protected, expectedPages, "secret123")

        val unlocked = editor.unlockPdf(protected, tmpFolder.root, "secret123")

        assertPdfNotEncrypted(editor, unlocked)
        assertPdfPageSignatures(unlocked, expectedPages)
    }

    @Test
    fun `unlock with wrong password fails explicitly`() {
        val input = TestPdfFactory.createPdf(
            tmpFolder.newFile("wrong-password.pdf"),
            listOf(TestPdfPage(width = 411f, height = 511f))
        )
        val protected = editor.protectPdf(input, tmpFolder.root, "secret123")

        assertThrows(PdfEditor.WrongPasswordException::class.java) {
            editor.unlockPdf(protected, tmpFolder.root, "bad-password")
        }
    }

    @Test
    fun `restrict usage disables permissions and remove password restores readable copy`() {
        val expectedPages = listOf(PdfPageSignature(width = 481, height = 641, rotation = 0))
        val input = TestPdfFactory.createPdf(
            tmpFolder.newFile("restricted.pdf"),
            listOf(TestPdfPage(width = 481f, height = 641f))
        )

        val restricted = editor.restrictUsage(
            input = input,
            outputDir = tmpFolder.root,
            ownerPassword = "owner-secret",
            canPrint = false,
            canCopy = false,
            canEdit = false
        )

        assertPdfEncrypted(editor, restricted)
        PDDocument.load(restricted, "").use { document ->
            val permission = document.currentAccessPermission
            assertFalse(permission.canPrint())
            assertFalse(permission.canExtractContent())
            assertFalse(permission.canModify())
        }

        val unrestricted = editor.removePassword(restricted, tmpFolder.root)

        assertPdfNotEncrypted(editor, unrestricted)
        assertPdfPageSignatures(unrestricted, expectedPages)
    }

    @Test
    fun `reorder keeps requested page order on real pdfs`() {
        val input = TestPdfFactory.createPdf(
            tmpFolder.newFile("reorder.pdf"),
            listOf(
                TestPdfPage(width = 301f, height = 401f),
                TestPdfPage(width = 302f, height = 402f),
                TestPdfPage(width = 303f, height = 403f)
            )
        )

        val reordered = editor.reorderPages(input, listOf(2, 0, 1), saveAsCopy = true)

        assertPdfPageSignatures(
            reordered,
            listOf(
                PdfPageSignature(width = 303, height = 403, rotation = 0),
                PdfPageSignature(width = 301, height = 401, rotation = 0),
                PdfPageSignature(width = 302, height = 402, rotation = 0)
            )
        )
    }

    @Test
    fun `duplicate and delete manipulate real page order`() {
        val input = TestPdfFactory.createPdf(
            tmpFolder.newFile("duplicate-delete.pdf"),
            listOf(
                TestPdfPage(width = 310f, height = 410f),
                TestPdfPage(width = 320f, height = 420f),
                TestPdfPage(width = 330f, height = 430f)
            )
        )

        val duplicated = editor.duplicatePages(input, tmpFolder.root, listOf(1))
        assertPdfPageSignatures(
            duplicated,
            listOf(
                PdfPageSignature(width = 310, height = 410, rotation = 0),
                PdfPageSignature(width = 320, height = 420, rotation = 0),
                PdfPageSignature(width = 320, height = 420, rotation = 0),
                PdfPageSignature(width = 330, height = 430, rotation = 0)
            )
        )

        val trimmed = editor.deletePages(duplicated, listOf(0, 2), saveAsCopy = true)
        assertPdfPageSignatures(
            trimmed,
            listOf(
                PdfPageSignature(width = 320, height = 420, rotation = 0),
                PdfPageSignature(width = 330, height = 430, rotation = 0)
            )
        )
    }

    @Test
    fun `rotate updates only selected page rotations`() {
        val input = TestPdfFactory.createPdf(
            tmpFolder.newFile("rotate.pdf"),
            listOf(
                TestPdfPage(width = 350f, height = 450f, rotation = 0),
                TestPdfPage(width = 360f, height = 460f, rotation = 90),
                TestPdfPage(width = 370f, height = 470f, rotation = 180)
            )
        )

        val rotated = editor.rotatePages(input, listOf(0, 1), 90, saveAsCopy = true)

        assertPdfPageSignatures(
            rotated,
            listOf(
                PdfPageSignature(width = 350, height = 450, rotation = 90),
                PdfPageSignature(width = 360, height = 460, rotation = 180),
                PdfPageSignature(width = 370, height = 470, rotation = 180)
            )
        )
        assertPdfPageRotations(rotated, listOf(90, 180, 180))
    }

    @Test
    fun `merge and split preserve document boundaries`() {
        val first = TestPdfFactory.createPdf(
            tmpFolder.newFile("merge-a.pdf"),
            listOf(
                TestPdfPage(width = 401f, height = 501f),
                TestPdfPage(width = 402f, height = 502f)
            )
        )
        val second = TestPdfFactory.createPdf(
            tmpFolder.newFile("merge-b.pdf"),
            listOf(TestPdfPage(width = 601f, height = 701f))
        )
        val merged = tmpFolder.newFile("merged.pdf")

        editor.mergePdfs(listOf(first, second), merged)

        assertTrue(merged.exists())
        assertPdfPageSignatures(
            merged,
            listOf(
                PdfPageSignature(width = 401, height = 501, rotation = 0),
                PdfPageSignature(width = 402, height = 502, rotation = 0),
                PdfPageSignature(width = 601, height = 701, rotation = 0)
            )
        )

        val parts = editor.splitPdf(merged, tmpFolder.root, listOf(1))

        assertEquals(2, parts.size)
        assertPdfPageSignatures(
            parts[0],
            listOf(
                PdfPageSignature(width = 401, height = 501, rotation = 0),
                PdfPageSignature(width = 402, height = 502, rotation = 0)
            )
        )
        assertPdfPageSignatures(parts[1], listOf(PdfPageSignature(width = 601, height = 701, rotation = 0)))
    }
}

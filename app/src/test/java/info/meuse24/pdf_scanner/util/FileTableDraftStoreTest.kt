package info.meuse24.pdf_scanner.util

import android.content.Context
import info.meuse24.pdf_scanner.domain.model.CsvDelimiter
import info.meuse24.pdf_scanner.domain.model.TableDraft
import info.meuse24.pdf_scanner.domain.model.TableDraftCell
import info.meuse24.pdf_scanner.domain.model.TableDraftPage
import info.meuse24.pdf_scanner.domain.model.TableDraftRow
import info.meuse24.pdf_scanner.testutil.TestDispatcherProvider
import java.io.File
import java.io.IOException
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class FileTableDraftStoreTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()

    private fun store(): FileTableDraftStore {
        val context = mock(Context::class.java)
        `when`(context.cacheDir).thenReturn(tmpFolder.root)
        return FileTableDraftStore(context, TestDispatcherProvider(testDispatcher))
    }

    private fun draft(scanId: Long = 42L) = TableDraft(
        scanId = scanId,
        savedAtEpochMillis = 1_000L,
        delimiter = CsvDelimiter.SEMICOLON,
        protectFormulas = true,
        pages = listOf(
            TableDraftPage(
                pageIndex = 0,
                selectedForExport = true,
                rows = listOf(
                    TableDraftRow(included = true, cells = listOf("1", "Stuhl", "99,00").map { TableDraftCell(it) }),
                    TableDraftRow(included = false, cells = listOf("2", "Tisch", "199,00").map { TableDraftCell(it) })
                )
            )
        )
    )

    @Test
    fun `Roundtrip liefert identischen Draft zurueck`() = runTest(testDispatcher) {
        val s = store()

        s.saveDraft(draft())
        val loaded = s.loadDraft(42L)

        assertEquals(draft(), loaded)
    }

    @Test
    fun `kein Draft vorhanden liefert null`() = runTest(testDispatcher) {
        assertNull(store().loadDraft(999L))
    }

    @Test
    fun `defekter Draft wird verworfen statt zu crashen`() = runTest(testDispatcher) {
        val s = store()
        File(tmpFolder.root, "table_drafts").apply { mkdirs() }
        File(tmpFolder.root, "table_drafts/7.json").writeText("{ kaputtes json")

        val loaded = s.loadDraft(7L)

        assertNull(loaded)
        assertFalse(File(tmpFolder.root, "table_drafts/7.json").exists())
    }

    @Test
    fun `Lesefehler wird fail-soft behandelt ohne die Datei zu loeschen`() = runTest(testDispatcher) {
        val s = store()
        val draftDir = File(tmpFolder.root, "table_drafts").apply { mkdirs() }
        // Ein Verzeichnis anstelle der erwarteten Datei loest beim Lesen zuverlaessig eine
        // IOException aus (Windows: FileNotFoundException "Access is denied", Linux: "Is a
        // directory") - simuliert einen echten transienten I/O-Fehler ohne die Store-Klasse
        // selbst zu mocken.
        val blockedFile = File(draftDir, "9.json").apply { mkdirs() }

        val loaded = s.loadDraft(9L)

        assertNull(loaded)
        assertTrue("Datei darf bei einem transienten Lesefehler nicht geloescht werden", blockedFile.exists())
    }

    @Test
    fun `saveDraft wirft IOException weiter statt sie zu verschlucken`() = runTest(testDispatcher) {
        // table_drafts liegt hier als Datei (nicht Verzeichnis) im Weg - saveDraft() kann dadurch
        // keine Temp-Datei anlegen. Der Store selbst verschluckt den Fehler nicht (das macht erst
        // der Aufrufer per runCatchingCancellable); er wirft ihn also weiter statt ihn zu ignorieren.
        File(tmpFolder.root, "table_drafts").writeText("blockiert das erwartete Verzeichnis")
        val s = store()

        val result = runCatching { s.saveDraft(draft()) }

        assertTrue(result.exceptionOrNull() is IOException)
    }

    @Test
    fun `deleteDraft entfernt die Datei`() = runTest(testDispatcher) {
        val s = store()
        s.saveDraft(draft())

        s.deleteDraft(42L)

        assertNull(s.loadDraft(42L))
    }

    @Test
    fun `deleteStaleDrafts raeumt nur Dateien aelter als 24h auf`() = runTest(testDispatcher) {
        val s = store()
        s.saveDraft(draft(scanId = 1L))
        s.saveDraft(draft(scanId = 2L))
        val staleFile = File(tmpFolder.root, "table_drafts/1.json")
        staleFile.setLastModified(System.currentTimeMillis() - 2 * 24 * 60 * 60 * 1000)

        s.deleteStaleDrafts()

        assertFalse(staleFile.exists())
        assertEquals(draft(scanId = 2L), s.loadDraft(2L))
    }

    @Test
    fun `speichern ueberschreibt vorhandenen Draft atomar`() = runTest(testDispatcher) {
        val s = store()
        s.saveDraft(draft(scanId = 5L))

        val updated = draft(scanId = 5L).copy(protectFormulas = false)
        s.saveDraft(updated)

        assertEquals(updated, s.loadDraft(5L))
    }
}

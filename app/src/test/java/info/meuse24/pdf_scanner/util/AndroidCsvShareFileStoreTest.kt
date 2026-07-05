package info.meuse24.pdf_scanner.util

import android.content.Context
import java.io.File
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class AndroidCsvShareFileStoreTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private fun store(): AndroidCsvShareFileStore {
        val context = mock(Context::class.java)
        `when`(context.cacheDir).thenReturn(tmpFolder.root)
        return AndroidCsvShareFileStore(context)
    }

    @Test
    fun `schreibt Datei in csv-Unterordner und sanitisiert den Dateinamen`() {
        val target = store().writeShareFile("Rechnung 2024/05!.csv") { it.write("a,b".toByteArray()) }

        assertEquals(File(tmpFolder.root, "csv"), target.parentFile)
        assertEquals("Rechnung_2024_05_.csv", target.name)
        assertEquals("a,b", target.readText())
        assertFalse(File(target.parentFile, "${target.name}.tmp").exists())
    }

    @Test
    fun `ueberschreibt bestehende Datei atomar`() {
        val s = store()
        s.writeShareFile("doc.csv") { it.write("old".toByteArray()) }

        val target = s.writeShareFile("doc.csv") { it.write("new".toByteArray()) }

        assertEquals("new", target.readText())
    }

    @Test
    fun `raeumt veraltete Share-Dateien auf`() {
        val s = store()
        val staleFile = File(File(tmpFolder.root, "csv").apply { mkdirs() }, "stale.csv").apply {
            writeText("stale")
            setLastModified(System.currentTimeMillis() - 2 * 24 * 60 * 60 * 1000)
        }

        s.writeShareFile("fresh.csv") { it.write("fresh".toByteArray()) }

        assertFalse(staleFile.exists())
    }

    @Test
    fun `wirft Exception und raeumt Temp-Datei auf wenn der Writer fehlschlaegt`() {
        val s = store()

        val error = runCatching {
            s.writeShareFile("broken.csv") { error("Schreibfehler") }
        }.exceptionOrNull()

        assertTrue(error != null)
        assertFalse(File(tmpFolder.root, "csv/broken.csv").exists())
        // Keine liegen gebliebene Temp-Datei, unabhängig vom (jetzt eindeutigen) Temp-Namen.
        val remaining = File(tmpFolder.root, "csv").listFiles().orEmpty()
        assertTrue("Verzeichnis sollte leer sein, enthielt aber ${remaining.map { it.name }}", remaining.isEmpty())
    }

    @Test
    fun `wirft IOException wenn eine vorhandene Zieldatei nicht ersetzt werden kann`() {
        val s = store()
        val csvDir = File(tmpFolder.root, "csv").apply { mkdirs() }
        // Ziel existiert bereits als nicht-leeres Verzeichnis -> Files.move(REPLACE_EXISTING)
        // schlaegt fehl.
        val blockingDir = File(csvDir, "locked.csv").apply { mkdirs() }
        File(blockingDir, "inner.txt").writeText("x")

        val error = runCatching {
            s.writeShareFile("locked.csv") { it.write("new".toByteArray()) }
        }.exceptionOrNull()

        assertTrue("erwartet IOException, war $error", error is IOException)
        // Keine liegen gebliebene Temp-Datei; das blockierende Verzeichnis bleibt unangetastet.
        val remaining = csvDir.listFiles().orEmpty().map { it.name }
        assertEquals(listOf("locked.csv"), remaining)
    }

    @Test
    fun `Temp-Dateiname ist eindeutig statt fix abgeleitet`() {
        val s = store()
        val csvDir = File(tmpFolder.root, "csv")
        val observedTempNames = mutableListOf<String>()

        repeat(2) {
            s.writeShareFile("doc.csv") { output ->
                val tempName = csvDir.listFiles().orEmpty().single { it.name != "doc.csv" }.name
                observedTempNames += tempName
                output.write("content".toByteArray())
            }
        }

        // Ein fixer Name wie "doc.csv.tmp" wuerde bei zwei Aufrufen identisch sein - der
        // eindeutige Temp-Name verhindert, dass sich parallele Exporte desselben Dateinamens
        // gegenseitig die Temp-Datei ueberschreiben.
        assertEquals(2, observedTempNames.distinct().size)
    }
}

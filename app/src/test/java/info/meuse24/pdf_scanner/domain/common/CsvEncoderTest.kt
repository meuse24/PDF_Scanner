package info.meuse24.pdf_scanner.domain.common

import info.meuse24.pdf_scanner.domain.model.CsvDelimiter
import info.meuse24.pdf_scanner.domain.model.DelimitedTextDialect
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvEncoderTest {

    private fun encode(
        rows: List<List<String>>,
        dialect: DelimitedTextDialect = DelimitedTextDialect(),
        writeBom: Boolean = false
    ): ByteArrayOutputStream {
        val output = ByteArrayOutputStream()
        encodeDelimitedText(output, rows, dialect, writeBom)
        return output
    }

    private fun ByteArrayOutputStream.asText(): String = toByteArray().toString(Charsets.UTF_8)

    @Test
    fun `Komma-Dialekt trennt Zellen mit Komma`() {
        val text = encode(listOf(listOf("a", "b", "c")), DelimitedTextDialect(CsvDelimiter.COMMA)).asText()

        assertEquals("a,b,c\r\n", text)
    }

    @Test
    fun `Semikolon-Dialekt trennt Zellen mit Semikolon`() {
        val text = encode(listOf(listOf("1", "2")), DelimitedTextDialect(CsvDelimiter.SEMICOLON)).asText()

        assertEquals("1;2\r\n", text)
    }

    @Test
    fun `Tab-Dialekt trennt Zellen mit Tabulator`() {
        val text = encode(listOf(listOf("x", "y")), DelimitedTextDialect(CsvDelimiter.TAB)).asText()

        assertEquals("x\ty\r\n", text)
    }

    @Test
    fun `Zellen mit Trennzeichen Anfuehrungszeichen oder Zeilenumbruch werden gequotet`() {
        val text = encode(
            listOf(listOf("enthält,komma", "hat \"quote\"", "mit\nzeilenumbruch", "mit\rcr"))
        ).asText()

        assertEquals("\"enthält,komma\",\"hat \"\"quote\"\"\",\"mit\nzeilenumbruch\",\"mit\rcr\"\r\n", text)
    }

    @Test
    fun `leere Zellen bleiben leer und ungequotet`() {
        val text = encode(listOf(listOf("a", "", "c"))).asText()

        assertEquals("a,,c\r\n", text)
    }

    @Test
    fun `CRLF als Zeilenende ueber mehrere Zeilen`() {
        val text = encode(listOf(listOf("1"), listOf("2"), listOf("3"))).asText()

        assertEquals("1\r\n2\r\n3\r\n", text)
    }

    @Test
    fun `UTF-8-BOM wird genau einmal am Dateianfang geschrieben`() {
        val output = encode(listOf(listOf("Ä", "ö")), writeBom = true)
        val bytes = output.toByteArray()

        assertArrayEquals(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()), bytes.copyOfRange(0, 3))
        // BOM darf nicht Teil des ersten Zellwerts sein und nicht mehrfach vorkommen.
        val text = bytes.toString(Charsets.UTF_8)
        assertTrue(text.startsWith("﻿"))
        assertEquals(1, text.count { it == '﻿' })
        assertEquals("﻿Ä,ö\r\n", text)
    }

    @Test
    fun `ohne writeBom bleibt kein BOM im Ergebnis`() {
        val bytes = encode(listOf(listOf("a")), writeBom = false).toByteArray()

        assertTrue(bytes.take(3) != listOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
    }

    @Test
    fun `Formel-Schutz aktiv stellt Apostroph vor gefaehrliche Zellinhalte`() {
        val dialect = DelimitedTextDialect(protectFormulas = true)
        val text = encode(listOf(listOf("=SUM(A1)", "+1", "-1", "@cmd", "normal")), dialect).asText()

        assertEquals("'=SUM(A1),'+1,'-1,'@cmd,normal\r\n", text)
    }

    @Test
    fun `Formel-Schutz aktiv schuetzt auch bei fuehrendem Tab CR oder Leerzeichen`() {
        val dialect = DelimitedTextDialect(protectFormulas = true)
        val text = encode(listOf(listOf("\t=SUM(A1)", " +1", "\r-1")), dialect).asText()

        // encodeCell quotet zusaetzlich, weil die Zelle nach dem Apostroph-Praefix ein
        // CR enthaelt (dritte Zelle) bzw. weil Tab/CR im Rohwert stecken.
        assertTrue(text.contains("'\t=SUM(A1)"))
        assertTrue(text.contains("' +1"))
        assertTrue(text.contains("'\r-1"))
    }

    @Test
    fun `Formel-Schutz deaktiviert laesst Zellinhalte unveraendert`() {
        val dialect = DelimitedTextDialect(protectFormulas = false)
        val text = encode(listOf(listOf("=SUM(A1)")), dialect).asText()

        assertEquals("=SUM(A1)\r\n", text)
    }

    @Test
    fun `Dialekt-Metadaten liefern korrekte Dateiendung und MIME-Typ`() {
        assertEquals("csv", DelimitedTextDialect(CsvDelimiter.COMMA).fileExtension)
        assertEquals("text/csv", DelimitedTextDialect(CsvDelimiter.COMMA).mimeType)
        assertEquals("csv", DelimitedTextDialect(CsvDelimiter.SEMICOLON).fileExtension)
        assertEquals("text/csv", DelimitedTextDialect(CsvDelimiter.SEMICOLON).mimeType)
        assertEquals("tsv", DelimitedTextDialect(CsvDelimiter.TAB).fileExtension)
        assertEquals("text/tab-separated-values", DelimitedTextDialect(CsvDelimiter.TAB).mimeType)
    }
}

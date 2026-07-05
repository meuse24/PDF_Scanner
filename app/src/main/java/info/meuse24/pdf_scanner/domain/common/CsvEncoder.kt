package info.meuse24.pdf_scanner.domain.common

import info.meuse24.pdf_scanner.domain.model.DelimitedTextDialect
import java.io.BufferedWriter
import java.io.OutputStream
import java.io.OutputStreamWriter

private val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
private val FORMULA_TRIGGER_CHARS = charArrayOf('=', '+', '-', '@')

/**
 * Schreibt [rows] als CSV/TSV auf [output], gemäß [dialect]. Schreibt direkt auf den
 * OutputStream (row-weise), damit die Speichernutzung nicht von der Tabellengröße abhängt.
 * Schließt [output] absichtlich nicht — Lebenszyklus gehört dem Aufrufer.
 *
 * - Zeilenende: `\r\n`
 * - Encoding: UTF-8, optional mit BOM (einmal pro Datei, als Bytes vor dem ersten Zellwert)
 * - Quoting: Feld in `"` einschließen, wenn es Trennzeichen, `"`, CR oder LF enthält; innere
 *   `"` werden als `""` geschrieben.
 * - Formel-Schutz (wenn [DelimitedTextDialect.protectFormulas] aktiv): beginnt der sichtbare
 *   Zellinhalt — nach Entfernen von führendem Whitespace (Leerzeichen, Tab, CR, LF; einige
 *   Tabellenprogramme ignorieren diesen beim Auswerten, siehe OWASP CSV Injection) — mit `=`,
 *   `+`, `-` oder `@`, wird für den Export ein Apostroph vorangestellt.
 */
fun encodeDelimitedText(
    output: OutputStream,
    rows: List<List<String>>,
    dialect: DelimitedTextDialect,
    writeBom: Boolean = true
) {
    if (writeBom) {
        output.write(UTF8_BOM)
    }
    val writer = BufferedWriter(OutputStreamWriter(output, Charsets.UTF_8))
    rows.forEach { row ->
        writer.write(row.joinToString(dialect.delimiter.char.toString()) { encodeCell(it, dialect) })
        writer.write("\r\n")
    }
    writer.flush()
}

private fun encodeCell(raw: String, dialect: DelimitedTextDialect): String {
    val protected = if (dialect.protectFormulas && needsFormulaProtection(raw)) {
        "'$raw"
    } else {
        raw
    }
    val needsQuoting = protected.any {
        it == dialect.delimiter.char || it == '"' || it == '\r' || it == '\n'
    }
    return if (needsQuoting) {
        "\"" + protected.replace("\"", "\"\"") + "\""
    } else {
        protected
    }
}

private fun needsFormulaProtection(raw: String): Boolean {
    val firstVisible = raw.trimStart().firstOrNull() ?: return false
    return firstVisible in FORMULA_TRIGGER_CHARS
}

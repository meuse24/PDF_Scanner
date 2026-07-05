package info.meuse24.pdf_scanner.domain.gateway

import java.io.File
import java.io.OutputStream

/**
 * Schreibt CSV/TSV-Exportdateien in einen Cache-Ordner für sofortiges Teilen (FileProvider).
 * Implementierungen schreiben atomar (Temp-Datei + Rename) und räumen veraltete Share-Dateien
 * auf, damit ein angehefteter Teilen-Intent nie eine halb geschriebene Datei sieht und der
 * Cache nicht unbegrenzt wächst.
 */
interface CsvShareFileStore {
    fun writeShareFile(displayName: String, writer: (OutputStream) -> Unit): File
}

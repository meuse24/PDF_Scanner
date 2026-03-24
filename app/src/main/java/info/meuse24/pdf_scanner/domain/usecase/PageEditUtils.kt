package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.data.local.ScanRecord
import java.io.File

/**
 * Bestimmt die Thumbnail-Zieldatei nach einer Seitenbearbeitungs-Operation.
 * Bei saveAsCopy wird eine neue Datei neben der Ergebnisdatei angelegt;
 * andernfalls wird das vorhandene Thumbnail wiederverwendet bzw. neu angelegt.
 */
internal fun thumbnailFile(
    record: ScanRecord,
    resultFile: File,
    saveAsCopy: Boolean,
    scansDir: File
): File {
    if (saveAsCopy) {
        return File(scansDir, "${resultFile.nameWithoutExtension}.jpg")
    }
    val existing = record.thumbnailPath?.let(::File)
    return existing ?: File(scansDir, "${record.filename}.jpg")
}

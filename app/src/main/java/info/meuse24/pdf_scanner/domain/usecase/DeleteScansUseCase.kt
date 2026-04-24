package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.repository.DocumentRepository
import java.io.File
import javax.inject.Inject

/**
 * Löscht Dateien + Thumbnails vom Dateisystem und entfernt die DB-Einträge.
 * @return true wenn alle Records vollständig gelöscht wurden; false bei mindestens einem Fehler
 */
class DeleteScansUseCase @Inject constructor(
    private val repository: DocumentRepository
) {
    suspend operator fun invoke(records: List<Document>): Boolean {
        var allDeleted = true
        for (record in records) {
            val file    = File(record.filepath)
            val deleted = !file.exists() || file.delete()
            if (deleted) {
                record.thumbnailPath?.let { path ->
                    val thumbFile = File(path)
                    if (thumbFile.exists()) thumbFile.delete()
                }
                repository.deleteScan(record)
            } else {
                allDeleted = false
            }
        }
        return allDeleted
    }
}


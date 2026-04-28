package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.gateway.StorageProvider
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.repository.DocumentRepository
import java.io.File
import javax.inject.Inject

sealed interface RenameDocumentResult {
    data class Success(val filename: String) : RenameDocumentResult
    data object BlankName : RenameDocumentResult
    data object TargetExists : RenameDocumentResult
    data object RenameFailed : RenameDocumentResult
}

class RenameDocumentUseCase @Inject constructor(
    private val repository: DocumentRepository,
    private val storageProvider: StorageProvider
) {
    suspend operator fun invoke(record: Document, newName: String): RenameDocumentResult {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return RenameDocumentResult.BlankName

        val targetScansDir = storageProvider.scansDir()
        val newFile = File(targetScansDir, "$trimmed.pdf")
        if (newFile.exists()) return RenameDocumentResult.TargetExists

        val oldFile = File(record.filepath)
        if (!oldFile.renameTo(newFile)) return RenameDocumentResult.RenameFailed

        val newThumbPath = record.thumbnailPath?.let { oldThumb ->
            val thumbFile = File(oldThumb)
            val newThumb = File(targetScansDir, "$trimmed.jpg")
            val renamed = !thumbFile.exists() || thumbFile.renameTo(newThumb)
            if (renamed) newThumb.absolutePath else oldThumb
        }

        repository.updateFilenameAndPath(record.id, trimmed, newFile.absolutePath, newThumbPath)
        return RenameDocumentResult.Success(trimmed)
    }
}

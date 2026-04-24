package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.repository.DocumentRepository
import info.meuse24.pdf_scanner.domain.repository.FolderRepository
import info.meuse24.pdf_scanner.domain.repository.TrashDocumentRepository
import java.io.File
import javax.inject.Inject

class RestoreMissingFileException : Exception()

class RestoreScansUseCase @Inject constructor(
    private val repository: TrashDocumentRepository,
    private val documentRepository: DocumentRepository,
    private val folderRepository: FolderRepository
) {
    suspend operator fun invoke(ids: List<Long>) {
        val validIds = ids.filter { it > 0L }
        if (validIds.isEmpty()) return

        val records = repository.getScansByIds(validIds)
        if (records.any { !File(it.filepath).exists() }) {
            throw RestoreMissingFileException()
        }
        val missingFolderIds = records.mapNotNull { it.folderId }
            .distinct()
            .filterNot { folderId -> folderRepository.folderExists(folderId) }
        if (missingFolderIds.isNotEmpty()) {
            val recordsToDetach = records
                .filter { it.folderId in missingFolderIds }
                .map { it.id }
            documentRepository.moveDocumentsToFolder(recordsToDetach, null)
        }
        repository.restore(validIds)
    }
}


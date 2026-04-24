package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.repository.DocumentRepository
import info.meuse24.pdf_scanner.domain.repository.FolderRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class DeleteFolderUseCase @Inject constructor(
    private val folderRepository: FolderRepository,
    private val documentRepository: DocumentRepository
) {
    suspend operator fun invoke(id: Long) {
        require(id > 0L) { "Folder id must be positive" }
        val documentIds = documentRepository.getScansInFolder(id)
            .first()
            .map { it.id }
        if (documentIds.isNotEmpty()) {
            documentRepository.moveDocumentsToFolder(documentIds, null)
        }
        folderRepository.deleteFolder(id)
    }
}

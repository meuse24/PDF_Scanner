package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.repository.DocumentRepository
import info.meuse24.pdf_scanner.domain.repository.FolderRepository
import javax.inject.Inject

class MoveDocumentsUseCase @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val folderRepository: FolderRepository
) {
    suspend operator fun invoke(ids: List<Long>, folderId: Long?) {
        val validIds = ids.filter { it > 0L }.distinct()
        if (validIds.isEmpty()) return
        if (folderId != null && !folderRepository.folderExists(folderId)) {
            throw IllegalArgumentException("Folder $folderId does not exist")
        }
        documentRepository.moveDocumentsToFolder(validIds, folderId)
    }
}

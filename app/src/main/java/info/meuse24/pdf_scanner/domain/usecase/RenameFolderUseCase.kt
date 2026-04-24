package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.repository.FolderRepository
import javax.inject.Inject

class RenameFolderUseCase @Inject constructor(
    private val repository: FolderRepository
) {
    suspend operator fun invoke(id: Long, name: String) {
        require(id > 0L) { "Folder id must be positive" }
        val trimmed = name.trim()
        require(trimmed.isNotBlank()) { "Folder name must not be blank" }
        repository.renameFolder(id, trimmed)
    }
}

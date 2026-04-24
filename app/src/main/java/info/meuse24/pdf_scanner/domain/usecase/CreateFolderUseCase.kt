package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.model.Folder
import info.meuse24.pdf_scanner.domain.repository.FolderRepository
import javax.inject.Inject

class CreateFolderUseCase @Inject constructor(
    private val repository: FolderRepository
) {
    suspend operator fun invoke(name: String): Long {
        val trimmed = name.trim()
        require(trimmed.isNotBlank()) { "Folder name must not be blank" }
        return repository.createFolder(Folder(name = trimmed))
    }
}

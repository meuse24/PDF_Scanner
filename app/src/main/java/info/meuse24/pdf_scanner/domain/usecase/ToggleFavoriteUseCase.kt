package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.repository.DocumentRepository
import javax.inject.Inject

class ToggleFavoriteUseCase @Inject constructor(
    private val repository: DocumentRepository
) {
    suspend operator fun invoke(record: Document) {
        require(record.id > 0L) { "Document must be persisted before toggling favorite" }
        repository.setFavorite(listOf(record.id), !record.isFavorite)
    }
}

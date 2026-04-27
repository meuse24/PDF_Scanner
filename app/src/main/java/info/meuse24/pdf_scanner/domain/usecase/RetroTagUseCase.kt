package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.repository.DocumentRepository
import javax.inject.Inject

class RetroTagUseCase @Inject constructor(
    private val repository: DocumentRepository,
    private val autoTagUseCase: AutoTagUseCase
) {
    suspend operator fun invoke(): Int {
        var tagged = 0
        repository.getAllSearchableWithoutTags().forEach { document ->
            val tags = document.extractedText?.let(autoTagUseCase::extractTags)
            if (tags != null) {
                repository.updateTags(document.id, tags)
                tagged++
            }
        }
        return tagged
    }
}

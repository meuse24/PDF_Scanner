package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.repository.TrashDocumentRepository
import javax.inject.Inject

class TrashScansUseCase @Inject constructor(
    private val repository: TrashDocumentRepository
) {
    suspend operator fun invoke(records: List<Document>): List<Long> {
        if (records.isEmpty()) return emptyList()
        require(records.all { it.id > 0L }) { "TrashScansUseCase requires persisted records" }

        val ids = records.map { it.id }
        repository.softDelete(ids, System.currentTimeMillis())
        return ids
    }
}


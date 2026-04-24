package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.repository.TrashDocumentRepository
import javax.inject.Inject

class PurgeTrashUseCase @Inject constructor(
    private val repository: TrashDocumentRepository,
    private val deleteScansUseCase: DeleteScansUseCase
) {
    suspend fun purgeSelected(records: List<Document>): Boolean =
        deleteScansUseCase(records)

    suspend fun purgeExpired(retentionMillis: Long): Int {
        val threshold = System.currentTimeMillis() - retentionMillis
        val expired = repository.findExpiredTrash(threshold)
        if (expired.isEmpty()) return 0
        deleteScansUseCase(expired)
        return expired.size
    }
}


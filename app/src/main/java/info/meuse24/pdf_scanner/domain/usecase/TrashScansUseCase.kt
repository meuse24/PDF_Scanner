package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.data.repository.TrashRepository
import javax.inject.Inject

class TrashScansUseCase @Inject constructor(
    private val repository: TrashRepository
) {
    suspend operator fun invoke(records: List<ScanRecord>): List<Long> {
        if (records.isEmpty()) return emptyList()
        require(records.all { it.id > 0L }) { "TrashScansUseCase requires persisted records" }

        val ids = records.map { it.id }
        repository.softDelete(ids, System.currentTimeMillis())
        return ids
    }
}

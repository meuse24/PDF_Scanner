package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.data.repository.TrashRepository
import javax.inject.Inject

class TrashScansUseCase @Inject constructor(
    private val repository: TrashRepository
) {
    suspend operator fun invoke(records: List<ScanRecord>): List<Long> {
        val ids = records.map { it.id }.filter { it > 0L }
        if (ids.isEmpty()) return emptyList()
        repository.softDelete(ids, System.currentTimeMillis())
        return ids
    }
}

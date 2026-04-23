package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.data.repository.TrashRepository
import java.io.File
import javax.inject.Inject

class RestoreMissingFileException : Exception()

class RestoreScansUseCase @Inject constructor(
    private val repository: TrashRepository
) {
    suspend operator fun invoke(ids: List<Long>) {
        val validIds = ids.filter { it > 0L }
        if (validIds.isEmpty()) return

        val records = repository.getScansByIds(validIds)
        if (records.any { !File(it.filepath).exists() }) {
            throw RestoreMissingFileException()
        }
        repository.restore(validIds)
    }
}

package info.meuse24.pdf_scanner.domain.usecase

import javax.inject.Inject

class BuildScanSearchQueryUseCase @Inject constructor() {
    operator fun invoke(rawQuery: String): String {
        return Regex("[\\p{L}\\p{N}_]+")
            .findAll(rawQuery.trim())
            .map { match -> "${match.value}*" }
            .joinToString(" ")
    }
}

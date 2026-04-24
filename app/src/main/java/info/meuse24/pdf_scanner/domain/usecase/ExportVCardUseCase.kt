package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.model.BusinessCard
import info.meuse24.pdf_scanner.domain.repository.VCardExportRepository
import info.meuse24.pdf_scanner.domain.service.VCardBuilder
import java.io.File
import javax.inject.Inject

class ExportVCardUseCase @Inject constructor(
    private val vCardBuilder: VCardBuilder,
    private val repository: VCardExportRepository
) {
    suspend operator fun invoke(card: BusinessCard): File {
        val filenameHint = card.fullName
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: card.organization?.trim()?.takeIf { it.isNotBlank() }
            ?: "business-card"
        return repository.export(filenameHint, vCardBuilder.build(card))
    }
}

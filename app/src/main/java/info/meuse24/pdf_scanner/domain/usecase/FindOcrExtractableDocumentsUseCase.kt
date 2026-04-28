package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.gateway.DocumentFileStore
import info.meuse24.pdf_scanner.domain.model.Document
import javax.inject.Inject

class FindOcrExtractableDocumentsUseCase @Inject constructor(
    private val fileStore: DocumentFileStore
) {
    operator fun invoke(records: List<Document>): List<Document> {
        return records.filter { record ->
            fileStore.exists(record.filepath) || record.thumbnailPath != null
        }
    }
}

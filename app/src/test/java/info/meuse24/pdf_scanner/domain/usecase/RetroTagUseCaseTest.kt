package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.repository.DocumentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RetroTagUseCaseTest {

    @Test
    fun `tags only matching searchable documents without tags`() = runTest {
        val repository = FakeRetroTagRepository(
            documents = listOf(
                document(1L, "Rechnungsnummer 2026-001 Bruttobetrag 100,00 EUR"),
                document(2L, "Ein normaler Brief ohne Struktur")
            )
        )
        val useCase = RetroTagUseCase(repository, AutoTagUseCase())

        val taggedCount = useCase()

        assertEquals(1, taggedCount)
        assertEquals(mapOf(1L to "invoice"), repository.updatedTags)
    }
}

private fun document(id: Long, text: String) = Document(
    id = id,
    filename = "doc_$id",
    filepath = "/tmp/doc_$id.pdf",
    timestamp = 0L,
    pageCount = 1,
    fileSize = 0L,
    extractedText = text
)

private class FakeRetroTagRepository(
    private val documents: List<Document>
) : DocumentRepository {
    val updatedTags = linkedMapOf<Long, String>()

    override fun getAllScans(): Flow<List<Document>> = flowOf(emptyList())
    override fun getScansInFolder(folderId: Long): Flow<List<Document>> = flowOf(emptyList())
    override fun getFavoriteScans(): Flow<List<Document>> = flowOf(emptyList())
    override fun searchScansFlow(query: String): Flow<List<Document>> = flowOf(emptyList())
    override suspend fun saveScan(record: Document): Long = record.id
    override suspend fun saveScans(records: List<Document>) = Unit
    override suspend fun deleteScan(record: Document) = Unit
    override suspend fun markSearchable(id: Long, fileSize: Long) = Unit
    override suspend fun markSearchableWithContent(
        id: Long,
        fileSize: Long,
        text: String?,
        tags: String?,
        confidence: Float?,
        language: String?,
        pageTexts: List<String>
    ) = Unit

    override suspend fun updateExtractedTextAndOcrStats(
        id: Long,
        text: String?,
        confidence: Float?,
        language: String?,
        pageTexts: List<String>
    ) = Unit

    override suspend fun updateFileSize(id: Long, fileSize: Long) = Unit
    override suspend fun updatePageMetrics(id: Long, pageCount: Int, fileSize: Long) = Unit
    override suspend fun invalidateAfterAppend(id: Long, fileSize: Long, pageCount: Int) = Unit
    override suspend fun updateFilenameAndPath(
        id: Long,
        filename: String,
        filepath: String,
        thumbnailPath: String?
    ) = Unit

    override suspend fun moveDocumentsToFolder(ids: List<Long>, folderId: Long?) = Unit
    override suspend fun setFavorite(ids: List<Long>, favorite: Boolean) = Unit
    override suspend fun getAllSearchableWithoutTags(): List<Document> = documents
    override suspend fun updateTags(id: Long, tags: String) {
        updatedTags[id] = tags
    }
}

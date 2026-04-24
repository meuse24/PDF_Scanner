package info.meuse24.pdf_scanner.data.repository

import info.meuse24.pdf_scanner.data.local.TrashDao
import info.meuse24.pdf_scanner.data.mapper.toDomain
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.repository.TrashDocumentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrashRepository @Inject constructor(
    private val dao: TrashDao
) : TrashDocumentRepository {
    override fun getTrashedScans(): Flow<List<Document>> = dao.getTrashedScans().map { records ->
        records.map { it.toDomain() }
    }

    override suspend fun getScansByIds(ids: List<Long>): List<Document> = dao.getScansByIds(ids).map { it.toDomain() }

    override suspend fun softDelete(ids: List<Long>, timestamp: Long) = dao.softDelete(ids, timestamp)

    override suspend fun restore(ids: List<Long>) = dao.restore(ids)

    override suspend fun findExpiredTrash(threshold: Long): List<Document> =
        dao.findExpiredTrash(threshold).map { it.toDomain() }
}

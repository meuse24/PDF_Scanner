package info.meuse24.pdf_scanner.data.repository

import info.meuse24.pdf_scanner.data.local.FolderDao
import info.meuse24.pdf_scanner.data.mapper.toDomain
import info.meuse24.pdf_scanner.data.mapper.toEntity
import info.meuse24.pdf_scanner.domain.model.Folder
import info.meuse24.pdf_scanner.domain.repository.FolderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FolderRepositoryImpl @Inject constructor(
    private val dao: FolderDao
) : FolderRepository {
    override fun observeFolders(): Flow<List<Folder>> = dao.observeFolders().map { folders ->
        folders.map { it.toDomain() }
    }

    override suspend fun createFolder(folder: Folder): Long = dao.insert(folder.toEntity())

    override suspend fun renameFolder(id: Long, name: String) = dao.renameFolder(id, name)

    override suspend fun deleteFolder(id: Long) = dao.deleteFolder(id)

    override suspend fun folderExists(id: Long): Boolean = dao.folderExists(id)
}

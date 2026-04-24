package info.meuse24.pdf_scanner.domain.repository

import info.meuse24.pdf_scanner.domain.model.Folder
import kotlinx.coroutines.flow.Flow

interface FolderRepository {
    fun observeFolders(): Flow<List<Folder>>
    suspend fun createFolder(folder: Folder): Long
    suspend fun renameFolder(id: Long, name: String)
    suspend fun deleteFolder(id: Long)
    suspend fun folderExists(id: Long): Boolean
}

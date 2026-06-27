package info.meuse24.pdf_scanner.ui.home

import info.meuse24.pdf_scanner.domain.gateway.StorageProvider
import info.meuse24.pdf_scanner.domain.model.AppSortOrder
import info.meuse24.pdf_scanner.domain.model.AppSettings
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.model.Folder
import info.meuse24.pdf_scanner.domain.repository.AppSettingsRepository
import info.meuse24.pdf_scanner.domain.repository.DocumentRepository
import info.meuse24.pdf_scanner.domain.repository.FolderRepository
import info.meuse24.pdf_scanner.domain.usecase.BuildScanSearchQueryUseCase
import info.meuse24.pdf_scanner.domain.usecase.MoveDocumentsUseCase
import info.meuse24.pdf_scanner.domain.usecase.RenameDocumentResult
import info.meuse24.pdf_scanner.domain.usecase.RenameDocumentUseCase
import info.meuse24.pdf_scanner.domain.usecase.RestoreScansUseCase
import info.meuse24.pdf_scanner.domain.usecase.ToggleFavoriteUseCase
import info.meuse24.pdf_scanner.domain.usecase.TrashScansUseCase
import info.meuse24.pdf_scanner.domain.workflow.MergePdfsWorkflow
import info.meuse24.pdf_scanner.domain.workflow.MergePdfsWorkflowResult
import info.meuse24.pdf_scanner.domain.workflow.WorkflowResult
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

class HomeArchiveCoordinator @Inject constructor(
    private val repository: DocumentRepository,
    private val folderRepository: FolderRepository,
    private val settingsRepository: AppSettingsRepository,
    private val trashScansUseCase: TrashScansUseCase,
    private val restoreScansUseCase: RestoreScansUseCase,
    private val moveDocumentsUseCase: MoveDocumentsUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val renameDocumentUseCase: RenameDocumentUseCase,
    private val buildScanSearchQueryUseCase: BuildScanSearchQueryUseCase,
    private val mergePdfsWorkflow: MergePdfsWorkflow,
    private val storageProvider: StorageProvider,
    private val archiveFilterStore: ArchiveFilterStore
) {
    val settings: StateFlow<AppSettings> = settingsRepository.settings
    val filter: StateFlow<ArchiveFilter> = archiveFilterStore.filter

    fun observeFolders(): Flow<List<Folder>> = folderRepository.observeFolders()

    fun allScansForList(): Flow<List<Document>> = repository.getAllScansForList()

    fun listScans(filter: ArchiveFilter): Flow<List<Document>> = when (filter) {
        ArchiveFilter.AllDocuments -> repository.getAllScansForList()
        ArchiveFilter.Favorites -> repository.getFavoriteScansForList()
        is ArchiveFilter.Folder -> repository.getScansInFolderForList(filter.folderId)
        is ArchiveFilter.Tag -> repository.getScansWithTagForList(filter.key)
    }

    fun searchScans(filter: ArchiveFilter, query: String): Flow<List<Document>> = when (filter) {
        ArchiveFilter.AllDocuments -> repository.searchScansForList(query)
        ArchiveFilter.Favorites -> repository.searchFavoriteScansForList(query)
        is ArchiveFilter.Folder -> repository.searchScansInFolderForList(query, filter.folderId)
        is ArchiveFilter.Tag -> repository.searchScansWithTagForList(query, filter.key)
    }

    fun buildSearchQuery(rawQuery: String): String = buildScanSearchQueryUseCase(rawQuery)

    suspend fun trash(records: List<Document>): List<Long> = trashScansUseCase(records)

    suspend fun restore(ids: List<Long>) {
        restoreScansUseCase(ids)
    }

    suspend fun move(ids: List<Long>, folderId: Long?) {
        moveDocumentsUseCase(ids, folderId)
    }

    suspend fun toggleFavorite(record: Document) {
        toggleFavoriteUseCase(record)
    }

    suspend fun rename(record: Document, newName: String): RenameDocumentResult =
        renameDocumentUseCase(record, newName)

    suspend fun getDocuments(ids: List<Long>): List<Document> = repository.getScansByIds(ids)

    suspend fun merge(
        records: List<Document>,
        outputFilename: String
    ): WorkflowResult<MergePdfsWorkflowResult> =
        mergePdfsWorkflow(records, outputFilename, storageProvider.scansDir())

    fun updateSortOrder(sortOrder: AppSortOrder) {
        settingsRepository.updateDefaultSortOrder(sortOrder)
    }

    fun showAllDocuments() = archiveFilterStore.showAllDocuments()

    fun showFavorites() = archiveFilterStore.showFavorites()

    fun showFolder(folderId: Long) = archiveFilterStore.showFolder(folderId)

    fun showTag(tagKey: String) = archiveFilterStore.showTag(tagKey)
}

package info.meuse24.pdf_scanner.ui.home

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ArchiveFilter {
    data object AllDocuments : ArchiveFilter
    data object Favorites : ArchiveFilter
    data class Folder(val folderId: Long) : ArchiveFilter
}

@Singleton
class ArchiveFilterStore @Inject constructor() {
    private val _filter = MutableStateFlow<ArchiveFilter>(ArchiveFilter.AllDocuments)
    val filter: StateFlow<ArchiveFilter> = _filter.asStateFlow()

    fun showAllDocuments() {
        _filter.value = ArchiveFilter.AllDocuments
    }

    fun showFavorites() {
        _filter.value = ArchiveFilter.Favorites
    }

    fun showFolder(folderId: Long) {
        _filter.value = ArchiveFilter.Folder(folderId)
    }
}

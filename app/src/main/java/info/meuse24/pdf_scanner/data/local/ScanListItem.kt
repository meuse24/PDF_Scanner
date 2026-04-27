package info.meuse24.pdf_scanner.data.local

import androidx.room.ColumnInfo

data class ScanListItem(
    val id: Long,
    val filename: String,
    val filepath: String,
    val timestamp: Long,
    val pageCount: Int,
    val fileSize: Long,
    @ColumnInfo(name = "thumbnail_path") val thumbnailPath: String? = null,
    @ColumnInfo(name = "is_searchable") val isSearchable: Boolean = false,
    @ColumnInfo(name = "is_encrypted") val isEncrypted: Boolean = false,
    val tags: String? = null,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    @ColumnInfo(name = "folder_id") val folderId: Long? = null,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false
)

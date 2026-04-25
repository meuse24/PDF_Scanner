package info.meuse24.pdf_scanner.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "scan_records",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folder_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("folder_id"),
        Index("deleted_at"),
        Index("timestamp"),
        Index("is_favorite")
    ]
)
data class ScanRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val filename: String,
    val filepath: String,
    val timestamp: Long,
    val pageCount: Int,
    val fileSize: Long,
    @ColumnInfo(name = "thumbnail_path") val thumbnailPath: String? = null,
    @ColumnInfo(name = "is_searchable", defaultValue = "0") val isSearchable: Boolean = false,
    @ColumnInfo(name = "is_encrypted", defaultValue = "0") val isEncrypted: Boolean = false,
    @ColumnInfo(name = "extracted_text") val extractedText: String? = null,
    val tags: String? = null,
    @ColumnInfo(name = "ocr_confidence") val ocrConfidence: Float? = null,
    @ColumnInfo(name = "ocr_language") val ocrLanguage: String? = null,
    @ColumnInfo(name = "ocr_page_text_json") val ocrPageTextJson: String? = null,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    @ColumnInfo(name = "folder_id") val folderId: Long? = null,
    @ColumnInfo(name = "is_favorite", defaultValue = "0") val isFavorite: Boolean = false
)

package info.meuse24.pdf_scanner.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_records")
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
    val tags: String? = null
)

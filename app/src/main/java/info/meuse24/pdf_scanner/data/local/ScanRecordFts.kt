package info.meuse24.pdf_scanner.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4

@Fts4(contentEntity = ScanRecord::class)
@Entity(tableName = "scan_records_fts")
data class ScanRecordFts(
    val filename: String,
    @ColumnInfo(name = "extracted_text") val extractedText: String?
)

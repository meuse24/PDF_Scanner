package info.meuse24.pdf_scanner.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanDao {
    @Query("SELECT * FROM scan_records WHERE deleted_at IS NULL ORDER BY timestamp DESC")
    fun getAllScans(): Flow<List<ScanRecord>>

    @Query(
        """
        SELECT * FROM scan_records
        WHERE deleted_at IS NULL
          AND id IN (SELECT docid FROM scan_records_fts WHERE scan_records_fts MATCH :query)
        ORDER BY timestamp DESC
        """
    )
    fun searchScansFlow(query: String): Flow<List<ScanRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: ScanRecord): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<ScanRecord>)

    @Delete
    suspend fun delete(record: ScanRecord)

    @Query(
        """
        UPDATE scan_records
        SET is_searchable = 1,
            fileSize = :fileSize,
            extracted_text = :text,
            tags = :tags,
            ocr_confidence = :confidence,
            ocr_language = :language,
            ocr_page_text_json = :pageTextJson
        WHERE id = :id
        """
    )
    suspend fun markSearchableWithContent(
        id: Long,
        fileSize: Long,
        text: String?,
        tags: String?,
        confidence: Float?,
        language: String?,
        pageTextJson: String?
    )

    @Query("UPDATE scan_records SET is_searchable = 1, fileSize = :fileSize WHERE id = :id")
    suspend fun markSearchable(id: Long, fileSize: Long)

    @Query("UPDATE scan_records SET fileSize = :fileSize WHERE id = :id")
    suspend fun updateFileSize(id: Long, fileSize: Long)

    @Query("UPDATE scan_records SET pageCount = :pageCount, fileSize = :fileSize WHERE id = :id")
    suspend fun updatePageMetrics(id: Long, pageCount: Int, fileSize: Long)

    @Query(
        """
        UPDATE scan_records
        SET extracted_text = :text,
            ocr_confidence = :confidence,
            ocr_language = :language,
            ocr_page_text_json = :pageTextJson
        WHERE id = :id
        """
    )
    suspend fun updateExtractedTextAndOcrStats(
        id: Long,
        text: String?,
        confidence: Float?,
        language: String?,
        pageTextJson: String?
    )

    @Query(
        """
        UPDATE scan_records
        SET is_searchable = 0,
            extracted_text = NULL,
            tags = NULL,
            ocr_confidence = NULL,
            ocr_language = NULL,
            ocr_page_text_json = NULL,
            pageCount = :pageCount,
            fileSize = :fileSize
        WHERE id = :id
        """
    )
    suspend fun invalidateAfterAppend(id: Long, fileSize: Long, pageCount: Int)

    @Query("UPDATE scan_records SET filename = :filename, filepath = :filepath, thumbnail_path = :thumbnailPath WHERE id = :id")
    suspend fun updateFilenameAndPath(id: Long, filename: String, filepath: String, thumbnailPath: String?)
}

package info.meuse24.pdf_scanner.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanDao {
    @Query("SELECT * FROM scan_records ORDER BY timestamp DESC")
    fun getAllScans(): Flow<List<ScanRecord>>

    @Query("SELECT * FROM scan_records WHERE id IN (SELECT docid FROM scan_records_fts WHERE scan_records_fts MATCH :query) ORDER BY timestamp DESC")
    fun searchScansFlow(query: String): Flow<List<ScanRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: ScanRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<ScanRecord>)

    @Delete
    suspend fun delete(record: ScanRecord)

    @Query("UPDATE scan_records SET is_searchable = 1, fileSize = :fileSize, extracted_text = :text, tags = :tags WHERE id = :id")
    suspend fun markSearchableWithContent(id: Long, fileSize: Long, text: String?, tags: String?)

    @Query("UPDATE scan_records SET is_searchable = 1, fileSize = :fileSize WHERE id = :id")
    suspend fun markSearchable(id: Long, fileSize: Long)

    @Query("UPDATE scan_records SET fileSize = :fileSize WHERE id = :id")
    suspend fun updateFileSize(id: Long, fileSize: Long)

    @Query("UPDATE scan_records SET pageCount = :pageCount, fileSize = :fileSize WHERE id = :id")
    suspend fun updatePageMetrics(id: Long, pageCount: Int, fileSize: Long)
}

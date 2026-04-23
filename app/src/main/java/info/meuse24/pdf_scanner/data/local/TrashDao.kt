package info.meuse24.pdf_scanner.data.local

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrashDao {
    @Query("SELECT * FROM scan_records WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC")
    fun getTrashedScans(): Flow<List<ScanRecord>>

    @Query("SELECT * FROM scan_records WHERE id IN (:ids)")
    suspend fun getScansByIds(ids: List<Long>): List<ScanRecord>

    @Query("UPDATE scan_records SET deleted_at = :timestamp WHERE id IN (:ids) AND deleted_at IS NULL")
    suspend fun softDelete(ids: List<Long>, timestamp: Long)

    @Query("UPDATE scan_records SET deleted_at = NULL WHERE id IN (:ids) AND deleted_at IS NOT NULL")
    suspend fun restore(ids: List<Long>)

    @Query("SELECT * FROM scan_records WHERE deleted_at IS NOT NULL AND deleted_at < :threshold")
    suspend fun findExpiredTrash(threshold: Long): List<ScanRecord>
}

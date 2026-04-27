package info.meuse24.pdf_scanner.data.local

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanListDao {
    @Query(
        """
        SELECT id, filename, filepath, timestamp, pageCount, fileSize,
               thumbnail_path, is_searchable, is_encrypted, extracted_text, tags, deleted_at,
               folder_id, is_favorite
        FROM scan_records
        WHERE deleted_at IS NULL
        ORDER BY timestamp DESC
        """
    )
    fun getAllScanListItems(): Flow<List<ScanListItem>>

    @Query(
        """
        SELECT id, filename, filepath, timestamp, pageCount, fileSize,
               thumbnail_path, is_searchable, is_encrypted, extracted_text, tags, deleted_at,
               folder_id, is_favorite
        FROM scan_records
        WHERE deleted_at IS NULL AND folder_id IS :folderId
        ORDER BY timestamp DESC
        """
    )
    fun getScanListItemsInFolder(folderId: Long): Flow<List<ScanListItem>>

    @Query(
        """
        SELECT id, filename, filepath, timestamp, pageCount, fileSize,
               thumbnail_path, is_searchable, is_encrypted, extracted_text, tags, deleted_at,
               folder_id, is_favorite
        FROM scan_records
        WHERE deleted_at IS NULL AND is_favorite = 1
        ORDER BY timestamp DESC
        """
    )
    fun getFavoriteScanListItems(): Flow<List<ScanListItem>>

    @Query(
        """
        SELECT id, filename, filepath, timestamp, pageCount, fileSize,
               thumbnail_path, is_searchable, is_encrypted, extracted_text, tags, deleted_at,
               folder_id, is_favorite
        FROM scan_records
        WHERE deleted_at IS NULL
          AND (',' || tags || ',') LIKE '%,' || :tagKey || ',%'
        ORDER BY timestamp DESC
        """
    )
    fun getScanListItemsWithTag(tagKey: String): Flow<List<ScanListItem>>

    @Query(
        """
        SELECT id, filename, filepath, timestamp, pageCount, fileSize,
               thumbnail_path, is_searchable, is_encrypted, extracted_text, tags, deleted_at,
               folder_id, is_favorite
        FROM scan_records
        WHERE deleted_at IS NULL
          AND id IN (SELECT docid FROM scan_records_fts WHERE scan_records_fts MATCH :query)
        ORDER BY timestamp DESC
        """
    )
    fun searchScanListItems(query: String): Flow<List<ScanListItem>>

    @Query(
        """
        SELECT id, filename, filepath, timestamp, pageCount, fileSize,
               thumbnail_path, is_searchable, is_encrypted, extracted_text, tags, deleted_at,
               folder_id, is_favorite
        FROM scan_records
        WHERE deleted_at IS NULL
          AND folder_id IS :folderId
          AND id IN (SELECT docid FROM scan_records_fts WHERE scan_records_fts MATCH :query)
        ORDER BY timestamp DESC
        """
    )
    fun searchScanListItemsInFolder(query: String, folderId: Long): Flow<List<ScanListItem>>

    @Query(
        """
        SELECT id, filename, filepath, timestamp, pageCount, fileSize,
               thumbnail_path, is_searchable, is_encrypted, extracted_text, tags, deleted_at,
               folder_id, is_favorite
        FROM scan_records
        WHERE deleted_at IS NULL
          AND is_favorite = 1
          AND id IN (SELECT docid FROM scan_records_fts WHERE scan_records_fts MATCH :query)
        ORDER BY timestamp DESC
        """
    )
    fun searchFavoriteScanListItems(query: String): Flow<List<ScanListItem>>
}

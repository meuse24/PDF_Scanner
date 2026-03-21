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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: ScanRecord)

    @Delete
    suspend fun delete(record: ScanRecord)
}

package info.meuse24.pdf_scanner.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ScanRecord::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scanDao(): ScanDao
}

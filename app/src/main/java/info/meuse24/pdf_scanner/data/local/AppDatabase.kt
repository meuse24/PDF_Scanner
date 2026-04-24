package info.meuse24.pdf_scanner.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ScanRecord::class, ScanRecordFts::class, FolderEntity::class], version = 8, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scanDao(): ScanDao
    abstract fun trashDao(): TrashDao
    abstract fun folderDao(): FolderDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE scan_records ADD COLUMN thumbnail_path TEXT")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE scan_records ADD COLUMN is_searchable INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE scan_records ADD COLUMN is_encrypted INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // New columns on main table
                db.execSQL("ALTER TABLE scan_records ADD COLUMN extracted_text TEXT")
                db.execSQL("ALTER TABLE scan_records ADD COLUMN tags TEXT")
                // FTS4 virtual content table
                db.execSQL(
                    "CREATE VIRTUAL TABLE IF NOT EXISTS `scan_records_fts` " +
                    "USING fts4(content=`scan_records`, `filename`, `extracted_text`)"
                )
                // Sync triggers (names must match what Room generates)
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS `scan_records_fts_BEFORE_UPDATE` " +
                    "BEFORE UPDATE ON `scan_records` BEGIN " +
                    "DELETE FROM `scan_records_fts` WHERE docid=OLD.`rowid`; END"
                )
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS `scan_records_fts_BEFORE_DELETE` " +
                    "BEFORE DELETE ON `scan_records` BEGIN " +
                    "DELETE FROM `scan_records_fts` WHERE docid=OLD.`rowid`; END"
                )
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS `scan_records_fts_AFTER_UPDATE` " +
                    "AFTER UPDATE ON `scan_records` BEGIN " +
                    "INSERT INTO `scan_records_fts`(docid, `filename`, `extracted_text`) " +
                    "VALUES (NEW.`rowid`, NEW.`filename`, NEW.`extracted_text`); END"
                )
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS `scan_records_fts_AFTER_INSERT` " +
                    "AFTER INSERT ON `scan_records` BEGIN " +
                    "INSERT INTO `scan_records_fts`(docid, `filename`, `extracted_text`) " +
                    "VALUES (NEW.`rowid`, NEW.`filename`, NEW.`extracted_text`); END"
                )
                // Populate FTS from existing records
                db.execSQL(
                    "INSERT INTO `scan_records_fts`(docid, `filename`, `extracted_text`) " +
                    "SELECT rowid, filename, extracted_text FROM scan_records"
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE scan_records ADD COLUMN deleted_at INTEGER")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE scan_records ADD COLUMN ocr_confidence REAL")
                db.execSQL("ALTER TABLE scan_records ADD COLUMN ocr_language TEXT")
                db.execSQL("ALTER TABLE scan_records ADD COLUMN ocr_page_text_json TEXT")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS folders (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL,
                        color_argb INTEGER,
                        created_at INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("ALTER TABLE scan_records ADD COLUMN folder_id INTEGER")
                db.execSQL("ALTER TABLE scan_records ADD COLUMN is_favorite INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_scan_records_folder_id ON scan_records(folder_id)")
            }
        }
    }
}

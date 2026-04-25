package info.meuse24.pdf_scanner.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ScanRecord::class, ScanRecordFts::class, FolderEntity::class], version = 9, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scanDao(): ScanDao
    abstract fun scanListDao(): ScanListDao
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

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TRIGGER IF EXISTS `scan_records_fts_BEFORE_UPDATE`")
                db.execSQL("DROP TRIGGER IF EXISTS `scan_records_fts_BEFORE_DELETE`")
                db.execSQL("DROP TRIGGER IF EXISTS `scan_records_fts_AFTER_UPDATE`")
                db.execSQL("DROP TRIGGER IF EXISTS `scan_records_fts_AFTER_INSERT`")
                db.execSQL("DROP TRIGGER IF EXISTS `room_fts_content_sync_scan_records_fts_BEFORE_UPDATE`")
                db.execSQL("DROP TRIGGER IF EXISTS `room_fts_content_sync_scan_records_fts_BEFORE_DELETE`")
                db.execSQL("DROP TRIGGER IF EXISTS `room_fts_content_sync_scan_records_fts_AFTER_UPDATE`")
                db.execSQL("DROP TRIGGER IF EXISTS `room_fts_content_sync_scan_records_fts_AFTER_INSERT`")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS scan_records_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        filename TEXT NOT NULL,
                        filepath TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        pageCount INTEGER NOT NULL,
                        fileSize INTEGER NOT NULL,
                        thumbnail_path TEXT,
                        is_searchable INTEGER NOT NULL DEFAULT 0,
                        is_encrypted INTEGER NOT NULL DEFAULT 0,
                        extracted_text TEXT,
                        tags TEXT,
                        ocr_confidence REAL,
                        ocr_language TEXT,
                        ocr_page_text_json TEXT,
                        deleted_at INTEGER,
                        folder_id INTEGER,
                        is_favorite INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(folder_id) REFERENCES folders(id) ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO scan_records_new (
                        id, filename, filepath, timestamp, pageCount, fileSize,
                        thumbnail_path, is_searchable, is_encrypted, extracted_text, tags,
                        ocr_confidence, ocr_language, ocr_page_text_json, deleted_at,
                        folder_id, is_favorite
                    )
                    SELECT
                        scan_records.id, filename, filepath, timestamp, pageCount, fileSize,
                        thumbnail_path, is_searchable, is_encrypted, extracted_text, tags,
                        ocr_confidence, ocr_language, ocr_page_text_json, deleted_at,
                        CASE
                            WHEN folder_id IS NULL THEN NULL
                            WHEN folders.id IS NULL THEN NULL
                            ELSE folder_id
                        END AS folder_id,
                        is_favorite
                    FROM scan_records
                    LEFT JOIN folders ON folders.id = scan_records.folder_id
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE scan_records")
                db.execSQL("ALTER TABLE scan_records_new RENAME TO scan_records")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_scan_records_folder_id ON scan_records(folder_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_scan_records_deleted_at ON scan_records(deleted_at)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_scan_records_timestamp ON scan_records(timestamp)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_scan_records_is_favorite ON scan_records(is_favorite)")
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS `room_fts_content_sync_scan_records_fts_BEFORE_UPDATE` " +
                        "BEFORE UPDATE ON `scan_records` BEGIN " +
                        "DELETE FROM `scan_records_fts` WHERE docid=OLD.`rowid`; END"
                )
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS `room_fts_content_sync_scan_records_fts_BEFORE_DELETE` " +
                        "BEFORE DELETE ON `scan_records` BEGIN " +
                        "DELETE FROM `scan_records_fts` WHERE docid=OLD.`rowid`; END"
                )
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS `room_fts_content_sync_scan_records_fts_AFTER_UPDATE` " +
                        "AFTER UPDATE ON `scan_records` BEGIN " +
                        "INSERT INTO `scan_records_fts`(docid, `filename`, `extracted_text`) " +
                        "VALUES (NEW.`rowid`, NEW.`filename`, NEW.`extracted_text`); END"
                )
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS `room_fts_content_sync_scan_records_fts_AFTER_INSERT` " +
                        "AFTER INSERT ON `scan_records` BEGIN " +
                        "INSERT INTO `scan_records_fts`(docid, `filename`, `extracted_text`) " +
                        "VALUES (NEW.`rowid`, NEW.`filename`, NEW.`extracted_text`); END"
                )
                db.execSQL("DELETE FROM scan_records_fts")
                db.execSQL(
                    "INSERT INTO `scan_records_fts`(docid, `filename`, `extracted_text`) " +
                        "SELECT rowid, filename, extracted_text FROM scan_records"
                )
            }
        }
    }
}

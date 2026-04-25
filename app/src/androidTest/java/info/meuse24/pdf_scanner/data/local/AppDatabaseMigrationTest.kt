package info.meuse24.pdf_scanner.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migration5To6AddsNullableDeletedAt() {
        helper.createDatabase(TEST_DB, 5).apply {
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS scan_records (
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
                    tags TEXT
                )
                """.trimIndent()
            )
            execSQL(
                """
                CREATE VIRTUAL TABLE IF NOT EXISTS scan_records_fts
                USING fts4(content=scan_records, filename, extracted_text)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO scan_records (
                    id, filename, filepath, timestamp, pageCount, fileSize,
                    thumbnail_path, is_searchable, is_encrypted, extracted_text, tags
                ) VALUES (1, 'scan', '/tmp/scan.pdf', 10, 1, 20, NULL, 0, 0, NULL, NULL)
                """.trimIndent()
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            6,
            true,
            AppDatabase.MIGRATION_5_6
        )

        db.query("SELECT deleted_at FROM scan_records WHERE id = 1").use { cursor ->
            cursor.moveToFirst()
            assertEquals(true, cursor.isNull(0))
        }
    }

    @Test
    fun migration6To7AddsOcrColumns() {
        helper.createDatabase(TEST_DB, 6).apply {
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS scan_records (
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
                    deleted_at INTEGER
                )
                """.trimIndent()
            )
            execSQL(
                """
                CREATE VIRTUAL TABLE IF NOT EXISTS scan_records_fts
                USING fts4(content=scan_records, filename, extracted_text)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO scan_records (
                    id, filename, filepath, timestamp, pageCount, fileSize,
                    thumbnail_path, is_searchable, is_encrypted, extracted_text, tags, deleted_at
                ) VALUES (1, 'scan', '/tmp/scan.pdf', 10, 1, 20, NULL, 1, 0, 'text', 'invoice', NULL)
                """.trimIndent()
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            7,
            true,
            AppDatabase.MIGRATION_6_7
        )

        db.query(
            "SELECT ocr_confidence, ocr_language, ocr_page_text_json FROM scan_records WHERE id = 1"
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals(true, cursor.isNull(0))
            assertNull(cursor.getString(1))
            assertNull(cursor.getString(2))
        }
    }

    @Test
    fun migration7To8AddsFoldersAndFavoriteColumns() {
        helper.createDatabase(TEST_DB, 7).apply {
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS scan_records (
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
                    deleted_at INTEGER
                )
                """.trimIndent()
            )
            execSQL(
                """
                CREATE VIRTUAL TABLE IF NOT EXISTS scan_records_fts
                USING fts4(content=scan_records, filename, extracted_text)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO scan_records (
                    id, filename, filepath, timestamp, pageCount, fileSize,
                    thumbnail_path, is_searchable, is_encrypted, extracted_text, tags,
                    ocr_confidence, ocr_language, ocr_page_text_json, deleted_at
                ) VALUES (1, 'scan', '/tmp/scan.pdf', 10, 1, 20, NULL, 1, 0, 'text', NULL, NULL, NULL, NULL, NULL)
                """.trimIndent()
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            8,
            true,
            AppDatabase.MIGRATION_7_8
        )

        db.query(
            "SELECT folder_id, is_favorite FROM scan_records WHERE id = 1"
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals(true, cursor.isNull(0))
            assertEquals(0, cursor.getInt(1))
        }
    }

    @Test
    fun migration8To9AddsForeignKeyAndListIndexes() {
        helper.createDatabase(TEST_DB, 8).apply {
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS folders (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    color_argb INTEGER,
                    created_at INTEGER NOT NULL
                )
                """.trimIndent()
            )
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS scan_records (
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
                    is_favorite INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
            execSQL("CREATE INDEX IF NOT EXISTS index_scan_records_folder_id ON scan_records(folder_id)")
            execSQL(
                """
                CREATE VIRTUAL TABLE IF NOT EXISTS scan_records_fts
                USING fts4(content=scan_records, filename, extracted_text)
                """.trimIndent()
            )
            execSQL("INSERT INTO folders (id, name, color_argb, created_at) VALUES (10, 'Work', NULL, 1)")
            execSQL(
                """
                INSERT INTO scan_records (
                    id, filename, filepath, timestamp, pageCount, fileSize,
                    thumbnail_path, is_searchable, is_encrypted, extracted_text, tags,
                    ocr_confidence, ocr_language, ocr_page_text_json, deleted_at,
                    folder_id, is_favorite
                ) VALUES
                    (1, 'alpha', '/tmp/alpha.pdf', 20, 1, 100, NULL, 1, 0, 'alpha text', NULL, 0.9, 'en', NULL, NULL, 10, 1),
                    (2, 'orphan', '/tmp/orphan.pdf', 10, 1, 100, NULL, 0, 0, 'orphan text', NULL, NULL, NULL, NULL, NULL, 99, 0)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO scan_records_fts(docid, filename, extracted_text)
                SELECT rowid, filename, extracted_text FROM scan_records
                """.trimIndent()
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            9,
            true,
            AppDatabase.MIGRATION_8_9
        )

        db.query("SELECT folder_id FROM scan_records WHERE id = 2").use { cursor ->
            cursor.moveToFirst()
            assertTrue(cursor.isNull(0))
        }

        val indexNames = mutableSetOf<String>()
        db.query("PRAGMA index_list('scan_records')").use { cursor ->
            while (cursor.moveToNext()) {
                indexNames += cursor.getString(cursor.getColumnIndexOrThrow("name"))
            }
        }
        assertTrue("deleted_at index missing", "index_scan_records_deleted_at" in indexNames)
        assertTrue("timestamp index missing", "index_scan_records_timestamp" in indexNames)
        assertTrue("is_favorite index missing", "index_scan_records_is_favorite" in indexNames)

        db.execSQL("PRAGMA foreign_keys=ON")
        db.execSQL("DELETE FROM folders WHERE id = 10")
        db.query("SELECT folder_id FROM scan_records WHERE id = 1").use { cursor ->
            cursor.moveToFirst()
            assertTrue(cursor.isNull(0))
        }

        db.query(
            """
            SELECT id FROM scan_records
            WHERE id IN (
                SELECT docid FROM scan_records_fts WHERE scan_records_fts MATCH 'alpha'
            )
            """.trimIndent()
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1L, cursor.getLong(0))
        }
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}

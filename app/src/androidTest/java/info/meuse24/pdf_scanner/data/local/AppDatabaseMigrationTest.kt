package info.meuse24.pdf_scanner.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    private companion object {
        const val TEST_DB = "migration-test"
    }
}

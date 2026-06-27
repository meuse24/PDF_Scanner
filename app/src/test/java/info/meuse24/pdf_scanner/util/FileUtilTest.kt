package info.meuse24.pdf_scanner.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import info.meuse24.pdf_scanner.testutil.FakeResourceProvider
import info.meuse24.pdf_scanner.testutil.TestStorageProvider
import java.io.ByteArrayInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class FileUtilTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    @Test
    fun `savePdf sanitizes external filename and keeps file in scans directory`() {
        val uri = mock(Uri::class.java)
        val resolver = mock(ContentResolver::class.java)
        val context = mock(Context::class.java)
        val bytes = byteArrayOf(1, 2, 3, 4)
        `when`(context.contentResolver).thenReturn(resolver)
        `when`(resolver.openInputStream(uri)).thenReturn(ByteArrayInputStream(bytes))
        val storageProvider = TestStorageProvider(tmpFolder.root)
        val fileUtil = FileUtil(context, storageProvider, FakeResourceProvider())

        val result = fileUtil.savePdf(uri, "../../outside")

        assertEquals(storageProvider.scansDir().canonicalFile, result.parentFile?.canonicalFile)
        assertEquals("_.._outside.pdf", result.name)
        assertArrayEquals(bytes, result.readBytes())
    }
}

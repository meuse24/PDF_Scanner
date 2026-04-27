package info.meuse24.pdf_scanner.ui.entry

import android.content.ClipData
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock

class AppEntryActionCodecTest {

    @Test
    fun `parses shared pdf from extra stream single uri`() {
        val uri = mockUri("shared_document.pdf")
        val intent = mockSendIntent(type = "application/pdf")
        stubSingleExtraStream(intent, uri)

        val action = AppEntryActionCodec.fromIntent(intent)

        assertEquals(AppEntryAction.SharePdf(uri), action)
    }

    @Test
    fun `parses multiple shared images from extra stream array list`() {
        val first = mockUri("photo_1.jpg")
        val second = mockUri("photo_2.png")
        val intent = mockSendMultipleIntent(type = "image/*")
        stubArrayListExtraStream(intent, arrayListOf(first, second))

        val action = AppEntryActionCodec.fromIntent(intent)

        assertEquals(AppEntryAction.ShareImages(listOf(first, second)), action)
    }

    @Test
    fun `parses shared pdf from clip data when extra stream is missing`() {
        val uri = mockUri("shared_document.pdf")
        val mockedClipData = mockClipData(uris = arrayOf(uri), mimeTypes = arrayOf("application/pdf"))
        val intent = mockSendIntent(type = "application/pdf").apply {
            `when`(clipData).thenReturn(mockedClipData)
        }

        val action = AppEntryActionCodec.fromIntent(intent)

        assertEquals(AppEntryAction.SharePdf(uri), action)
    }

    @Test
    fun `parses multiple shared images from clip data without mime type`() {
        val first = mockUri("photo_1.jpg")
        val second = mockUri("photo_2.png")
        val mockedClipData = mockClipData(uris = arrayOf(first, second))
        val intent = mockSendMultipleIntent(type = null).apply {
            `when`(clipData).thenReturn(mockedClipData)
        }

        val action = AppEntryActionCodec.fromIntent(intent)

        assertEquals(AppEntryAction.ShareImages(listOf(first, second)), action)
    }

    @Test
    fun `parses open with pdf via extension when type is absent`() {
        val uri = mockUri("invoice_2026.pdf")
        val intent = mockViewIntent(type = null, data = uri)

        val action = AppEntryActionCodec.fromIntent(intent)

        assertEquals(AppEntryAction.SharePdf(uri), action)
    }

    @Test
    fun `parses open with image via extension when type is absent`() {
        val uri = mockUri("camera_import.heic")
        val intent = mockViewIntent(type = null, data = uri)

        val action = AppEntryActionCodec.fromIntent(intent)

        assertEquals(AppEntryAction.ShareImages(listOf(uri)), action)
    }

    @Test
    fun `parses content uri using content resolver mime type when uri has no extension`() {
        val uri = mockUri("document%3A5678")
        val context = mockContextWithResolvedType(uri, "application/pdf")
        val intent = mockViewIntent(type = null, data = uri)

        val action = AppEntryActionCodec.fromIntent(context, intent)

        assertEquals(AppEntryAction.SharePdf(uri), action)
    }

    @Test
    fun `accepts application x pdf mime type variant`() {
        val uri = mockUri("legacy_provider_entry")
        val intent = mockSendIntent(type = "application/x-pdf")
        stubSingleExtraStream(intent, uri)

        val action = AppEntryActionCodec.fromIntent(intent)

        assertEquals(AppEntryAction.SharePdf(uri), action)
    }

    @Test
    fun `send multiple pdf payloads stay unsupported`() {
        val first = mockUri("one.pdf")
        val second = mockUri("two.pdf")
        val intent = mockSendMultipleIntent(type = "application/pdf")
        stubArrayListExtraStream(intent, arrayListOf(first, second))

        val action = AppEntryActionCodec.fromIntent(intent)

        assertNull(action)
    }

    @Test
    fun `ignores unsupported share payloads`() {
        val uri = mockUri("notes.txt")
        val intent = mockSendIntent(type = "text/plain")
        stubSingleExtraStream(intent, uri)

        val action = AppEntryActionCodec.fromIntent(intent)

        assertNull(action)
    }

    private fun mockSendIntent(type: String?): Intent = mock(Intent::class.java).also { intent ->
        `when`(intent.getStringExtra(AppEntryActionCodec.EXTRA_KEY)).thenReturn(null)
        `when`(intent.action).thenReturn(Intent.ACTION_SEND)
        `when`(intent.type).thenReturn(type)
        `when`(intent.clipData).thenReturn(null)
        `when`(intent.data).thenReturn(null)
        stubSingleExtraStream(intent, null)
        stubArrayListExtraStream(intent, null)
    }

    private fun mockSendMultipleIntent(type: String?): Intent = mock(Intent::class.java).also { intent ->
        `when`(intent.getStringExtra(AppEntryActionCodec.EXTRA_KEY)).thenReturn(null)
        `when`(intent.action).thenReturn(Intent.ACTION_SEND_MULTIPLE)
        `when`(intent.type).thenReturn(type)
        `when`(intent.clipData).thenReturn(null)
        `when`(intent.data).thenReturn(null)
        stubSingleExtraStream(intent, null)
        stubArrayListExtraStream(intent, null)
    }

    private fun mockViewIntent(type: String?, data: Uri?): Intent = mock(Intent::class.java).also { intent ->
        `when`(intent.getStringExtra(AppEntryActionCodec.EXTRA_KEY)).thenReturn(null)
        `when`(intent.action).thenReturn(Intent.ACTION_VIEW)
        `when`(intent.type).thenReturn(type)
        `when`(intent.data).thenReturn(data)
        `when`(intent.clipData).thenReturn(null)
        stubSingleExtraStream(intent, null)
        stubArrayListExtraStream(intent, null)
    }

    @Suppress("DEPRECATION")
    private fun stubSingleExtraStream(intent: Intent, uri: Uri?) {
        `when`(intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)).thenReturn(uri)
        `when`(intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)).thenReturn(uri)
    }

    @Suppress("DEPRECATION")
    private fun stubArrayListExtraStream(intent: Intent, uris: ArrayList<Uri>?) {
        `when`(intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)).thenReturn(uris)
        `when`(intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)).thenReturn(uris)
    }

    private fun mockUri(lastPathSegment: String): Uri = mock(Uri::class.java).also { uri ->
        `when`(uri.lastPathSegment).thenReturn(lastPathSegment)
    }

    private fun mockContextWithResolvedType(uri: Uri, mimeType: String): Context {
        val resolver = mock(ContentResolver::class.java)
        `when`(resolver.getType(uri)).thenReturn(mimeType)
        return mock(Context::class.java).also { context ->
            `when`(context.contentResolver).thenReturn(resolver)
        }
    }

    private fun mockClipData(
        uris: Array<Uri>,
        mimeTypes: Array<String> = emptyArray()
    ): ClipData {
        val clipData = mock(ClipData::class.java)
        val description = mock(android.content.ClipDescription::class.java)
        `when`(clipData.itemCount).thenReturn(uris.size)
        `when`(clipData.description).thenReturn(description)
        `when`(description.mimeTypeCount).thenReturn(mimeTypes.size)
        mimeTypes.forEachIndexed { index, mimeType ->
            `when`(description.getMimeType(index)).thenReturn(mimeType)
        }
        uris.forEachIndexed { index, uri ->
            val item = mock(ClipData.Item::class.java)
            `when`(item.uri).thenReturn(uri)
            `when`(clipData.getItemAt(index)).thenReturn(item)
        }
        return clipData
    }
}

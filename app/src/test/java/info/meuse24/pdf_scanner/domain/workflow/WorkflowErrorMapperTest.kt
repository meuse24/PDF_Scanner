package info.meuse24.pdf_scanner.domain.workflow

import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.testutil.FakeResourceProvider
import info.meuse24.pdf_scanner.util.OcrModelInstallException
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkflowErrorMapperTest {

    @Test
    fun `maps OcrFailed with model install cause to model download message`() {
        val mapper = WorkflowErrorMapper(
            FakeResourceProvider(
                strings = mapOf(
                    R.string.ocr_model_download_failed to "model download failed",
                    R.string.searchable_failed to "searchable failed"
                )
            )
        )

        val message = mapper.map(
            ScanWorkflowError.OcrFailed(
                OcrModelInstallException("install failed")
            )
        )

        assertEquals("model download failed", message)
    }

    @Test
    fun `maps OcrFailed with generic cause to searchable failed message`() {
        val mapper = WorkflowErrorMapper(
            FakeResourceProvider(
                strings = mapOf(
                    R.string.ocr_model_download_failed to "model download failed",
                    R.string.searchable_failed to "searchable failed"
                )
            )
        )

        val message = mapper.map(
            ScanWorkflowError.OcrFailed(
                IllegalStateException("ocr failed")
            )
        )

        assertEquals("searchable failed", message)
    }

    @Test
    fun `maps AppendSourceEncrypted to append encrypted message`() {
        val mapper = WorkflowErrorMapper(
            FakeResourceProvider(
                strings = mapOf(
                    R.string.append_target_encrypted to "remove password before appending"
                )
            )
        )

        val message = mapper.map(ScanWorkflowError.AppendSourceEncrypted)

        assertEquals("remove password before appending", message)
    }
}

package info.meuse24.pdf_scanner.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import info.meuse24.pdf_scanner.domain.model.FormFieldValue
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PdfFormRoundTripInstrumentedTest {

    private lateinit var context: Context
    private lateinit var workDir: File
    private lateinit var formOps: PdfEditorFormOps

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PDFBoxResourceLoader.init(context)
        workDir = File(context.cacheDir, "form-round-trip").apply {
            deleteRecursively()
            mkdirs()
        }
        formOps = PdfEditorFormOps(context)
    }

    @After
    fun tearDown() {
        workDir.deleteRecursively()
    }

    @Test
    fun filledTextValueSurvivesRoundTrip() {
        assertTextRoundTrip("Miyuki 東京")
    }

    @Test
    fun devanagariTextValueSurvivesRoundTrip() {
        assertTextRoundTrip("नमस्ते")
    }

    @Test
    fun arabicTextValueSurvivesRoundTrip() {
        assertTextRoundTrip("مرحبا")
    }

    @Test
    fun japaneseChineseAndKoreanValuesSurviveRoundTrip() {
        listOf("こんにちは", "你好", "안녕하세요").forEachIndexed { index, text ->
            assertTextRoundTrip(text, inputName = "cjk-$index.pdf")
        }
    }

    @Test
    fun flattenRemovesInteractiveFields() {
        val input = createTextForm(File(workDir, "input.pdf"))

        val output = formOps.fillFormFields(
            input = input,
            outputDir = workDir,
            values = mapOf("name" to FormFieldValue.single("Flattened")),
            flatten = true
        )

        PDDocument.load(output).use { document ->
            assertEquals(1, document.numberOfPages)
            assertTrue(document.documentCatalog.getAcroForm(null)?.fields.orEmpty().isEmpty())
        }
    }

    private fun createTextForm(file: File): File {
        PDDocument().use { document ->
            val page = PDPage(PDRectangle.A4)
            document.addPage(page)
            val form = PDAcroForm(document)
            val resources = PDResources()
            context.assets.open("fonts/NotoSans-Regular.ttf").use { stream ->
                resources.put(
                    COSName.getPDFName("Noto"),
                    PDType0Font.load(document, stream, true)
                )
            }
            form.defaultResources = resources
            form.defaultAppearance = "/Noto 10 Tf 0 g"
            form.setNeedAppearances(false)
            document.documentCatalog.acroForm = form

            val field = PDTextField(form).apply {
                partialName = "name"
                alternateFieldName = "Name"
            }
            attachWidget(field, page, PDRectangle(50f, 730f, 260f, 24f))
            field.value = "Initial"
            form.fields = listOf(field)
            document.save(file)
        }
        return file
    }

    private fun assertTextRoundTrip(
        text: String,
        inputName: String = "input.pdf"
    ) {
        val input = createTextForm(File(workDir, inputName))
        val output = formOps.fillFormFields(
            input = input,
            outputDir = workDir,
            values = mapOf("name" to FormFieldValue.single(text)),
            flatten = false
        )

        PDDocument.load(output).use { document ->
            val form = requireNotNull(document.documentCatalog.getAcroForm(null))
            assertEquals(text, form.getField("name").valueAsString)
        }
    }

    private fun attachWidget(field: PDField, page: PDPage, rectangle: PDRectangle) {
        val widget = field.widgets.single()
        widget.rectangle = rectangle
        widget.page = page
        page.annotations.add(widget)
    }
}

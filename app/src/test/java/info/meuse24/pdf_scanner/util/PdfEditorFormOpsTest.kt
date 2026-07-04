package info.meuse24.pdf_scanner.util

import com.tom_roush.fontbox.ttf.TTFParser
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceEntry
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDComboBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDListBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDRadioButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import info.meuse24.pdf_scanner.domain.model.AcroFormCapability
import info.meuse24.pdf_scanner.domain.model.FormFieldType
import info.meuse24.pdf_scanner.domain.model.FormFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PdfEditorFormOpsTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val formOps = PdfEditorFormOps { null }

    @Test
    fun `reads all supported fields and maps radio widgets`() {
        val input = createFormPdf(temporaryFolder.newFile("form.pdf"))

        assertEquals(AcroFormCapability.FILLABLE, formOps.detectFormCapability(input))
        val fields = formOps.readFormFields(input)

        assertEquals(6, fields.size)
        assertEquals(FormFieldType.TEXT, fields.first { it.fullyQualifiedName == "name" }.type)
        assertEquals(
            "Yes",
            fields.first { it.fullyQualifiedName == "accepted" }.widgetOptionValue
        )
        val radioWidgets = fields.filter { it.fullyQualifiedName == "colour" }
        assertEquals(2, radioWidgets.size)
        assertEquals(listOf("red", "blue"), radioWidgets.map { it.widgetOptionValue })
        assertTrue(fields.all { it.rectNormalized.left in 0f..1f })
        assertTrue(fields.all { it.rectNormalized.top in 0f..1f })
    }

    @Test
    fun `fills fields and preserves editable AcroForm`() {
        val input = createFormPdf(temporaryFolder.newFile("form.pdf"))

        val output = formOps.fillFormFields(
            input = input,
            outputDir = temporaryFolder.root,
            values = mapOf(
                "name" to FormFieldValue.single("Ada Lovelace"),
                "accepted" to FormFieldValue.single("Yes"),
                "colour" to FormFieldValue.single("blue"),
                "country" to FormFieldValue.single("AT"),
                "topics" to FormFieldValue(listOf("Kotlin", "PDF"))
            ),
            flatten = false
        )

        PDDocument.load(output).use { document ->
            val form = requireNotNull(document.documentCatalog.getAcroForm(null))
            assertEquals("Ada Lovelace", form.getField("name").valueAsString)
            assertTrue((form.getField("accepted") as PDCheckBox).isChecked)
            assertEquals(1, (form.getField("colour") as PDRadioButton).selectedIndex)
            assertEquals(listOf("AT"), (form.getField("country") as PDComboBox).value)
            assertEquals(listOf("Kotlin", "PDF"), (form.getField("topics") as PDListBox).value)
        }
    }

    @Test
    fun `detects PDF without form`() {
        val input = temporaryFolder.newFile("plain.pdf")
        PDDocument().use { document ->
            document.addPage(PDPage(PDRectangle.A4))
            document.save(input)
        }

        assertEquals(AcroFormCapability.NONE, formOps.detectFormCapability(input))
        assertTrue(formOps.readFormFields(input).isEmpty())
    }

    @Test
    fun `selects fallback font by script`() {
        assertEquals(FormFallbackFont.GENERAL, "Miyuki".detectFormFallbackFont())
        assertEquals(FormFallbackFont.DEVANAGARI, "नमस्ते".detectFormFallbackFont())
        assertEquals(FormFallbackFont.ARABIC, "مرحبا".detectFormFallbackFont())
        assertEquals(FormFallbackFont.CJK, "Miyuki 東京".detectFormFallbackFont())
        assertEquals(FormFallbackFont.CJK, "こんにちは".detectFormFallbackFont())
        assertEquals(FormFallbackFont.CJK, "안녕하세요".detectFormFallbackFont())
    }

    @Test
    fun `bundled fallback fonts are parseable and contain required glyphs`() {
        assertFontContains(FormFallbackFont.GENERAL, "Miyuki ÄΩЖ")
        assertFontContains(FormFallbackFont.DEVANAGARI, "नमस्ते")
        assertFontContains(FormFallbackFont.ARABIC, "مرحبا")
        assertFontContains(FormFallbackFont.CJK, "Miyuki 東京 こんにちは 你好 안녕하세요")
    }

    private fun assertFontContains(fallback: FormFallbackFont, text: String) {
        val asset = sequenceOf(
            File("src/main/assets/${fallback.assetPath}"),
            File("app/src/main/assets/${fallback.assetPath}")
        ).firstOrNull(File::isFile)
        requireNotNull(asset) { "Missing test font asset: ${fallback.assetPath}" }

        TTFParser().parse(asset).use { font ->
            val cmap = font.unicodeCmapLookup
            text.codePoints()
                .filter { !Character.isWhitespace(it) }
                .forEach { codePoint ->
                    assertTrue(
                        "${fallback.name} has no glyph for U+${codePoint.toString(16).uppercase()}",
                        cmap.getGlyphId(codePoint) != 0
                    )
                }
        }
    }

    private fun createFormPdf(file: File): File {
        PDDocument().use { document ->
            val page = PDPage(PDRectangle.A4)
            document.addPage(page)
            val form = PDAcroForm(document).apply {
                defaultResources = rawDefaultResources()
                defaultAppearance = "/Helv 10 Tf 0 g"
                setNeedAppearances(true)
            }
            document.documentCatalog.acroForm = form

            val name = PDTextField(form).apply {
                partialName = "name"
                alternateFieldName = "Name"
                isRequired = true
            }
            attachWidgets(name, page, listOf(PDRectangle(50f, 730f, 250f, 22f)))
            name.value = "Initial"

            val accepted = PDCheckBox(form).apply { partialName = "accepted" }
            val acceptedWidget = widget(page, PDRectangle(50f, 690f, 18f, 18f), "Yes", document)
            accepted.widgets = listOf(acceptedWidget)
            acceptedWidget.setParent(accepted)
            accepted.check()

            val colour = PDRadioButton(form).apply {
                partialName = "colour"
                exportValues = listOf("red", "blue")
            }
            val colourWidgets = listOf(
                widget(page, PDRectangle(50f, 650f, 18f, 18f), "RedOn", document),
                widget(page, PDRectangle(90f, 650f, 18f, 18f), "BlueOn", document)
            )
            colour.widgets = colourWidgets
            colourWidgets.forEach { it.setParent(colour) }
            colour.value = "red"

            val country = PDComboBox(form).apply {
                partialName = "country"
                setOptions(listOf("AT", "DE"), listOf("Austria", "Germany"))
            }
            attachWidgets(country, page, listOf(PDRectangle(50f, 610f, 180f, 22f)))
            country.setValue("DE")

            val topics = PDListBox(form).apply {
                partialName = "topics"
                isMultiSelect = true
                options = listOf("Kotlin", "PDF", "Android")
            }
            attachWidgets(topics, page, listOf(PDRectangle(50f, 540f, 180f, 60f)))
            topics.value = listOf("Android")

            form.fields = listOf(name, accepted, colour, country, topics)
            document.save(file)
        }
        return file
    }

    private fun attachWidgets(
        field: PDField,
        page: PDPage,
        rectangles: List<PDRectangle>
    ) {
        val widgets = field.widgets
        assertFalse(widgets.isEmpty())
        widgets.zip(rectangles).forEach { (widget, rectangle) ->
            widget.rectangle = rectangle
            widget.page = page
            page.annotations.add(widget)
        }
    }

    private fun widget(
        page: PDPage,
        rectangle: PDRectangle,
        onValue: String,
        document: PDDocument
    ): PDAnnotationWidget {
        val normal = COSDictionary()
        normal.setItem(COSName.Off, appearanceStream(document, rectangle))
        normal.setItem(COSName.getPDFName(onValue), appearanceStream(document, rectangle))
        return PDAnnotationWidget().apply {
            this.rectangle = rectangle
            this.page = page
            appearance = PDAppearanceDictionary().apply {
                normalAppearance = PDAppearanceEntry(normal)
            }
            setAppearanceState(COSName.Off.name)
            page.annotations.add(this)
        }
    }

    private fun appearanceStream(
        document: PDDocument,
        rectangle: PDRectangle
    ): PDAppearanceStream = PDAppearanceStream(document).apply {
        bBox = PDRectangle(rectangle.width, rectangle.height)
        resources = PDResources()
    }

    private fun rawDefaultResources(): PDResources {
        val fontResources = COSDictionary().apply {
            setItem(COSName.HELV, rawType1Font("Helvetica"))
            setItem(COSName.ZA_DB, rawType1Font("ZapfDingbats"))
        }
        return PDResources().apply {
            cosObject.setItem(COSName.FONT, fontResources)
        }
    }

    private fun rawType1Font(baseFont: String): COSDictionary = COSDictionary().apply {
        setItem(COSName.TYPE, COSName.FONT)
        setItem(COSName.SUBTYPE, COSName.getPDFName("Type1"))
        setItem(COSName.BASE_FONT, COSName.getPDFName(baseFont))
    }
}

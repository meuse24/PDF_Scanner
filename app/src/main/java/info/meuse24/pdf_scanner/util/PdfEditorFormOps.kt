package info.meuse24.pdf_scanner.util

import android.content.Context
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDChoice
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDComboBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDListBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDRadioButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDSignatureField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDVariableText
import dagger.hilt.android.qualifiers.ApplicationContext
import info.meuse24.pdf_scanner.domain.common.resolveUniqueFilename
import info.meuse24.pdf_scanner.domain.model.AcroFormCapability
import info.meuse24.pdf_scanner.domain.model.FormField
import info.meuse24.pdf_scanner.domain.model.FormFieldOption
import info.meuse24.pdf_scanner.domain.model.FormFieldType
import info.meuse24.pdf_scanner.domain.model.FormFieldValue
import info.meuse24.pdf_scanner.domain.model.NormalizedFormRect
import info.meuse24.pdf_scanner.domain.pdf.PdfFormOps
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject
import javax.inject.Singleton

private const val FORM_OUTPUT_SUFFIX = "_Ausgefuellt"

internal enum class FormFallbackFont(
    val resourceName: String,
    val assetPath: String
) {
    GENERAL("FormFallbackGeneral", "fonts/NotoSans-Regular.ttf"),
    DEVANAGARI("FormFallbackDevanagari", "fonts/NotoSansDevanagari-Regular.ttf"),
    ARABIC("FormFallbackArabic", "fonts/NotoSansArabic-Regular.ttf"),
    CJK("FormFallbackCjk", "fonts/NotoSansCJKjp-VF.ttf")
}

@Singleton
class PdfEditorFormOps internal constructor(
    private val fallbackFontProvider: (FormFallbackFont) -> InputStream?
) : PdfFormOps {

    @Inject
    constructor(
        @ApplicationContext context: Context
    ) : this(assetFontProvider(context))

    override fun detectFormCapability(file: File): AcroFormCapability =
        PDDocument.load(file).use { document ->
            val acroForm = document.documentCatalog.getAcroForm(null)
                ?: return@use AcroFormCapability.NONE
            when {
                acroForm.hasXFA() -> AcroFormCapability.XFA_UNSUPPORTED
                acroForm.hasExistingSignature() -> AcroFormCapability.SIGNED_LOCKED
                acroForm.fieldTree.any { field -> field.isSupportedFillableField() } ->
                    AcroFormCapability.FILLABLE
                else -> AcroFormCapability.NONE
            }
        }

    override fun readFormFields(file: File): List<FormField> =
        PDDocument.load(file).use { document ->
            val acroForm = document.documentCatalog.getAcroForm(null) ?: return@use emptyList()
            val pages = (0 until document.numberOfPages).map(document::getPage)
            acroForm.fieldTree.flatMap { field ->
                field.toFormFields(pages)
            }
        }

    override fun fillFormFields(
        input: File,
        outputDir: File,
        values: Map<String, FormFieldValue>,
        flatten: Boolean
    ): File {
        outputDir.mkdirs()
        val baseName = resolveUniqueFilename(
            outputDir,
            "${input.nameWithoutExtension}$FORM_OUTPUT_SUFFIX"
        )
        val output = File(outputDir, "$baseName.pdf")
        val temporary = File.createTempFile("${baseName}_", ".tmp", outputDir)
        try {
            PDDocument.load(input).use { document ->
                val acroForm = document.documentCatalog.getAcroForm(null)
                    ?: throw IOException("PDF enthält kein AcroForm")
                check(acroForm.detectCapability() == AcroFormCapability.FILLABLE) {
                    "AcroForm kann nicht ausgefüllt werden"
                }

                val fieldsByName = acroForm.fieldTree.associateBy(PDField::getFullyQualifiedName)
                values.forEach { (name, value) ->
                    val field = fieldsByName[name] ?: return@forEach
                    if (field.isReadOnly) return@forEach
                    field.applyFormValue(document, acroForm, value)
                }
                if (flatten) {
                    if (acroForm.needAppearances) acroForm.refreshAppearances()
                    acroForm.flatten()
                }
                document.save(temporary)
            }
            if (!temporary.exists() || temporary.length() == 0L) {
                throw IOException("Formularausgabe ist leer")
            }
            Files.move(
                temporary.toPath(),
                output.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
            return output
        } catch (throwable: Throwable) {
            temporary.delete()
            throw if (throwable is IOException) {
                throwable
            } else {
                IOException("Formular konnte nicht gespeichert werden", throwable)
            }
        }
    }

    private fun PDAcroForm.detectCapability(): AcroFormCapability = when {
        hasXFA() -> AcroFormCapability.XFA_UNSUPPORTED
        hasExistingSignature() -> AcroFormCapability.SIGNED_LOCKED
        fieldTree.any { it.isSupportedFillableField() } -> AcroFormCapability.FILLABLE
        else -> AcroFormCapability.NONE
    }

    private fun PDField.applyFormValue(
        document: PDDocument,
        acroForm: PDAcroForm,
        value: FormFieldValue
    ) {
        when (this) {
            is PDTextField -> {
                val text = value.singleValue.take(maxLen.takeIf { it > 0 } ?: Int.MAX_VALUE)
                if (text.requiresUnicodeFallback()) {
                    installFallbackFont(document, acroForm, this, text)
                }
                setValue(text)
            }
            is PDCheckBox -> {
                val selected = value.singleValue
                if (selected.isNotEmpty() && selected != "Off") {
                    setValue(selected)
                } else {
                    unCheck()
                }
            }
            is PDRadioButton -> setValue(value.singleValue.ifEmpty { "Off" })
            is PDChoice -> {
                if (isMultiSelect) {
                    setValue(value.values)
                } else {
                    setValue(value.singleValue)
                }
            }
        }
    }

    private fun installFallbackFont(
        document: PDDocument,
        acroForm: PDAcroForm,
        field: PDVariableText,
        text: String
    ) {
        val resources = acroForm.defaultResources ?: PDResources().also(acroForm::setDefaultResources)
        val fallback = text.detectFormFallbackFont()
        val fontName = COSName.getPDFName(fallback.resourceName)
        val font = if (!resources.fontNames.contains(fontName)) {
            val stream = fallbackFontProvider(fallback)
                ?: throw IOException("Fallback-Font fehlt: ${fallback.assetPath}")
            // AcroForm appearance generation resolves the font again through
            // default resources. PDFBox requires a fully embedded font here;
            // a not-yet-written subset no longer exposes its Unicode cmap.
            stream.use { PDType0Font.load(document, it, false) }
                .also { resources.put(fontName, it) }
        } else {
            resources.getFont(fontName) as? PDType0Font
                ?: throw IOException("Ungültige Formular-Fontressource: ${fallback.resourceName}")
        }
        validateFontCoverage(font, text, fallback)
        field.defaultAppearance = "/${fallback.resourceName} 0 Tf 0 g"
    }

    private fun validateFontCoverage(
        font: PDType0Font,
        text: String,
        fallback: FormFallbackFont
    ) {
        val printableText = text.filterNot { it == '\n' || it == '\r' || it == '\t' }
        try {
            font.encode(printableText)
        } catch (exception: IllegalArgumentException) {
            throw IOException(
                "Font ${fallback.assetPath} deckt den Formulartext nicht vollständig ab",
                exception
            )
        }
    }
}

private fun PDAcroForm.hasExistingSignature(): Boolean =
    fieldTree.filterIsInstance<PDSignatureField>().any { it.signature != null }

private fun PDField.isSupportedFillableField(): Boolean =
    !isReadOnly && when (this) {
        is PDTextField,
        is PDCheckBox,
        is PDRadioButton,
        is PDComboBox,
        is PDListBox -> true
        else -> false
    }

private fun PDField.toFormFields(pages: List<PDPage>): List<FormField> {
    val widgets = widgets
    if (widgets.isEmpty()) return emptyList()
    val fieldType = toFormFieldType()
    val initialValue = toInitialValue()
    val options = toOptions()
    val label = alternateFieldName
        ?.takeIf(String::isNotBlank)
        ?: partialName?.takeIf(String::isNotBlank)
        ?: fullyQualifiedName

    return widgets.mapIndexedNotNull { widgetIndex, widget ->
        val pageIndex = widget.findPageIndex(pages)
        val page = pages.getOrNull(pageIndex) ?: return@mapIndexedNotNull null
        val rect = widget.rectangle ?: return@mapIndexedNotNull null
        if (rect.width <= 0f || rect.height <= 0f) return@mapIndexedNotNull null
        val widgetOptionValue = when (this) {
            is PDRadioButton -> options.getOrNull(widgetIndex)?.value
            is PDCheckBox -> options.firstOrNull()?.value
            else -> null
        }
        FormField(
            id = "$fullyQualifiedName#$widgetIndex",
            fullyQualifiedName = fullyQualifiedName,
            label = label,
            type = fieldType,
            pageIndex = pageIndex,
            rectNormalized = rect.toNormalizedFormRect(page.cropBox, page.rotation),
            value = initialValue,
            options = options,
            widgetOptionValue = widgetOptionValue,
            required = isRequired,
            readOnly = isReadOnly,
            maxLength = (this as? PDTextField)?.maxLen?.takeIf { it > 0 },
            multiSelect = (this as? PDChoice)?.isMultiSelect == true
        )
    }
}

private fun PDField.toFormFieldType(): FormFieldType = when (this) {
    is PDTextField -> if (isMultiline) FormFieldType.MULTILINE_TEXT else FormFieldType.TEXT
    is PDCheckBox -> FormFieldType.CHECKBOX
    is PDRadioButton -> FormFieldType.RADIO_GROUP
    is PDComboBox -> FormFieldType.COMBO_BOX
    is PDListBox -> FormFieldType.LIST_BOX
    else -> FormFieldType.UNSUPPORTED
}

private fun PDField.toInitialValue(): FormFieldValue = when (this) {
    is PDCheckBox -> if (isChecked) FormFieldValue.single(safeOnValue()) else FormFieldValue()
    is PDRadioButton -> {
        val selectedIndex = selectedIndex
        val selectedValue = exportValues.getOrNull(selectedIndex)
            ?: onValues.toList().getOrNull(selectedIndex)
            ?: value.takeUnless { it == "Off" }
        FormFieldValue.single(selectedValue.orEmpty())
    }
    is PDChoice -> FormFieldValue(value)
    else -> FormFieldValue.single(valueAsString)
}

private fun PDField.toOptions(): List<FormFieldOption> = when (this) {
    is PDCheckBox -> safeOnValue()
        .takeIf(String::isNotEmpty)
        ?.let { listOf(FormFieldOption(it)) }
        .orEmpty()
    is PDRadioButton -> {
        val exportValues = exportValues
        val onValues = onValues.toList()
        if (exportValues.size == widgets.size) {
            exportValues.map(::FormFieldOption)
        } else {
            onValues.map(::FormFieldOption)
        }
    }
    is PDChoice -> {
        val exportValues = optionsExportValues
        val displayValues = optionsDisplayValues
        val values = exportValues.ifEmpty { options }
        values.mapIndexed { index, value ->
            FormFieldOption(value, displayValues.getOrNull(index) ?: value)
        }
    }
    else -> emptyList()
}

private fun PDCheckBox.safeOnValue(): String =
    runCatching { onValue }.getOrDefault("")

private fun PDAnnotationWidget.findPageIndex(pages: List<PDPage>): Int {
    val assignedPage = page
    if (assignedPage != null) {
        val assignedIndex = pages.indexOfFirst { it.cosObject === assignedPage.cosObject }
        if (assignedIndex >= 0) return assignedIndex
    }
    return pages.indexOfFirst { candidate ->
        candidate.annotations.any { annotation -> annotation.cosObject === cosObject }
    }
}

private fun PDRectangle.toNormalizedFormRect(
    cropBox: PDRectangle,
    rotation: Int
): NormalizedFormRect {
    val width = cropBox.width.coerceAtLeast(1f)
    val height = cropBox.height.coerceAtLeast(1f)
    val left = ((lowerLeftX - cropBox.lowerLeftX) / width).coerceIn(0f, 1f)
    val right = ((upperRightX - cropBox.lowerLeftX) / width).coerceIn(0f, 1f)
    val top = ((cropBox.upperRightY - upperRightY) / height).coerceIn(0f, 1f)
    val bottom = ((cropBox.upperRightY - lowerLeftY) / height).coerceIn(0f, 1f)
    val corners = listOf(
        rotateNormalized(left, top, rotation),
        rotateNormalized(right, top, rotation),
        rotateNormalized(left, bottom, rotation),
        rotateNormalized(right, bottom, rotation)
    )
    return NormalizedFormRect(
        left = corners.minOf { it.first }.coerceIn(0f, 1f),
        top = corners.minOf { it.second }.coerceIn(0f, 1f),
        right = corners.maxOf { it.first }.coerceIn(0f, 1f),
        bottom = corners.maxOf { it.second }.coerceIn(0f, 1f)
    )
}

private fun rotateNormalized(x: Float, y: Float, rotation: Int): Pair<Float, Float> =
    when (normalizeRotation(rotation)) {
        90 -> 1f - y to x
        180 -> 1f - x to 1f - y
        270 -> y to 1f - x
        else -> x to y
    }

private fun String.requiresUnicodeFallback(): Boolean = any { it.code > 255 }

internal fun String.detectFormFallbackFont(): FormFallbackFont {
    var hasDevanagari = false
    var hasArabic = false
    val codePoints = codePoints().iterator()
    while (codePoints.hasNext()) {
        val codePoint = codePoints.nextInt()
        when {
            codePoint.isCjkCodePoint() -> return FormFallbackFont.CJK
            codePoint.isArabicCodePoint() -> hasArabic = true
            codePoint.isDevanagariCodePoint() -> hasDevanagari = true
        }
    }
    return when {
        hasArabic -> FormFallbackFont.ARABIC
        hasDevanagari -> FormFallbackFont.DEVANAGARI
        else -> FormFallbackFont.GENERAL
    }
}

private fun Int.isDevanagariCodePoint(): Boolean =
    this in 0x0900..0x097F ||
        this in 0xA8E0..0xA8FF ||
        this in 0x11B00..0x11B5F

private fun Int.isArabicCodePoint(): Boolean =
    this in 0x0600..0x06FF ||
        this in 0x0750..0x077F ||
        this in 0x0870..0x089F ||
        this in 0x08A0..0x08FF ||
        this in 0xFB50..0xFDFF ||
        this in 0xFE70..0xFEFF

private fun Int.isCjkCodePoint(): Boolean =
    this in 0x1100..0x11FF ||
        this in 0x2E80..0x303F ||
        this in 0x3040..0x30FF ||
        this in 0x3130..0x318F ||
        this in 0x31F0..0x31FF ||
        this in 0x3400..0x4DBF ||
        this in 0x4E00..0x9FFF ||
        this in 0xA960..0xA97F ||
        this in 0xAC00..0xD7AF ||
        this in 0xD7B0..0xD7FF ||
        this in 0xF900..0xFAFF ||
        this in 0x20000..0x2FA1F

private fun assetFontProvider(context: Context): (FormFallbackFont) -> InputStream? = { fallback ->
    runCatching { context.assets.open(fallback.assetPath) }.getOrNull()
}

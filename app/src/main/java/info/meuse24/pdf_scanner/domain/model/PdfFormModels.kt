package info.meuse24.pdf_scanner.domain.model

enum class AcroFormCapability {
    NONE,
    FILLABLE,
    XFA_UNSUPPORTED,
    SIGNED_LOCKED
}

enum class FormFieldType {
    TEXT,
    MULTILINE_TEXT,
    CHECKBOX,
    RADIO_GROUP,
    COMBO_BOX,
    LIST_BOX,
    UNSUPPORTED
}

data class NormalizedFormRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

data class FormFieldOption(
    val value: String,
    val label: String = value
)

data class FormFieldValue(
    val values: List<String> = emptyList()
) {
    val singleValue: String get() = values.firstOrNull().orEmpty()

    companion object {
        fun single(value: String): FormFieldValue =
            FormFieldValue(value.takeIf { it.isNotEmpty() }?.let(::listOf).orEmpty())
    }
}

data class FormField(
    val id: String,
    val fullyQualifiedName: String,
    val label: String,
    val type: FormFieldType,
    val pageIndex: Int,
    val rectNormalized: NormalizedFormRect,
    val value: FormFieldValue,
    val options: List<FormFieldOption> = emptyList(),
    val widgetOptionValue: String? = null,
    val required: Boolean = false,
    val readOnly: Boolean = false,
    val maxLength: Int? = null,
    val multiSelect: Boolean = false
)

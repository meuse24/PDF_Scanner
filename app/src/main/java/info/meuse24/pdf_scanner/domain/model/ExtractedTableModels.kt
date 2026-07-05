package info.meuse24.pdf_scanner.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class TableIssue {
    AMBIGUOUS_COLUMN,
    SPANNING_LINE,
    LOW_CONFIDENCE,
    WRAPPED_ROW_GUESS
}

data class ExtractedCell(
    val text: String,
    val sourceLineIds: List<Int>,
    val confidence: Float,
    val issues: Set<TableIssue> = emptySet()
)

data class ExtractedRow(
    val cells: List<ExtractedCell>,
    val included: Boolean = true
)

data class ExtractedTable(
    val pageIndex: Int,
    val rows: List<ExtractedRow>,
    val columnBounds: List<OcrRect>,
    val confidence: Float,
    val issues: Set<TableIssue> = emptySet()
)

data class TableExtractionResult(
    val tablesByPage: Map<Int, ExtractedTable?>
)

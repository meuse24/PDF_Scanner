package info.meuse24.pdf_scanner.domain.common

import info.meuse24.pdf_scanner.domain.model.ExtractedCell
import info.meuse24.pdf_scanner.domain.model.ExtractedRow
import info.meuse24.pdf_scanner.domain.model.ExtractedTable
import info.meuse24.pdf_scanner.domain.model.OcrPositionedPage
import info.meuse24.pdf_scanner.domain.model.OcrRect
import info.meuse24.pdf_scanner.domain.model.TableIssue
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Rekonstruiert die Haupttabelle einer Seite aus positionierter OCR-Geometrie.
 * Pure Kotlin, keine ML-Kit-/Android-Abhängigkeit. Siehe
 * docs/table-extraction-csv-implementation-plan.md, Abschnitt "Rekonstruktionsalgorithmus".
 *
 * Liefert `null`, wenn keine Seitenregion eine belastbare Mehrspaltenstruktur zeigt.
 */
fun reconstructTable(
    page: OcrPositionedPage,
    config: TableReconstructionConfig = TableReconstructionConfig()
): ExtractedTable? {
    val cleaned = cleanLines(page)
    if (cleaned.isEmpty()) return null

    val (medianLineHeight, robustCharWidth) = robustScale(cleaned, config)
    val medianAngle = computeMedianAngle(cleaned)
    val working = if (abs(medianAngle) >= config.relevantSkewDegrees) {
        deskew(cleaned, medianAngle, page.widthPx / 2f, page.heightPx / 2f)
    } else {
        cleaned
    }

    val rows = clusterRows(working, medianLineHeight, config)
    if (rows.isEmpty()) return null

    val minGapWidth = config.columnGapCharWidthFactor * robustCharWidth
    val rowSegmentsList = rows.map { rowSegments(it, minGapWidth) }

    val structuralCandidates = findRangeCandidates(rows, rowSegmentsList, medianLineHeight, config)
        .filter { it.qualifyingCount >= config.minSupportingRows }
    if (structuralCandidates.isEmpty()) return null

    // Eine Region muss nicht nur strukturell genug Zeilen mit >= minColumns Segmenten liefern,
    // sondern die daraus abgeleiteten Spalten muessen auch durch einen Mindestanteil der Zeilen
    // tatsaechlich bestaetigt werden (wiederkehrende Kanten-/Luecken-Uebereinstimmung) und einen
    // Mindest-Score erreichen. Sonst wuerden beliebige mehrteilige Textzeilen als Tabelle gelten.
    val validated = structuralCandidates.mapNotNull { candidate ->
        val columns = buildColumns(rows, rowSegmentsList, candidate, config)
        if (columns.size < config.minColumns) return@mapNotNull null
        if (!hasRecurringColumnSupport(rowSegmentsList, candidate, columns, config)) return@mapNotNull null
        val score = scoreRange(candidate, rowSegmentsList, config)
        if (score < config.minRegionScore) return@mapNotNull null
        Triple(candidate, columns, score)
    }
    if (validated.isEmpty()) return null

    val (winner, columns, _) = validated.maxByOrNull { it.third } ?: return null

    val lineSegmentCounts = rowSegmentsList.flatten().groupingBy { it.lineId }.eachCount()
    val finalRows = buildRows(rows, rowSegmentsList, lineSegmentCounts, winner, columns, medianLineHeight, config)

    val nonEmptyConfidences = finalRows.flatMap { it.cells }
        .filter { it.text.isNotBlank() }
        .map { it.confidence }
    val tableConfidence = if (nonEmptyConfidences.isNotEmpty()) nonEmptyConfidences.average().toFloat() else 0f

    val issues = finalRows.flatMap { row -> row.cells.flatMap { it.issues } }.toMutableSet()
    if (tableConfidence < config.lowConfidenceThreshold) issues += TableIssue.LOW_CONFIDENCE

    return ExtractedTable(
        pageIndex = page.pageIndex,
        rows = finalRows,
        columnBounds = columns,
        confidence = tableConfidence,
        issues = issues
    )
}

// ---- Interne Arbeitsmodelle ----

private data class WorkingElement(
    val id: Int,
    val text: String,
    val bounds: OcrRect,
    val confidence: Float,
    val angleDeg: Float
)

private data class WorkingLine(
    val id: Int,
    val bounds: OcrRect,
    val elements: List<WorkingElement>,
    val angleDeg: Float
)

private data class Row(val lines: List<WorkingLine>, val bounds: OcrRect)

private data class Segment(
    val lineId: Int,
    val text: String,
    val bounds: OcrRect,
    val confidence: Float
)

private data class RangeCandidate(val startIdx: Int, val endIdx: Int, val qualifyingCount: Int)

private data class ColumnAssignment(val index: Int, val ambiguous: Boolean)

// ---- Schritt 1: Bereinigung und robuste Skalenwerte ----

private fun cleanLines(page: OcrPositionedPage): List<WorkingLine> =
    page.lines
        .filter { it.bounds.isValid && it.text.isNotBlank() }
        .mapIndexed { index, line ->
            val cleanedElements = line.elements
                .filter { it.bounds.isValid && it.text.isNotBlank() }
                .mapIndexed { elIdx, el -> WorkingElement(elIdx, el.text, el.bounds, el.confidence, el.angleDeg) }
            val elements = cleanedElements.ifEmpty {
                listOf(WorkingElement(0, line.text, line.bounds, line.confidence, line.angleDeg))
            }
            WorkingLine(index, line.bounds, elements, line.angleDeg)
        }

private fun median(sorted: List<Float>): Float? {
    if (sorted.isEmpty()) return null
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2f
}

private fun robustScale(lines: List<WorkingLine>, config: TableReconstructionConfig): Pair<Float, Float> {
    val heights = lines.map { it.bounds.height }.filter { it > 0f }.sorted()
    val medianLineHeight = median(heights) ?: config.fallbackLineHeight

    val charWidths = lines.flatMap { it.elements }
        .filter { it.text.isNotEmpty() && it.bounds.width > 0f }
        .map { it.bounds.width / it.text.length }
        .sorted()
    val robustCharWidth = median(charWidths)?.takeIf { it > 0f } ?: config.fallbackCharWidth

    return medianLineHeight to robustCharWidth
}

// ---- Schritt 2: Seitenwinkel und Deskew ----

private fun foldAngle(angle: Float): Float {
    var a = angle % 360f
    if (a <= -180f) a += 360f
    if (a > 180f) a -= 360f
    return a
}

/**
 * Linearer Median auf Werten nahe der ±180°-Grenze scheitert (z. B. 179° und -179° liegen
 * geometrisch 2° auseinander, numerisch aber 358°). Deshalb wird zunächst eine zirkuläre
 * Referenzachse (Kreismittel über den Vektor-Mittelwert) bestimmt, alle Winkel relativ dazu
 * neu gefaltet (liegen danach nah bei 0°, ein linearer Median ist dort unkritisch) und das
 * Ergebnis anschließend wieder auf die Referenzachse zurückgerechnet.
 */
private fun computeMedianAngle(lines: List<WorkingLine>): Float {
    val angles = lines.flatMap { line -> line.elements.map { foldAngle(it.angleDeg) } }
    if (angles.isEmpty()) return 0f

    val meanSin = angles.map { sin(Math.toRadians(it.toDouble())) }.average()
    val meanCos = angles.map { cos(Math.toRadians(it.toDouble())) }.average()
    val referenceAxis = Math.toDegrees(atan2(meanSin, meanCos)).toFloat()

    val recentered = angles.map { foldAngle(it - referenceAxis) }.sorted()
    val medianRecentered = median(recentered) ?: 0f
    return foldAngle(medianRecentered + referenceAxis)
}

private fun rotatePoint(x: Float, y: Float, angleDeg: Float, pivotX: Float, pivotY: Float): Pair<Float, Float> {
    val rad = Math.toRadians(angleDeg.toDouble())
    val cosA = cos(rad)
    val sinA = sin(rad)
    val dx = (x - pivotX).toDouble()
    val dy = (y - pivotY).toDouble()
    val rx = dx * cosA - dy * sinA
    val ry = dx * sinA + dy * cosA
    return (rx + pivotX).toFloat() to (ry + pivotY).toFloat()
}

private fun rotateRect(rect: OcrRect, angleDeg: Float, pivotX: Float, pivotY: Float): OcrRect {
    val corners = listOf(
        rect.left to rect.top,
        rect.right to rect.top,
        rect.right to rect.bottom,
        rect.left to rect.bottom
    ).map { (x, y) -> rotatePoint(x, y, angleDeg, pivotX, pivotY) }
    val xs = corners.map { it.first }
    val ys = corners.map { it.second }
    return OcrRect(xs.min(), ys.min(), xs.max(), ys.max())
}

private fun deskew(lines: List<WorkingLine>, medianAngleDeg: Float, pivotX: Float, pivotY: Float): List<WorkingLine> {
    val rotation = -medianAngleDeg
    return lines.map { line ->
        line.copy(
            bounds = rotateRect(line.bounds, rotation, pivotX, pivotY),
            elements = line.elements.map { it.copy(bounds = rotateRect(it.bounds, rotation, pivotX, pivotY)) }
        )
    }
}

// ---- Schritt 3: Zeilenclustering ----

private fun union(a: OcrRect, b: OcrRect) = OcrRect(
    minOf(a.left, b.left),
    minOf(a.top, b.top),
    maxOf(a.right, b.right),
    maxOf(a.bottom, b.bottom)
)

private fun clusterRows(
    lines: List<WorkingLine>,
    medianLineHeight: Float,
    config: TableReconstructionConfig
): List<Row> {
    // Kanonische Sortierung nach Position (nicht nach Eingabereihenfolge) macht das Ergebnis
    // unabhängig von der Reihenfolge der übergebenen Lines.
    val sorted = lines.sortedWith(compareBy({ it.bounds.centerY }, { it.bounds.left }))
    val clusters = mutableListOf<MutableList<WorkingLine>>()
    var currentBounds: OcrRect? = null

    for (line in sorted) {
        val bounds = currentBounds
        if (bounds != null && (
                bounds.verticalOverlapRatio(line.bounds) >= config.rowOverlapRatioThreshold ||
                    abs(line.bounds.centerY - bounds.centerY) <= config.rowBaselineToleranceFactor * medianLineHeight
                )
        ) {
            clusters.last().add(line)
            currentBounds = union(bounds, line.bounds)
        } else {
            clusters.add(mutableListOf(line))
            currentBounds = line.bounds
        }
    }

    return clusters.map { group -> Row(lines = group, bounds = group.map { it.bounds }.reduce(::union)) }
}

// ---- Segmentierung (Grundlage für Schritt 4-6) ----

private fun splitLineIntoSegments(line: WorkingLine, minGapWidth: Float): List<Segment> {
    val sortedElements = line.elements.sortedBy { it.bounds.left }
    val groups = mutableListOf<MutableList<WorkingElement>>()
    for (el in sortedElements) {
        val lastGroup = groups.lastOrNull()
        if (lastGroup == null) {
            groups.add(mutableListOf(el))
        } else {
            val gap = el.bounds.left - lastGroup.last().bounds.right
            if (gap > minGapWidth) {
                groups.add(mutableListOf(el))
            } else {
                lastGroup.add(el)
            }
        }
    }
    return groups.map { group ->
        Segment(
            lineId = line.id,
            text = group.joinToString(" ") { it.text },
            bounds = group.map { it.bounds }.reduce(::union),
            confidence = group.map { it.confidence }.average().toFloat()
        )
    }
}

private fun rowSegments(row: Row, minGapWidth: Float): List<Segment> =
    row.lines.flatMap { splitLineIntoSegments(it, minGapWidth) }.sortedBy { it.bounds.left }

// ---- Schritt 4: Tabellenbereich erkennen ----

private fun findRangeCandidates(
    rows: List<Row>,
    rowSegmentsList: List<List<Segment>>,
    medianLineHeight: Float,
    config: TableReconstructionConfig
): List<RangeCandidate> {
    val maxGap = config.wrappedRowMaxGapFactor * medianLineHeight
    val candidates = mutableListOf<RangeCandidate>()
    var i = 0
    while (i < rows.size) {
        if (rowSegmentsList[i].size < config.minColumns) {
            i++
            continue
        }
        var qualifyingCount = 1
        var rangeLeft = rows[i].bounds.left
        var rangeRight = rows[i].bounds.right
        var j = i
        var k = i + 1
        while (k < rows.size) {
            val segs = rowSegmentsList[k]
            when {
                segs.size >= config.minColumns -> {
                    qualifyingCount++
                    rangeLeft = minOf(rangeLeft, rows[k].bounds.left)
                    rangeRight = maxOf(rangeRight, rows[k].bounds.right)
                    j = k
                    k++
                }
                segs.size == 1 -> {
                    val tableWidth = rangeRight - rangeLeft
                    val segWidth = segs[0].bounds.width
                    val verticalGap = rows[k].bounds.top - rows[k - 1].bounds.bottom
                    val isNarrowEnough = tableWidth > 0f && segWidth < config.wrappedRowMaxWidthRatio * tableWidth
                    val isCloseEnough = verticalGap <= maxGap
                    if (isNarrowEnough && isCloseEnough) {
                        j = k
                        k++
                    } else {
                        break
                    }
                }
                else -> break
            }
        }
        candidates.add(RangeCandidate(i, j, qualifyingCount))
        i = j + 1
    }
    return candidates
}

private fun scoreRange(
    range: RangeCandidate,
    rowSegmentsList: List<List<Segment>>,
    config: TableReconstructionConfig
): Float {
    val qualifyingIdxs = (range.startIdx..range.endIdx).filter { rowSegmentsList[it].size >= config.minColumns }
    val counts = qualifyingIdxs.map { rowSegmentsList[it].size.toFloat() }
    val meanCount = counts.average().toFloat()
    val variance = if (counts.isNotEmpty()) counts.map { (it - meanCount) * (it - meanCount) }.average().toFloat() else 0f
    val stddev = sqrt(variance)
    val columnConsistency = if (meanCount > 0f) (1f - (stddev / meanCount)).coerceIn(0f, 1f) else 0f

    val rowCountNorm = (range.qualifyingCount.toFloat() / (config.minSupportingRows * 2f)).coerceAtMost(1f)
    val rangeLength = (range.endIdx - range.startIdx + 1).toFloat()
    val cellOccupancy = (range.qualifyingCount.toFloat() / rangeLength).coerceIn(0f, 1f)

    val confidences = qualifyingIdxs.flatMap { idx -> rowSegmentsList[idx].map { it.confidence } }
    val geometryConfidence = if (confidences.isNotEmpty()) confidences.average().toFloat() else 0f

    return config.regionScoreRowCountWeight * rowCountNorm +
        config.regionScoreColumnConsistencyWeight * columnConsistency +
        config.regionScoreCellOccupancyWeight * cellOccupancy +
        config.regionScoreConfidenceWeight * geometryConfidence
}

// ---- Schritt 5: Spalten bestimmen ----

private fun buildColumns(
    rows: List<Row>,
    rowSegmentsList: List<List<Segment>>,
    range: RangeCandidate,
    config: TableReconstructionConfig
): List<OcrRect> {
    val qualifyingIdxs = (range.startIdx..range.endIdx).filter { rowSegmentsList[it].size >= config.minColumns }
    if (qualifyingIdxs.isEmpty()) return emptyList()
    val referenceIdx = qualifyingIdxs.maxByOrNull { rowSegmentsList[it].size } ?: return emptyList()
    val referenceSegments = rowSegmentsList[referenceIdx].sortedBy { it.bounds.left }
    val k = referenceSegments.size
    if (k < config.minColumns) return emptyList()

    val separators = (0 until k - 1).map { idx ->
        (referenceSegments[idx].bounds.right + referenceSegments[idx + 1].bounds.left) / 2f
    }

    val regionRows = (range.startIdx..range.endIdx).map { rows[it] }
    val regionLeft = regionRows.minOf { it.bounds.left }
    val regionRight = regionRows.maxOf { it.bounds.right }
    val regionTop = regionRows.minOf { it.bounds.top }
    val regionBottom = regionRows.maxOf { it.bounds.bottom }

    return (0 until k).map { idx ->
        val left = if (idx == 0) regionLeft else separators[idx - 1]
        val right = if (idx == k - 1) regionRight else separators[idx]
        OcrRect(left, regionTop, right, regionBottom)
    }
}

/**
 * Prüft, ob die aus der Referenzzeile abgeleiteten Spaltengrenzen durch einen Mindestanteil der
 * qualifizierenden Zeilen tatsächlich als wiederkehrende Lücke bestätigt werden: für jede Zeile
 * wird geprüft, ob JEDE Kandidaten-Spaltengrenze innerhalb einer ihrer tatsächlichen
 * Zwischen-Segment-Lücken liegt (Toleranz relativ zur angrenzenden Spaltenbreite, damit eine
 * durchgehend leere Zelle - deren Lücke mehrere Spaltengrenzen überspannt - nicht faelschlich
 * als Nichtübereinstimmung zaehlt). Ohne diese Prüfung würden beliebige mehrteilige Textzeilen,
 * deren Wortlücken nicht an derselben Stelle wie in den übrigen Zeilen liegen, trotzdem als
 * Tabellenspalten durchgehen, sobald sie zufällig >= minColumns Segmente haben.
 */
private fun hasRecurringColumnSupport(
    rowSegmentsList: List<List<Segment>>,
    range: RangeCandidate,
    columns: List<OcrRect>,
    config: TableReconstructionConfig
): Boolean {
    val qualifyingIdxs = (range.startIdx..range.endIdx).filter { rowSegmentsList[it].size >= config.minColumns }
    if (qualifyingIdxs.isEmpty() || columns.size < 2) return false

    val separators = (0 until columns.size - 1).map { columns[it].right }
    val tolerances = (0 until columns.size - 1).map { idx ->
        config.separatorMatchToleranceRatio * minOf(columns[idx].width, columns[idx + 1].width)
    }

    val supportingRows = qualifyingIdxs.count { idx ->
        val segments = rowSegmentsList[idx].sortedBy { it.bounds.left }
        val gaps = (0 until segments.size - 1).map { i -> segments[i].bounds.right to segments[i + 1].bounds.left }
        separators.indices.all { sepIdx ->
            val separator = separators[sepIdx]
            val tolerance = tolerances[sepIdx]
            gaps.any { (gapLeft, gapRight) -> separator >= gapLeft - tolerance && separator <= gapRight + tolerance }
        }
    }

    return supportingRows.toFloat() / qualifyingIdxs.size >= config.minGapRecurrenceRatio
}

// ---- Schritt 6: Zellen zuordnen und Lines verfeinern ----

private fun assignColumn(segment: Segment, columns: List<OcrRect>, config: TableReconstructionConfig): ColumnAssignment {
    val width = segment.bounds.width
    if (width <= 0f || columns.isEmpty()) return ColumnAssignment(0, false)
    var bestIdx = 0
    var bestVal = -1f
    var secondVal = -1f
    columns.forEachIndexed { idx, col ->
        val overlap = segment.bounds.horizontalOverlap(col) / width
        if (overlap > bestVal) {
            secondVal = bestVal
            bestVal = overlap
            bestIdx = idx
        } else if (overlap > secondVal) {
            secondVal = overlap
        }
    }
    val ambiguous = secondVal >= 0f && (bestVal - secondVal) < config.ambiguousOverlapMarginRatio
    return ColumnAssignment(bestIdx, ambiguous)
}

private fun buildRows(
    rows: List<Row>,
    rowSegmentsList: List<List<Segment>>,
    lineSegmentCounts: Map<Int, Int>,
    range: RangeCandidate,
    columns: List<OcrRect>,
    medianLineHeight: Float,
    config: TableReconstructionConfig
): List<ExtractedRow> {
    val finalRows = mutableListOf<ExtractedRow>()
    val tableWidth = columns.last().right - columns.first().left
    val maxWrappedGap = config.wrappedRowMaxGapFactor * medianLineHeight

    for (idx in range.startIdx..range.endIdx) {
        val segments = rowSegmentsList[idx]

        val isNarrowSingleSegment = segments.size == 1 &&
            finalRows.isNotEmpty() &&
            idx != range.startIdx &&
            tableWidth > 0f &&
            segments[0].bounds.width < config.wrappedRowMaxWidthRatio * tableWidth

        if (isNarrowSingleSegment) {
            val seg = segments[0]
            val assignment = assignColumn(seg, columns, config)
            val verticalGap = rows[idx].bounds.top - rows[idx - 1].bounds.bottom
            val lastRow = finalRows.last()
            val prevCell = lastRow.cells[assignment.index]
            // Nur mergen, wenn die Folgezeile tatsaechlich nah ist UND dieselbe, bereits belegte
            // Spalte der Vorgaengerzeile fortsetzt. Sonst koennte ein schmaler, weit entfernter
            // Footer still in die letzte Zelle rutschen.
            val isCloseEnough = verticalGap <= maxWrappedGap
            val continuesOccupiedColumn = prevCell.text.isNotBlank()
            if (isCloseEnough && continuesOccupiedColumn) {
                finalRows.removeAt(finalRows.lastIndex)
                val updatedCells = lastRow.cells.toMutableList()
                val mergedIssues = prevCell.issues + TableIssue.WRAPPED_ROW_GUESS +
                    if (assignment.ambiguous) setOf(TableIssue.AMBIGUOUS_COLUMN) else emptySet()
                updatedCells[assignment.index] = prevCell.copy(
                    text = "${prevCell.text} ${seg.text}",
                    sourceLineIds = (prevCell.sourceLineIds + seg.lineId).distinct(),
                    confidence = (prevCell.confidence + seg.confidence) / 2f,
                    issues = mergedIssues
                )
                finalRows.add(lastRow.copy(cells = updatedCells))
                continue
            }
        }

        val assignments = segments.map { it to assignColumn(it, columns, config) }
        val cells = columns.indices.map { colIdx ->
            val matching = assignments.filter { it.second.index == colIdx }
            if (matching.isEmpty()) {
                ExtractedCell(text = "", sourceLineIds = emptyList(), confidence = 0f)
            } else {
                val sorted = matching.sortedWith(compareBy({ it.first.bounds.top }, { it.first.bounds.left }))
                val issues = mutableSetOf<TableIssue>()
                sorted.forEach { (seg, assignment) ->
                    if (assignment.ambiguous) issues += TableIssue.AMBIGUOUS_COLUMN
                    if ((lineSegmentCounts[seg.lineId] ?: 1) > 1) issues += TableIssue.SPANNING_LINE
                }
                ExtractedCell(
                    text = sorted.joinToString(" ") { it.first.text },
                    sourceLineIds = sorted.map { it.first.lineId }.distinct(),
                    confidence = sorted.map { it.first.confidence }.average().toFloat(),
                    issues = issues
                )
            }
        }
        finalRows.add(ExtractedRow(cells = cells))
    }

    return finalRows
}

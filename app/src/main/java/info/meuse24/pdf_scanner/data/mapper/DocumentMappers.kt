package info.meuse24.pdf_scanner.data.mapper

import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.util.fromOcrPageTextJson
import info.meuse24.pdf_scanner.util.toOcrPageTextJson

fun ScanRecord.toDomain(): Document = Document(
    id = id,
    filename = filename,
    filepath = filepath,
    timestamp = timestamp,
    pageCount = pageCount,
    fileSize = fileSize,
    thumbnailPath = thumbnailPath,
    isSearchable = isSearchable,
    isEncrypted = isEncrypted,
    extractedText = extractedText,
    tags = tags,
    ocrConfidence = ocrConfidence,
    ocrLanguage = ocrLanguage,
    pageTexts = ocrPageTextJson.fromOcrPageTextJson(),
    deletedAt = deletedAt,
    folderId = folderId,
    isFavorite = isFavorite
)

fun Document.toEntity(): ScanRecord = ScanRecord(
    id = id,
    filename = filename,
    filepath = filepath,
    timestamp = timestamp,
    pageCount = pageCount,
    fileSize = fileSize,
    thumbnailPath = thumbnailPath,
    isSearchable = isSearchable,
    isEncrypted = isEncrypted,
    extractedText = extractedText,
    tags = tags,
    ocrConfidence = ocrConfidence,
    ocrLanguage = ocrLanguage,
    ocrPageTextJson = pageTexts.toOcrPageTextJson(),
    deletedAt = deletedAt,
    folderId = folderId,
    isFavorite = isFavorite
)

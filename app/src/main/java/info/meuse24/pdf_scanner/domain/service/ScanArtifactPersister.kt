package info.meuse24.pdf_scanner.domain.service

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.pdf.PdfRenderingOps
import info.meuse24.pdf_scanner.domain.repository.DocumentRepository
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject

class ScanArtifactPersister @Inject constructor(
    private val pdfRenderingOps: PdfRenderingOps,
    private val repository: DocumentRepository
) {
    suspend fun persistDerived(
        outputFile: File,
        scansDir: File,
        pageCount: Int,
        thumbnailStrategy: ThumbnailStrategy = ThumbnailStrategy.Generate,
        transform: (Document) -> Document = { it }
    ): Document {
        val document = buildDocument(outputFile, scansDir, pageCount, thumbnailStrategy, transform)
        val savedId = repository.saveScan(document)
        return document.copy(id = savedId)
    }

    suspend fun persistDerivedFrom(
        sourceDocument: Document,
        outputFile: File,
        scansDir: File,
        pageCount: Int = sourceDocument.pageCount,
        thumbnailStrategy: ThumbnailStrategy = ThumbnailStrategy.Generate,
        transform: (Document) -> Document = { it }
    ): Document {
        return persistDerived(outputFile, scansDir, pageCount, thumbnailStrategy) {
            transform(it.copy(isSearchable = sourceDocument.isSearchable))
        }
    }

    suspend fun persistDerivedAll(
        outputFiles: List<File>,
        scansDir: File,
        pageCountProvider: (File) -> Int,
        transform: (Document, File) -> Document = { document, _ -> document }
    ): List<Document> {
        val documents = outputFiles.map { outputFile ->
            buildDocument(
                outputFile = outputFile,
                scansDir = scansDir,
                pageCount = pageCountProvider(outputFile),
                thumbnailStrategy = ThumbnailStrategy.Generate
            ) { document ->
                transform(document, outputFile)
            }
        }
        repository.saveScans(documents)
        return documents
    }

    private fun buildDocument(
        outputFile: File,
        scansDir: File,
        pageCount: Int,
        thumbnailStrategy: ThumbnailStrategy,
        transform: (Document) -> Document
    ): Document {
        val thumbnailPath = persistThumbnail(
            outputFile = outputFile,
            scansDir = scansDir,
            thumbnailStrategy = thumbnailStrategy
        )
        return transform(
            Document(
                filename = outputFile.nameWithoutExtension,
                filepath = outputFile.absolutePath,
                timestamp = System.currentTimeMillis(),
                pageCount = pageCount,
                fileSize = outputFile.length(),
                thumbnailPath = thumbnailPath
            )
        )
    }

    private fun persistThumbnail(
        outputFile: File,
        scansDir: File,
        thumbnailStrategy: ThumbnailStrategy
    ): String? {
        val target = File(scansDir, "${outputFile.nameWithoutExtension}.jpg")
        return when (thumbnailStrategy) {
            ThumbnailStrategy.Generate -> {
                pdfRenderingOps.generateThumbnail(outputFile, target)
                target.takeIf { it.exists() }?.absolutePath
            }
            is ThumbnailStrategy.CopyFromPath -> {
                val source = thumbnailStrategy.path?.let(::File)?.takeIf { it.exists() } ?: return null
                Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                target.absolutePath
            }
        }
    }

    sealed interface ThumbnailStrategy {
        data object Generate : ThumbnailStrategy
        data class CopyFromPath(val path: String?) : ThumbnailStrategy
    }
}

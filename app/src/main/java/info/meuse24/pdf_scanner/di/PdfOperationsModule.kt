package info.meuse24.pdf_scanner.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import info.meuse24.pdf_scanner.domain.pdf.PdfAnnotationOps
import info.meuse24.pdf_scanner.domain.pdf.PdfFormOps
import info.meuse24.pdf_scanner.domain.pdf.PdfMetadataOps
import info.meuse24.pdf_scanner.domain.pdf.PdfRenderingOps
import info.meuse24.pdf_scanner.domain.pdf.PdfSecurityOps
import info.meuse24.pdf_scanner.domain.pdf.PdfStructureOps
import info.meuse24.pdf_scanner.domain.pdf.PdfTextOps
import info.meuse24.pdf_scanner.util.PdfEditor
import info.meuse24.pdf_scanner.util.PdfEditorFormOps
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PdfOperationsModule {

    @Binds
    @Singleton
    abstract fun bindPdfStructureOps(
        impl: PdfEditor
    ): PdfStructureOps

    @Binds
    @Singleton
    abstract fun bindPdfRenderingOps(
        impl: PdfEditor
    ): PdfRenderingOps

    @Binds
    @Singleton
    abstract fun bindPdfSecurityOps(
        impl: PdfEditor
    ): PdfSecurityOps

    @Binds
    @Singleton
    abstract fun bindPdfTextOps(
        impl: PdfEditor
    ): PdfTextOps

    @Binds
    @Singleton
    abstract fun bindPdfAnnotationOps(
        impl: PdfEditor
    ): PdfAnnotationOps

    @Binds
    @Singleton
    abstract fun bindPdfMetadataOps(
        impl: PdfEditor
    ): PdfMetadataOps

    @Binds
    @Singleton
    abstract fun bindPdfFormOps(
        impl: PdfEditorFormOps
    ): PdfFormOps
}

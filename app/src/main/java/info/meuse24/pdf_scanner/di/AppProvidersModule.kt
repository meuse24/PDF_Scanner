package info.meuse24.pdf_scanner.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import info.meuse24.pdf_scanner.util.AndroidResourceProvider
import info.meuse24.pdf_scanner.util.AndroidStorageProvider
import info.meuse24.pdf_scanner.util.AndroidCsvShareFileStore
import info.meuse24.pdf_scanner.util.AndroidDownloadsStorage
import info.meuse24.pdf_scanner.util.AndroidOcrInputImageLoader
import info.meuse24.pdf_scanner.util.AndroidPdfPageInputImageLoader
import info.meuse24.pdf_scanner.util.AndroidPdfPageJpgRenderer
import info.meuse24.pdf_scanner.util.AndroidPdfPageBitmapRenderer
import info.meuse24.pdf_scanner.util.DefaultDispatcherProvider
import info.meuse24.pdf_scanner.domain.gateway.DocumentFileStore
import info.meuse24.pdf_scanner.domain.gateway.DispatcherProvider
import info.meuse24.pdf_scanner.domain.gateway.DocxBuilder
import info.meuse24.pdf_scanner.domain.gateway.DownloadsStorage
import info.meuse24.pdf_scanner.domain.gateway.CsvShareFileStore
import info.meuse24.pdf_scanner.domain.gateway.OcrDocumentTextExtractor
import info.meuse24.pdf_scanner.domain.gateway.OcrPositionedTextExtractor
import info.meuse24.pdf_scanner.domain.gateway.PdfPageJpgRenderer
import info.meuse24.pdf_scanner.domain.gateway.QrCodeReader
import info.meuse24.pdf_scanner.domain.gateway.ReviewPromptPolicy
import info.meuse24.pdf_scanner.domain.gateway.TableDraftStore
import info.meuse24.pdf_scanner.util.OcrInputImageLoader
import info.meuse24.pdf_scanner.util.OcrModelInstaller
import info.meuse24.pdf_scanner.util.PdfPageInputImageLoader
import info.meuse24.pdf_scanner.util.PdfPageBitmapRenderer
import info.meuse24.pdf_scanner.domain.gateway.ResourceProvider
import info.meuse24.pdf_scanner.domain.gateway.SearchablePdfGenerator
import info.meuse24.pdf_scanner.domain.gateway.StorageProvider
import info.meuse24.pdf_scanner.util.FileTableDraftStore
import info.meuse24.pdf_scanner.util.FileUtil
import info.meuse24.pdf_scanner.util.MlKitOcrDocumentTextExtractor
import info.meuse24.pdf_scanner.util.MlKitOcrPositionedTextExtractor
import info.meuse24.pdf_scanner.util.MlKitTextRecognizerRunner
import info.meuse24.pdf_scanner.util.QrCodeScanner
import info.meuse24.pdf_scanner.util.SearchablePdfBuilder
import info.meuse24.pdf_scanner.util.PlayReviewPromptManager
import info.meuse24.pdf_scanner.util.TextRecognizerRunner
import info.meuse24.pdf_scanner.util.AndroidOcrModelInstaller
import info.meuse24.pdf_scanner.domain.pdf.PdfImageRenderer
import info.meuse24.pdf_scanner.util.BitmapPdfImageRenderer
import info.meuse24.pdf_scanner.util.DocxBuilderImpl
import info.meuse24.pdf_scanner.domain.gateway.TextTranslator
import info.meuse24.pdf_scanner.util.MlKitTextTranslator
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppProvidersModule {

    @Binds
    @Singleton
    abstract fun bindResourceProvider(
        impl: AndroidResourceProvider
    ): ResourceProvider

    @Binds
    @Singleton
    abstract fun bindStorageProvider(
        impl: AndroidStorageProvider
    ): StorageProvider

    @Binds
    @Singleton
    abstract fun bindDownloadsStorage(
        impl: AndroidDownloadsStorage
    ): DownloadsStorage

    @Binds
    @Singleton
    abstract fun bindDocxBuilder(
        impl: DocxBuilderImpl
    ): DocxBuilder

    @Binds
    @Singleton
    abstract fun bindDocumentFileStore(
        impl: FileUtil
    ): DocumentFileStore

    @Binds
    @Singleton
    abstract fun bindPdfPageJpgRenderer(
        impl: AndroidPdfPageJpgRenderer
    ): PdfPageJpgRenderer

    @Binds
    @Singleton
    abstract fun bindPdfPageBitmapRenderer(
        impl: AndroidPdfPageBitmapRenderer
    ): PdfPageBitmapRenderer

    @Binds
    @Singleton
    abstract fun bindPdfPageInputImageLoader(
        impl: AndroidPdfPageInputImageLoader
    ): PdfPageInputImageLoader

    @Binds
    @Singleton
    abstract fun bindOcrInputImageLoader(
        impl: AndroidOcrInputImageLoader
    ): OcrInputImageLoader

    @Binds
    @Singleton
    abstract fun bindPdfImageRenderer(
        impl: BitmapPdfImageRenderer
    ): PdfImageRenderer

    @Binds
    @Singleton
    abstract fun bindOcrModelInstaller(
        impl: AndroidOcrModelInstaller
    ): OcrModelInstaller

    @Binds
    @Singleton
    abstract fun bindSearchablePdfGenerator(
        impl: SearchablePdfBuilder
    ): SearchablePdfGenerator

    @Binds
    @Singleton
    abstract fun bindQrCodeReader(
        impl: QrCodeScanner
    ): QrCodeReader

    @Binds
    @Singleton
    abstract fun bindOcrDocumentTextExtractor(
        impl: MlKitOcrDocumentTextExtractor
    ): OcrDocumentTextExtractor

    @Binds
    @Singleton
    abstract fun bindOcrPositionedTextExtractor(
        impl: MlKitOcrPositionedTextExtractor
    ): OcrPositionedTextExtractor

    @Binds
    @Singleton
    abstract fun bindCsvShareFileStore(
        impl: AndroidCsvShareFileStore
    ): CsvShareFileStore

    @Binds
    @Singleton
    abstract fun bindTableDraftStore(
        impl: FileTableDraftStore
    ): TableDraftStore

    @Binds
    @Singleton
    abstract fun bindReviewPromptPolicy(
        impl: PlayReviewPromptManager
    ): ReviewPromptPolicy

    @Binds
    @Singleton
    abstract fun bindTextRecognizerRunner(
        impl: MlKitTextRecognizerRunner
    ): TextRecognizerRunner

    @Binds
    @Singleton
    abstract fun bindDispatcherProvider(
        impl: DefaultDispatcherProvider
    ): DispatcherProvider

    @Binds
    @Singleton
    abstract fun bindTextTranslator(
        impl: MlKitTextTranslator
    ): TextTranslator
}

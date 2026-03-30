package info.meuse24.pdf_scanner.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import info.meuse24.pdf_scanner.util.AndroidResourceProvider
import info.meuse24.pdf_scanner.util.AndroidStorageProvider
import info.meuse24.pdf_scanner.util.AndroidDownloadsStorage
import info.meuse24.pdf_scanner.util.AndroidOcrInputImageLoader
import info.meuse24.pdf_scanner.util.AndroidPdfPageInputImageLoader
import info.meuse24.pdf_scanner.util.AndroidPdfPageJpgRenderer
import info.meuse24.pdf_scanner.util.DefaultDispatcherProvider
import info.meuse24.pdf_scanner.util.DispatcherProvider
import info.meuse24.pdf_scanner.util.DownloadsStorage
import info.meuse24.pdf_scanner.util.OcrInputImageLoader
import info.meuse24.pdf_scanner.util.OcrModelInstaller
import info.meuse24.pdf_scanner.util.PdfPageInputImageLoader
import info.meuse24.pdf_scanner.util.PdfPageJpgRenderer
import info.meuse24.pdf_scanner.util.ResourceProvider
import info.meuse24.pdf_scanner.util.StorageProvider
import info.meuse24.pdf_scanner.util.MlKitTextRecognizerRunner
import info.meuse24.pdf_scanner.util.TextRecognizerRunner
import info.meuse24.pdf_scanner.util.AndroidOcrModelInstaller
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
    abstract fun bindPdfPageJpgRenderer(
        impl: AndroidPdfPageJpgRenderer
    ): PdfPageJpgRenderer

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
    abstract fun bindOcrModelInstaller(
        impl: AndroidOcrModelInstaller
    ): OcrModelInstaller

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
}

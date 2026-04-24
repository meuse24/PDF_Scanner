package info.meuse24.pdf_scanner.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import info.meuse24.pdf_scanner.data.export.VCardExportRepositoryImpl
import info.meuse24.pdf_scanner.data.repository.FolderRepositoryImpl
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.data.repository.SettingsRepository
import info.meuse24.pdf_scanner.data.repository.TrashRepository
import info.meuse24.pdf_scanner.domain.repository.AppSettingsRepository
import info.meuse24.pdf_scanner.domain.repository.DocumentRepository
import info.meuse24.pdf_scanner.domain.repository.FolderRepository
import info.meuse24.pdf_scanner.domain.repository.TrashDocumentRepository
import info.meuse24.pdf_scanner.domain.repository.VCardExportRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindDocumentRepository(
        impl: ScanRepository
    ): DocumentRepository

    @Binds
    @Singleton
    abstract fun bindAppSettingsRepository(
        impl: SettingsRepository
    ): AppSettingsRepository

    @Binds
    @Singleton
    abstract fun bindFolderRepository(
        impl: FolderRepositoryImpl
    ): FolderRepository

    @Binds
    @Singleton
    abstract fun bindTrashDocumentRepository(
        impl: TrashRepository
    ): TrashDocumentRepository

    @Binds
    @Singleton
    abstract fun bindVCardExportRepository(
        impl: VCardExportRepositoryImpl
    ): VCardExportRepository
}

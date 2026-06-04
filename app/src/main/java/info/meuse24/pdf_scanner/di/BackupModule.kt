package info.meuse24.pdf_scanner.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import info.meuse24.pdf_scanner.data.backup.Argon2BackupKeyDeriver
import info.meuse24.pdf_scanner.data.backup.BackupManifestZipReader
import info.meuse24.pdf_scanner.data.backup.BackupRestoreStore
import info.meuse24.pdf_scanner.data.backup.BackupZipPayloadWriter
import info.meuse24.pdf_scanner.data.backup.BackupZipStagingReader
import info.meuse24.pdf_scanner.data.backup.RoomBackupDataSource
import info.meuse24.pdf_scanner.data.backup.RoomBackupRestoreStore
import info.meuse24.pdf_scanner.data.backup.RoomBackupRestoreWriter
import info.meuse24.pdf_scanner.data.backup.TinkBackupArchiveCodec
import info.meuse24.pdf_scanner.domain.gateway.BackupArchiveCodec
import info.meuse24.pdf_scanner.domain.gateway.BackupDataSource
import info.meuse24.pdf_scanner.domain.gateway.BackupKeyDeriver
import info.meuse24.pdf_scanner.domain.gateway.BackupLocationFactory
import info.meuse24.pdf_scanner.domain.gateway.BackupManifestReader
import info.meuse24.pdf_scanner.domain.gateway.BackupPayloadWriter
import info.meuse24.pdf_scanner.domain.gateway.BackupPayloadStagingReader
import info.meuse24.pdf_scanner.domain.gateway.BackupRestoreWriter
import info.meuse24.pdf_scanner.util.AndroidBackupLocationFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BackupModule {
    @Binds
    @Singleton
    abstract fun bindBackupArchiveCodec(
        impl: TinkBackupArchiveCodec
    ): BackupArchiveCodec

    @Binds
    @Singleton
    abstract fun bindBackupKeyDeriver(
        impl: Argon2BackupKeyDeriver
    ): BackupKeyDeriver

    @Binds
    @Singleton
    abstract fun bindBackupDataSource(
        impl: RoomBackupDataSource
    ): BackupDataSource

    @Binds
    @Singleton
    abstract fun bindBackupPayloadWriter(
        impl: BackupZipPayloadWriter
    ): BackupPayloadWriter

    @Binds
    @Singleton
    abstract fun bindBackupPayloadStagingReader(
        impl: BackupZipStagingReader
    ): BackupPayloadStagingReader

    @Binds
    @Singleton
    abstract fun bindBackupRestoreWriter(
        impl: RoomBackupRestoreWriter
    ): BackupRestoreWriter

    @Binds
    @Singleton
    abstract fun bindBackupRestoreStore(
        impl: RoomBackupRestoreStore
    ): BackupRestoreStore

    @Binds
    @Singleton
    abstract fun bindBackupManifestReader(
        impl: BackupManifestZipReader
    ): BackupManifestReader

    @Binds
    @Singleton
    abstract fun bindBackupLocationFactory(
        impl: AndroidBackupLocationFactory
    ): BackupLocationFactory
}

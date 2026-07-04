package info.meuse24.pdf_scanner.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import info.meuse24.pdf_scanner.domain.gateway.LocalSyncServer
import info.meuse24.pdf_scanner.util.sync.KtorLocalSyncServer
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LocalSyncModule {

    @Binds
    @Singleton
    abstract fun bindLocalSyncServer(impl: KtorLocalSyncServer): LocalSyncServer
}

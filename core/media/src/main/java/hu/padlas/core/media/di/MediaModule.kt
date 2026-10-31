package hu.padlas.core.media.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import hu.padlas.core.media.MediaOps
import hu.padlas.core.media.MediaStoreCursorScanner
import hu.padlas.core.media.MediaStoreOps
import hu.padlas.core.media.MediaStoreScanner

@Module
@InstallIn(SingletonComponent::class)
abstract class MediaModule {

    @Binds
    abstract fun mediaOps(impl: MediaStoreOps): MediaOps

    @Binds
    abstract fun mediaStoreScanner(impl: MediaStoreCursorScanner): MediaStoreScanner
}

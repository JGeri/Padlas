package hu.padlas.core.common.di

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

/**
 * Dedicated pool for the hash pipeline. Hashing 25k files is CPU + I/O bound at the
 * same time; letting it saturate [DefaultDispatcher] starves recomposition.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class HashDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

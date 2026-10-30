package hu.padlas.core.common.di

import android.util.Log
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
object CoroutinesModule {

    @Provides
    @IoDispatcher
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun defaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @Singleton
    @HashDispatcher
    fun hashDispatcher(): CoroutineDispatcher = Dispatchers.Default.limitedParallelism(HASH_PARALLELISM)

    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(@DefaultDispatcher dispatcher: CoroutineDispatcher): CoroutineScope =
        CoroutineScope(SupervisorJob() + dispatcher + LogFailures)

    /**
     * Work in this scope outlives every screen: storage scans and deletes that finish their
     * bookkeeping after the screen left. Without a handler one failure there ends the process;
     * with it the failed job is logged and the next pass starts clean.
     */
    private val LogFailures = CoroutineExceptionHandler { _, failure ->
        Log.e("ApplicationScope", "Background work failed", failure)
    }

    private const val HASH_PARALLELISM = 4
}

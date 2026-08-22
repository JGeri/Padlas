package hu.padlas.feature.index

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The read-only half of [IndexRepository]. The Vault wants the progress numbers without
 * also getting the ability to start a scan; scans are started through [IndexScheduler].
 */
@Singleton
class IndexStatus @Inject constructor(repository: IndexRepository) {

    val state: StateFlow<IndexState> = repository.state

    /** True once the index holds at least one asset; the app uses it to skip the first run. */
    val indexed: Flow<Boolean> = repository.indexed
}

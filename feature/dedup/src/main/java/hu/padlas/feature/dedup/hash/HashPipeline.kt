package hu.padlas.feature.dedup.hash

import androidx.core.net.toUri
import hu.padlas.core.common.di.HashDispatcher
import hu.padlas.core.database.dao.AssetDao
import hu.padlas.core.database.model.HashRow
import hu.padlas.core.media.Hasher
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Spec 2.4. Hashing 25 000 files with SHA-256 is I/O bound and takes minutes, so the work
 * is staged: a cheap fingerprint for everyone, the expensive one only where the cheap one
 * collided, and a perceptual hash for images so bursts and lookalikes can be found at all.
 *
 * A singleton because the progress it publishes is read by the screen while the worker
 * drives it, and because two concurrent runs would hash the same files twice.
 */
@Singleton
class HashPipeline @Inject constructor(
    private val assetDao: AssetDao,
    private val hasher: Hasher,
    @HashDispatcher private val hashDispatcher: CoroutineDispatcher,
) {

    private val _progress = MutableStateFlow(HashProgress())
    val progress: StateFlow<HashProgress> = _progress.asStateFlow()

    private val running = Mutex()

    suspend fun run(): HashProgress = running.withLock {
        withContext(hashDispatcher) {
            val library = assetDao.count().coerceAtLeast(1)
            quickPhase(library)
            fullPhase()
            perceptualPhase(library)
            _progress.updateAndGet { it.copy(phase = HashPhase.DONE) }
        }
    }

    /** Phase 1: size + first 64KB + last 64KB. The disk never reads the middle. */
    private suspend fun quickPhase(library: Int) {
        val pending = assetDao.needingQuickHash(limit = library)
        beginPhase(HashPhase.QUICK, pending.size)
        forEachBounded(pending) { row ->
            val hash = hasher.quickHash(row.uri.toUri(), row.sizeBytes) ?: return@forEachBounded false
            assetDao.setQuickHash(row.mediaStoreId, hash, System.currentTimeMillis())
            true
        }
    }

    /**
     * Phase 2: the full hash, and only where two files already agreed on size, head and
     * tail. That is under 2% of a library, which is what makes the whole pipeline fit in
     * seconds instead of minutes.
     */
    private suspend fun fullPhase() {
        val pending = assetDao.quickHashCollisions()
            .flatMap { assetDao.rowsWithQuickHash(it.quickHash) }
            .filter { it.sha256.isNullOrBlank() }
        beginPhase(HashPhase.FULL, pending.size)
        forEachBounded(pending) { row ->
            val hash = hasher.sha256(row.uri.toUri()) ?: return@forEachBounded false
            assetDao.setSha256(row.mediaStoreId, hash)
            true
        }
    }

    /** Phase 3: images only. dHash and the sharpness score come out of one decode. */
    private suspend fun perceptualPhase(library: Int) {
        val pending = assetDao.needingPerceptualHash(limit = library)
        beginPhase(HashPhase.PERCEPTUAL, pending.size)
        forEachBounded(pending) { row ->
            val result = hasher.perceptual(row.uri.toUri()) ?: return@forEachBounded false
            assetDao.setPerceptual(row.mediaStoreId, result.dHash, result.blurScore)
            true
        }
    }

    /**
     * Four files in flight, never more: the dispatcher is already limited to four threads,
     * and letting the whole queue fan out would hold 25 000 open decodes at once.
     *
     * The queue is read in one go rather than re-queried per batch, because a file the
     * hasher cannot open stays in the query result forever and a re-querying loop would
     * spin on it.
     */
    private suspend fun forEachBounded(rows: List<HashRow>, hash: suspend (HashRow) -> Boolean) {
        rows.chunked(MAX_IN_FLIGHT).forEach { chunk ->
            val results = coroutineScope {
                chunk.map { row -> async { hashOrSkip(row, hash) } }.awaitAll()
            }
            _progress.update { current ->
                current.copy(
                    done = current.done + results.size,
                    unreadable = current.unreadable + results.count { !it },
                )
            }
        }
    }

    /** One unreadable file must not take the batch down with it; the run reports it instead. */
    private suspend fun hashOrSkip(row: HashRow, hash: suspend (HashRow) -> Boolean): Boolean = try {
        hash(row)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        false
    }

    private fun beginPhase(phase: HashPhase, total: Int) {
        _progress.value = HashProgress(phase = phase, done = 0, total = total)
    }

    private companion object {
        const val MAX_IN_FLIGHT = 4
    }
}

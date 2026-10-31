package hu.padlas.core.testing

import hu.padlas.core.database.entity.Asset
import hu.padlas.core.media.MediaStoreScanner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * MediaStore as a map. Like the real one it never lists a trashed file, so a test puts in
 * [files] exactly what a plain query on the phone would return.
 */
class FakeMediaStoreScanner : MediaStoreScanner {

    val files = linkedMapOf<Long, Asset>()

    /** The cursor failed or the permission went away: the id pass comes back empty. */
    var unavailable = false

    /** Ids asked for by [scanIds], in order. */
    val readById = mutableListOf<Long>()

    fun put(vararg assets: Asset) = assets.forEach { files[it.mediaStoreId] = it }

    fun remove(vararg ids: Long) = ids.forEach { files.remove(it) }

    override fun scan(sinceDateModified: Long): Flow<List<Asset>> = flow {
        files.values
            .filter { sinceDateModified == 0L || it.dateModified > sinceDateModified }
            .sortedBy { it.mediaStoreId }
            .chunked(MediaStoreScanner.BATCH_SIZE)
            .forEach { emit(it) }
    }

    override fun scanIds(ids: Collection<Long>): Flow<List<Asset>> = flow {
        readById += ids
        ids.mapNotNull { files[it] }.chunked(MediaStoreScanner.BATCH_SIZE).forEach { emit(it) }
    }

    override suspend fun currentIds(): Set<Long> = if (unavailable) emptySet() else files.keys.toSet()

    override suspend fun assetById(mediaStoreId: Long): Asset? = files[mediaStoreId]
}

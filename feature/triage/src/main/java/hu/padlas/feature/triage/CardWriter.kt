package hu.padlas.feature.triage

import android.net.Uri
import hu.padlas.core.common.di.IoDispatcher
import hu.padlas.core.database.dao.AssetDao
import hu.padlas.core.database.dao.AuditDao
import hu.padlas.core.database.dao.CardDao
import hu.padlas.core.database.entity.Asset
import hu.padlas.core.database.entity.AuditAction
import hu.padlas.core.database.entity.AuditEntry
import hu.padlas.core.database.entity.CardEntity
import hu.padlas.core.media.ThumbnailStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * The cédula of spec 1.6. Written before the file is destroyed, never after: once the
 * system delete dialog returns there is nothing left to read a thumbnail from.
 *
 * A card is a 256px thumbnail, the recognised text, the date, the original path and the
 * size - about 20 KB against the 2-4 MB of the original. That is what keeps a thrown out
 * screenshot findable six months later instead of turning it into a blind spot.
 */
@Singleton
class CardWriter @Inject constructor(
    private val assetDao: AssetDao,
    private val cardDao: CardDao,
    private val auditDao: AuditDao,
    private val thumbnails: ThumbnailStore,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    /** In id chunks SQLite accepts: a whole folder can be thousands of rows. */
    suspend fun writeFor(ids: List<Long>): List<CardEntity> =
        write(ids.chunked(SQLITE_ARGUMENT_LIMIT).flatMap { chunk -> assetDao.byIds(chunk) })

    /**
     * Assets that were never triaged get a card too, only without text. A record with a
     * thumbnail and a path still answers "did I have a screenshot of this".
     */
    suspend fun write(assets: List<Asset>): List<CardEntity> = withContext(io) {
        val pending = assets.filter { it.cardId == null }
        if (pending.isEmpty()) return@withContext emptyList()

        val now = System.currentTimeMillis()
        // A round can be a thousand shots: one thumbnail after the other kept the user
        // waiting half a minute before the delete dialog. A few at a time is what the
        // thumbnail decoder takes without starving the screen.
        val gate = Semaphore(THUMBNAIL_PARALLELISM)
        val cards = coroutineScope {
            pending.map { asset ->
                async {
                    gate.withPermit { cardOf(asset, now) }
                }
            }.awaitAll()
        }

        cardDao.upsertAll(cards)
        cards.forEach { assetDao.setCardId(it.assetId, it.cardId) }
        auditDao.insertAll(
            cards.map {
                AuditEntry(
                    timestamp = now,
                    action = AuditAction.CARD_WRITTEN,
                    assetId = it.assetId,
                    sizeBytes = it.sizeBytes,
                    reversible = false,
                )
            },
        )
        cards
    }

    private suspend fun cardOf(asset: Asset, now: Long): CardEntity {
        val cardId = UUID.randomUUID().toString()
        val thumb = runCatching {
            thumbnails.writeCardThumbnail(cardId, Uri.parse(asset.uri))
        }.getOrNull()
        return CardEntity(
            cardId = cardId,
            assetId = asset.mediaStoreId,
            thumbPath = thumb,
            ocrText = asset.ocrText,
            dateTaken = asset.dateTaken,
            originalPath = asset.relativePath + asset.displayName,
            displayName = asset.displayName,
            sizeBytes = asset.sizeBytes,
            createdAt = now,
        )
    }

    private companion object {
        const val SQLITE_ARGUMENT_LIMIT = 900
        const val THUMBNAIL_PARALLELISM = 4
    }
}

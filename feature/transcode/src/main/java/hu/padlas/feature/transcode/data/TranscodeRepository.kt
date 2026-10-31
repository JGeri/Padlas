package hu.padlas.feature.transcode.data

import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import androidx.sqlite.db.SimpleSQLiteQuery
import dagger.hilt.android.qualifiers.ApplicationContext
import hu.padlas.core.common.di.IoDispatcher
import hu.padlas.core.common.text.UiText
import hu.padlas.core.common.text.uiText
import hu.padlas.core.database.dao.AssetDao
import hu.padlas.core.database.dao.AuditDao
import hu.padlas.core.database.dao.TranscodeJobDao
import hu.padlas.core.database.entity.Asset
import hu.padlas.core.database.entity.AuditAction
import hu.padlas.core.database.entity.AuditEntry
import hu.padlas.core.database.entity.JobState
import hu.padlas.core.database.entity.TranscodeJobEntity
import hu.padlas.core.database.model.SizeAndCount
import hu.padlas.core.media.MediaOps
import hu.padlas.core.media.MediaStoreScanner
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class TranscodeRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val assetDao: AssetDao,
    private val jobDao: TranscodeJobDao,
    private val auditDao: AuditDao,
    private val scanner: MediaStoreScanner,
    private val mediaOps: MediaOps,
    private val transcoder: VideoTranscoder,
    private val trialDecoder: TrialDecoder,
    private val publisher: TranscodeOutputPublisher,
    private val thermal: ThermalGovernor,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    /**
     * Conflated, not latest-wins: while the indexer writes, the asset table changes every
     * few milliseconds, and cancelling each read for the next one means none ever finishes.
     */
    fun observeVideos(limit: Int = LIST_LIMIT): Flow<List<VideoItem>> = combine(
        assetDao.observeTranscodeCandidates(TranscodeMath.MIN_CANDIDATE_BYTES),
        jobDao.observeAll(),
    ) { _, jobs -> jobs }.conflate().map { jobs -> videos(jobs, limit) }

    fun observeSaved(): Flow<SizeAndCount> = jobDao.observeSaved()

    fun observePending(): Flow<Int> = jobDao.observePendingCount()

    val throttled: Flow<Boolean> get() = thermal.throttled

    suspend fun enqueueAll(limit: Int = BATCH_LIMIT): Int = withContext(io) {
        val candidates = assetDao.transcodeCandidates(TranscodeMath.MIN_CANDIDATE_BYTES, limit)
        val now = now()
        val fresh = candidates.filter { jobDao.byAsset(it.mediaStoreId) == null }
        jobDao.upsertAll(fresh.map { TranscodeJobEntity(it.mediaStoreId, it.sizeBytes, updatedAt = now) })
        fresh.size
    }

    suspend fun enqueue(assetIds: List<Long>) = withContext(io) {
        val now = now()
        val assets = assetDao.byIds(assetIds).filter { it.isVideo }
        jobDao.upsertAll(assets.map { TranscodeJobEntity(it.mediaStoreId, it.sizeBytes, updatedAt = now) })
    }

    /**
     * One item at a time with a thermal check between each: the governor is what keeps a
     * night batch from degrading to a crawl half an hour in.
     */
    suspend fun runBatch(
        limit: Int = BATCH_LIMIT,
        onProgress: (BatchProgress) -> Unit = {},
    ): BatchOutcome {
        var done = 0
        var failed = 0
        repeat(limit) { index ->
            val job = withContext(io) { jobDao.byState(JobState.PENDING, 1).firstOrNull() }
                ?: return BatchOutcome(done, failed, paused = false)
            if (thermal.isThrottled()) return BatchOutcome(done, failed, paused = true)
            val asset = withContext(io) { assetDao.byId(job.assetId) }
            if (asset == null || !asset.isVideo) {
                withContext(io) { jobDao.setState(job.assetId, JobState.FAILED, TranscodeFailure.MISSING_ASSET.name, now()) }
                failed++
                return@repeat
            }
            onProgress(BatchProgress(index, asset.displayName, 0f))
            val succeeded = transcodeOne(asset) { fraction ->
                onProgress(BatchProgress(index, asset.displayName, fraction))
            }
            if (succeeded) done++ else failed++
            thermal.awaitCool()
        }
        return BatchOutcome(done, failed, paused = false)
    }

    private suspend fun transcodeOne(asset: Asset, onProgress: (Float) -> Unit): Boolean {
        val output = File(cacheDir(), asset.mediaStoreId.toString() + OUTPUT_EXTENSION)
        withContext(io) { jobDao.setState(asset.mediaStoreId, JobState.RUNNING, null, now()) }
        val request = TranscodeRequest(
            source = mediaOps.uriFor(asset.mediaStoreId, isVideo = true),
            output = output,
            sourceBytes = asset.sizeBytes,
            durationMs = asset.durationMs ?: 0L,
        )
        val export = transcoder.transcode(request, onProgress)
        if (export is ExportOutcome.Failure) return fail(asset.mediaStoreId, output, export.reason)
        val produced = (export as ExportOutcome.Success).resultBytes
        if (produced >= asset.sizeBytes) return fail(asset.mediaStoreId, output, TranscodeFailure.NO_GAIN.name)
        val trial = trialDecoder.verify(output, asset.durationMs ?: 0L)
        if (trial is TrialResult.Broken) return fail(asset.mediaStoreId, output, trial.reason)
        withContext(io) { jobDao.markSucceeded(asset.mediaStoreId, produced, output.absolutePath, now()) }
        return true
    }

    private suspend fun fail(assetId: Long, output: File, reason: String): Boolean {
        output.delete()
        withContext(io) { jobDao.setState(assetId, JobState.FAILED, reason, now()) }
        return false
    }

    /**
     * The compressed file enters the gallery here and the asset row starts pointing at it.
     * Removing the originals is the caller's separate, batched, user-confirmed step.
     */
    suspend fun publishFinished(assetIds: List<Long>): List<Uri> = withContext(io) {
        val originals = mutableListOf<Uri>()
        for (assetId in assetIds) {
            val job = jobDao.byAsset(assetId) ?: continue
            val path = job.outputPath ?: continue
            if (path.startsWith(CONTENT_SCHEME)) {
                originals += mediaOps.uriFor(assetId, isVideo = true)
                continue
            }
            val asset = assetDao.byId(assetId) ?: continue
            val trial = trialDecoder.verify(File(path), asset.durationMs ?: 0L)
            if (trial !is TrialResult.Playable) {
                jobDao.setState(assetId, JobState.FAILED, TranscodeFailure.RETRIAL_FAILED.name, now())
                continue
            }
            val published = publisher.publish(asset, File(path), trial) ?: continue
            assetDao.replaceAfterTranscode(assetId, published.toString(), job.resultBytes, OUTPUT_MIME)
            jobDao.markSucceeded(assetId, job.resultBytes, published.toString(), now())
            auditDao.insert(transcodedEntry(assetId, job))
            originals += mediaOps.uriFor(assetId, isVideo = true)
        }
        originals
    }

    /** Jobs whose compressed copy is in the gallery while the original still takes space. */
    suspend fun pendingRemovalIds(): List<Long> = withContext(io) {
        jobDao.byState(JobState.SUCCEEDED, LIST_LIMIT)
            .filter { it.outputPath?.startsWith(CONTENT_SCHEME) == true }
            .filter { scanner.assetById(it.assetId) != null }
            .map { it.assetId }
    }

    /** Called after the system trash dialog closed; only MediaStore decides what happened. */
    suspend fun settleRemovals(assetIds: List<Long>): Int = withContext(io) {
        var removed = 0
        for (assetId in assetIds) {
            if (scanner.assetById(assetId) != null) continue
            val job = jobDao.byAsset(assetId) ?: continue
            val asset = assetDao.byId(assetId)
            val publishedUri = job.outputPath?.takeIf { it.startsWith(CONTENT_SCHEME) }
            if (publishedUri != null && asset != null) {
                publisher.reclaimName(Uri.parse(publishedUri), asset.displayName)
            }
            File(cacheDir(), assetId.toString() + OUTPUT_EXTENSION).delete()
            auditDao.insert(
                AuditEntry(
                    timestamp = now(),
                    action = AuditAction.TRASHED,
                    assetId = assetId,
                    sizeBytes = job.originalBytes,
                    reversible = true,
                ),
            )
            removed++
        }
        removed
    }

    suspend fun retry(assetId: Long) = withContext(io) {
        jobDao.setState(assetId, JobState.PENDING, null, now())
    }

    private fun transcodedEntry(assetId: Long, job: TranscodeJobEntity) = AuditEntry(
        timestamp = now(),
        action = AuditAction.TRANSCODED,
        assetId = assetId,
        sizeBytes = TranscodeMath.savedBytes(job.originalBytes, job.resultBytes),
        payloadJson = "{\"eredetiBajt\":" + job.originalBytes + ",\"ujBajt\":" + job.resultBytes + "}",
    )

    private suspend fun videos(jobs: List<TranscodeJobEntity>, limit: Int): List<VideoItem> = withContext(io) {
        val byId = jobs.associateBy { it.assetId }
        val rows = assetDao.rawAssets(
            SimpleSQLiteQuery(VIDEO_QUERY, arrayOf(TranscodeMath.MIN_CANDIDATE_BYTES, limit.toLong())),
        )
        val largest = rows.maxOfOrNull { it.sizeBytes } ?: 0L
        rows.map { asset ->
            val job = byId[asset.mediaStoreId]
            val original = job?.originalBytes ?: asset.sizeBytes
            VideoItem(
                assetId = asset.mediaStoreId,
                displayName = asset.displayName,
                uri = mediaOps.uriFor(asset.mediaStoreId, isVideo = true),
                originalBytes = original,
                resultBytes = job?.resultBytes ?: 0L,
                durationMs = asset.durationMs ?: 0L,
                heightFraction = TranscodeMath.heightFraction(original, largest),
                state = job?.state,
                failureReason = job?.failureReason?.let(::failureText),
                published = job?.outputPath?.startsWith(CONTENT_SCHEME) == true,
            )
        }
    }

    private fun failureText(stored: String): UiText {
        val failure = TranscodeFailure.entries.firstOrNull { it.name == stored } ?: legacyFailures[stored]
        return if (failure != null) uiText(failure.text) else UiText.Raw(stored)
    }

    /** Jobs that failed before reasons were stored by name hold the Hungarian sentence itself. */
    private val legacyFailures: Map<String, TranscodeFailure> by lazy {
        val hungarian = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag("hu")) }
        val resources = context.createConfigurationContext(hungarian).resources
        TranscodeFailure.entries.associateBy { resources.getString(it.text) }
    }

    private fun cacheDir(): File = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }

    private fun now(): Long = System.currentTimeMillis()

    private companion object {
        const val LIST_LIMIT = 200
        const val BATCH_LIMIT = 40
        const val CACHE_DIR = "transcode"
        const val CONTENT_SCHEME = "content://"
        const val OUTPUT_MIME = "video/mp4"
        const val OUTPUT_EXTENSION = ".mp4"
        val VIDEO_QUERY = """
            SELECT * FROM asset
            WHERE mimeType LIKE 'video/%' AND state IN ('LOCAL', 'PROTECTED') AND sizeBytes > ?
            ORDER BY sizeBytes DESC LIMIT ?
        """.trimIndent()
    }
}

data class VideoItem(
    val assetId: Long,
    val displayName: String,
    val uri: Uri,
    val originalBytes: Long,
    val resultBytes: Long,
    val durationMs: Long,
    val heightFraction: Float,
    val state: JobState?,
    val failureReason: UiText?,
    val published: Boolean,
)

data class BatchProgress(val index: Int, val displayName: String, val fraction: Float)

data class BatchOutcome(val done: Int, val failed: Int, val paused: Boolean)

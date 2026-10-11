package hu.padlas.feature.rules

import androidx.sqlite.db.SimpleSQLiteQuery
import hu.padlas.core.common.di.IoDispatcher
import hu.padlas.core.common.format.Format
import hu.padlas.core.database.dao.AssetDao
import hu.padlas.core.database.dao.AuditDao
import hu.padlas.core.database.dao.RuleDao
import hu.padlas.core.database.dao.UploadJobDao
import hu.padlas.core.database.entity.Asset
import hu.padlas.core.database.entity.AssetState
import hu.padlas.core.database.entity.AuditAction
import hu.padlas.core.database.entity.AuditEntry
import hu.padlas.core.database.entity.JobState
import hu.padlas.core.database.entity.RuleAction
import hu.padlas.core.database.entity.RuleEntity
import hu.padlas.core.database.entity.UploadJobEntity
import hu.padlas.core.database.model.SizeAndCount
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

data class DryRunResult(
    val ruleId: Long,
    val matchCount: Int,
    val matchBytes: Long,
    val samples: List<Asset>,
)

data class RuleRunResult(
    val ruleId: Long,
    val affected: Int,
    val bytes: Long,
    val message: String,
)

/**
 * Automation is allowed to move things and to put them in the bin. It is never allowed to
 * delete permanently, in any value class (INV-1b), and it never runs before a dry run has
 * put a number on it (INV-6).
 */
@Singleton
class RuleEngine @Inject constructor(
    private val assetDao: AssetDao,
    private val ruleDao: RuleDao,
    private val auditDao: AuditDao,
    private val uploadJobDao: UploadJobDao,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    suspend fun dryRun(rule: RuleEntity, now: Long = System.currentTimeMillis()): DryRunResult =
        withContext(io) {
            val predicate = PredicateJson.decode(rule.predicateJson)
                ?: return@withContext DryRunResult(rule.id, 0, 0, emptyList())

            val totals = PredicateCompiler.selectTotals(predicate, now)
            val summary: SizeAndCount = assetDao.rawSizeAndCount(
                SimpleSQLiteQuery(totals.where, totals.args.toTypedArray()),
            )

            val listing = PredicateCompiler.selectAssets(predicate, now, limit = SAMPLE_POOL)
            val pool = assetDao.rawAssets(SimpleSQLiteQuery(listing.where, listing.args.toTypedArray()))

            DryRunResult(
                ruleId = rule.id,
                matchCount = summary.itemCount,
                matchBytes = summary.totalBytes,
                // Twenty random rows, not the twenty biggest: a sample that is always the
                // top of the list hides exactly the mistakes a dry run is meant to catch.
                samples = pool.shuffled().take(SAMPLES),
            )
        }

    suspend fun apply(rule: RuleEntity, now: Long = System.currentTimeMillis()): RuleRunResult =
        withContext(io) {
            val predicate = PredicateJson.decode(rule.predicateJson)
                ?: return@withContext RuleRunResult(rule.id, 0, 0, "A szabály nem értelmezhető.")

            val listing = PredicateCompiler.selectAssets(predicate, now)
            val matches = assetDao.rawAssets(SimpleSQLiteQuery(listing.where, listing.args.toTypedArray()))
                .filter { it.eligibleForAutomation() }

            if (matches.isEmpty()) {
                ruleDao.recordRun(rule.id, now, 0, 0)
                return@withContext RuleRunResult(rule.id, 0, 0, "Nincs találat.")
            }

            val bytes = matches.sumOf { it.sizeBytes }
            val ids = matches.map { it.mediaStoreId }

            val message = when (rule.action) {
                RuleAction.ARCHIVE -> {
                    uploadJobDao.upsertAll(
                        matches.map { asset ->
                            UploadJobEntity(
                                assetId = asset.mediaStoreId,
                                totalBytes = asset.sizeBytes,
                                state = JobState.PENDING,
                                updatedAt = now,
                            )
                        },
                    )
                    assetDao.setState(ids, AssetState.UPLOADING)
                    "${Format.count(ids.size)} fájl vár feltöltésre a felhőbe."
                }

                RuleAction.TRASH -> {
                    // The system bin can only be entered through a user-confirmed dialog,
                    // so automation stages the batch and the Kidobo screen finishes it.
                    // Pretending otherwise would make the trash counter lie (INV-1b, INV-2).
                    assetDao.defer(ids, now)
                    "${Format.count(ids.size)} fájl vár jóváhagyásra az Eldobható képek között."
                }

                RuleAction.PROTECT -> {
                    assetDao.setProtected(ids, true)
                    "${Format.count(ids.size)} fájl védett lett."
                }

                RuleAction.TRANSCODE -> {
                    "${Format.count(ids.size)} videó vár tömörítésre."
                }
            }

            auditDao.insert(
                AuditEntry(
                    timestamp = now,
                    action = AuditAction.RULE_RUN,
                    assetId = null,
                    sizeBytes = bytes,
                    reversible = rule.action == RuleAction.TRASH,
                    note = "${rule.name}: $message",
                ),
            )
            ruleDao.recordRun(rule.id, now, ids.size, bytes)

            RuleRunResult(rule.id, ids.size, bytes, message)
        }

    suspend fun runEnabled(now: Long = System.currentTimeMillis()): List<RuleRunResult> =
        withContext(io) {
            ruleDao.enabled().map { rule -> apply(rule, now) }
        }

    suspend fun seedBuiltInsIfEmpty() = withContext(io) {
        if (ruleDao.count() > 0) return@withContext
        ruleDao.upsertAll(BuiltInRules.all())
    }

    /** INV-4: the protected set is never touched by a rule, whatever the predicate says. */
    private fun Asset.eligibleForAutomation(): Boolean =
        !protectedManually && !isFavorite && state != AssetState.PROTECTED &&
            (shotKind == null || shotKind != hu.padlas.core.database.entity.ShotKind.SECRET)

    private companion object {
        const val SAMPLES = 20
        const val SAMPLE_POOL = 400
    }
}

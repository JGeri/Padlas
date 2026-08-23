package hu.padlas.work

import android.content.Context
import hu.padlas.feature.archive.ArchiveScheduler
import hu.padlas.feature.dedup.work.DedupScheduler
import hu.padlas.feature.index.IndexScheduler
import hu.padlas.feature.rules.RulesScheduler
import hu.padlas.feature.triage.TriageScheduler

/**
 * The layers only shrink each other's input if they actually run. Nothing here starts a
 * transfer or deletes anything: it schedules the measuring passes, so the Kidobó does not
 * sit on eleven thousand rows that were never looked at.
 *
 * Every call goes through unique work with a KEEP policy, so calling this on every Vault
 * visit is free once the work is queued.
 */
object PadlasBootstrap {

    fun onIndexReady(context: Context) {
        IndexScheduler.enqueueIncremental(context)
        DedupScheduler.enqueueScan(context)
        TriageScheduler.enqueueTriage(context)
        RulesScheduler.enqueueWeekly(context)
        ArchiveScheduler.enqueueReconcile(context)
    }
}

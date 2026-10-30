package hu.padlas.feature.audit

import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import hu.padlas.core.common.di.IoDispatcher
import hu.padlas.core.common.format.Format
import hu.padlas.core.common.result.outcomeOf
import hu.padlas.core.database.dao.AssetDao
import hu.padlas.core.database.dao.AuditDao
import hu.padlas.core.database.entity.AuditAction
import hu.padlas.core.database.entity.AuditEntry
import hu.padlas.core.media.MediaOps
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
internal class AuditViewModel @Inject constructor(
    private val auditDao: AuditDao,
    private val assetDao: AssetDao,
    private val mediaOps: MediaOps,
    private val killSwitch: KillSwitch,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    private val local = MutableStateFlow(LocalState())
    private var pendingUndo: UndoBatch? = null

    val state: StateFlow<LogUiState> = combine(
        auditDao.observeRecent(LIMIT),
        auditDao.observeReclaimedBytes(),
        local,
    ) { entries, reclaimed, localState ->
        LogUiState(
            loading = false,
            days = entries.groupByDay(LocalDate.now(ZoneId.systemDefault())),
            reclaimedBytes = reclaimed,
            undoRequest = localState.undoRequest,
            message = localState.message,
            messageOk = localState.messageOk,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LogUiState())

    /**
     * Only the two phase bin is reversible; a permanent delete never claims to be. A whole
     * batch comes back through one system dialog, exactly as it left (INV-3).
     */
    fun undo(entries: List<AuditEntry>) {
        val reversible = entries.filter { it.reversible && it.action == AuditAction.TRASHED && it.assetId != null }
        if (reversible.isEmpty() || pendingUndo != null) return
        viewModelScope.launch {
            val assets = withContext(io) {
                reversible.mapNotNull { it.assetId }.distinct()
                    .chunked(SQLITE_ARGUMENT_LIMIT)
                    .flatMap { chunk -> assetDao.byIds(chunk) }
            }
            if (assets.isEmpty()) {
                local.update { it.copy(message = "Ezek a fájlok már nincsenek a telefonon.", messageOk = false) }
                return@launch
            }
            // The log shows at most LIMIT entries, so one row always fits in one dialog; the
            // cap only guards against that ever changing.
            val sender = mediaOps.untrashRequest(
                assets.take(MediaOps.MAX_REQUEST).map { mediaOps.uriFor(it.mediaStoreId, it.isVideo) },
            )
            if (sender == null) {
                local.update { it.copy(message = "A visszaállítás most nem indítható.", messageOk = false) }
                return@launch
            }
            val found = assets.take(MediaOps.MAX_REQUEST).map { it.mediaStoreId }.toSet()
            pendingUndo = UndoBatch(
                entryIds = reversible.filter { it.assetId in found }.map { it.id },
                assetIds = found.toList(),
                intentSender = sender,
            )
            local.update { it.copy(undoRequest = pendingUndo) }
        }
    }

    fun onUndoResult(confirmed: Boolean) {
        val batch = pendingUndo
        pendingUndo = null
        local.update { it.copy(undoRequest = null) }
        if (batch == null || !confirmed) return
        viewModelScope.launch {
            // The files are back either way; a failed write (a full disk) must not take the
            // screen down with it.
            val recorded = withContext(io) {
                outcomeOf {
                    val now = System.currentTimeMillis()
                    batch.assetIds.chunked(SQLITE_ARGUMENT_LIMIT).forEach { chunk -> assetDao.markUntrashed(chunk) }
                    batch.entryIds.forEach { id -> auditDao.markConsumed(id) }
                    auditDao.insertAll(
                        batch.assetIds.map { assetId ->
                            AuditEntry(
                                timestamp = now,
                                action = AuditAction.UNTRASHED,
                                assetId = assetId,
                                sizeBytes = 0,
                                reversible = false,
                            )
                        },
                    )
                }.isSuccess
            }
            local.update {
                if (recorded) {
                    it.copy(message = "${Format.count(batch.assetIds.size)} fájl visszaállítva.", messageOk = true)
                } else {
                    it.copy(message = "Visszaállítva, de a napló nem frissült. Megtelt a tárhely?", messageOk = false)
                }
            }
        }
    }

    /** INV-5: the whole log leaves as CSV, not a filtered view of it. */
    suspend fun exportCsv(): String = withContext(io) {
        buildString {
            appendLine("timestamp;action;assetId;sizeBytes;reversible;note")
            auditDao.all().forEach { entry ->
                append(ISO.format(Instant.ofEpochMilli(entry.timestamp).atZone(ZoneId.systemDefault())))
                append(';')
                append(entry.action.name)
                append(';')
                append(entry.assetId?.toString().orEmpty())
                append(';')
                append(entry.sizeBytes)
                append(';')
                append(entry.reversible)
                append(';')
                appendLine(entry.note.orEmpty().replace(';', ','))
            }
        }
    }

    fun stopAllWork() {
        killSwitch.stopEverything()
        local.update { it.copy(message = "Minden háttérmunka leállt.", messageOk = true) }
    }

    fun message(text: String, ok: Boolean = true) = local.update { it.copy(message = text, messageOk = ok) }

    fun dismissMessage() = local.update { it.copy(message = null) }

    /**
     * A batch writes one row per file with one shared timestamp, so a thousand deleted
     * files read as one line, not as a thousand.
     */
    private fun List<AuditEntry>.groupByDay(today: LocalDate): List<LogDay> {
        val zone = ZoneId.systemDefault()
        return groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
            .map { (date, entries) ->
                val rows = entries
                    .groupBy { Triple(it.timestamp, it.action, it.note) }
                    .map { (key, batch) ->
                        LogRow(
                            key = "${key.first}-${key.second}-${batch.first().id}",
                            action = key.second,
                            timestamp = key.first,
                            entries = batch,
                            sizeBytes = batch.sumOf { it.sizeBytes },
                            note = key.third,
                        )
                    }
                LogDay(
                    key = date.toString(),
                    label = dayLabel(date, today),
                    rows = rows,
                    bytes = entries.filter { it.action in FREEING }.sumOf { it.sizeBytes },
                )
            }
    }

    private fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "Ma"
        today.minusDays(1) -> "Tegnap"
        else -> {
            val pattern = if (date.year == today.year) SAME_YEAR else OTHER_YEAR
            pattern.format(date).replaceFirstChar { it.titlecase(HU) }
        }
    }

    private data class LocalState(
        val undoRequest: UndoBatch? = null,
        val message: String? = null,
        val messageOk: Boolean = true,
    )

    private companion object {
        const val LIMIT = 500
        const val SQLITE_ARGUMENT_LIMIT = 900
        val HU: Locale = Locale.forLanguageTag("hu-HU")
        val ISO: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
        val SAME_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM d., EEEE", HU)
        val OTHER_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy. MMMM d.", HU)

        /** The same two actions the all-time total counts; nothing else frees bytes for good. */
        val FREEING = setOf(AuditAction.DELETED, AuditAction.GHOSTED)
    }
}

internal data class UndoBatch(
    val entryIds: List<Long>,
    val assetIds: List<Long>,
    val intentSender: IntentSender,
)

internal data class LogRow(
    val key: String,
    val action: AuditAction,
    val timestamp: Long,
    val entries: List<AuditEntry>,
    val sizeBytes: Long,
    val note: String?,
) {
    val count: Int get() = entries.size

    val undoable: Boolean
        get() = action == AuditAction.TRASHED && entries.any { it.reversible && it.assetId != null }
}

internal data class LogDay(
    val key: String,
    val label: String,
    val rows: List<LogRow>,
    /** Bytes freed for good that day; informational rows (index size, hash totals) do not count. */
    val bytes: Long,
)

internal data class LogUiState(
    val loading: Boolean = true,
    val days: List<LogDay> = emptyList(),
    val reclaimedBytes: Long = 0,
    val undoRequest: UndoBatch? = null,
    val message: String? = null,
    val messageOk: Boolean = true,
)

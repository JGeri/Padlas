package hu.padlas.feature.audit

import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import hu.padlas.core.common.di.IoDispatcher
import hu.padlas.core.database.dao.AssetDao
import hu.padlas.core.database.dao.AuditDao
import hu.padlas.core.database.entity.AuditAction
import hu.padlas.core.database.entity.AuditEntry
import hu.padlas.core.media.MediaOps
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
            days = entries.groupByDay(),
            reclaimedBytes = reclaimed,
            undoRequest = localState.undoRequest,
            message = localState.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LogUiState())

    /** Only the two phase bin is reversible; a permanent delete never claims to be. */
    fun undo(entry: AuditEntry) {
        if (!entry.reversible || entry.action != AuditAction.TRASHED) return
        val assetId = entry.assetId ?: return
        viewModelScope.launch {
            val asset = withContext(io) { assetDao.byId(assetId) }
            if (asset == null) {
                local.update { it.copy(message = "Az elem már nincs az indexben.") }
                return@launch
            }
            val sender = runCatching {
                mediaOps.untrashRequest(listOf(mediaOps.uriFor(asset.mediaStoreId, asset.isVideo)))
            }.getOrNull()
            if (sender == null) {
                local.update { it.copy(message = "A visszaállítás nem indítható.") }
                return@launch
            }
            pendingUndo = UndoBatch(entry.id, listOf(assetId), sender)
            local.update { it.copy(undoRequest = pendingUndo) }
        }
    }

    fun onUndoResult(confirmed: Boolean) {
        val batch = pendingUndo
        pendingUndo = null
        local.update { it.copy(undoRequest = null) }
        if (batch == null || !confirmed) return
        viewModelScope.launch {
            withContext(io) {
                assetDao.markUntrashed(batch.assetIds)
                auditDao.markConsumed(batch.entryId)
                auditDao.insert(
                    AuditEntry(
                        timestamp = System.currentTimeMillis(),
                        action = AuditAction.UNTRASHED,
                        assetId = batch.assetIds.firstOrNull(),
                        sizeBytes = 0,
                        reversible = false,
                    ),
                )
            }
            local.update { it.copy(message = "Visszaállítva.") }
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
        local.update { it.copy(message = "Minden háttérmunka leállt.") }
    }

    fun message(text: String) = local.update { it.copy(message = text) }

    fun dismissMessage() = local.update { it.copy(message = null) }

    private fun List<AuditEntry>.groupByDay(): List<LogDay> =
        groupBy { DAY.format(Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault())) }
            .map { (day, entries) ->
                LogDay(
                    label = day,
                    entries = entries,
                    bytes = entries.sumOf { it.sizeBytes },
                )
            }

    private data class LocalState(
        val undoRequest: UndoBatch? = null,
        val message: String? = null,
    )

    private companion object {
        const val LIMIT = 500
        val ISO: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
        val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy. MM. dd.")
    }
}

internal data class UndoBatch(
    val entryId: Long,
    val assetIds: List<Long>,
    val intentSender: IntentSender,
)

internal data class LogDay(
    val label: String,
    val entries: List<AuditEntry>,
    val bytes: Long,
)

internal data class LogUiState(
    val loading: Boolean = true,
    val days: List<LogDay> = emptyList(),
    val reclaimedBytes: Long = 0,
    val undoRequest: UndoBatch? = null,
    val message: String? = null,
)

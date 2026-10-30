package hu.padlas.feature.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import hu.padlas.core.database.dao.RuleDao
import hu.padlas.core.database.entity.RuleEntity
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
internal class RulesViewModel @Inject constructor(
    private val ruleDao: RuleDao,
    private val engine: RuleEngine,
    private val tracker: StorageDeltaTracker,
) : ViewModel() {

    private val local = MutableStateFlow(LocalState())

    val state: StateFlow<RulesUiState> = combine(
        ruleDao.observeAll(),
        local,
    ) { rules, localState ->
        RulesUiState(
            loading = false,
            rules = rules,
            dryRuns = localState.dryRuns,
            openDryRun = localState.openDryRun,
            messageOk = localState.messageOk,
            deltas = localState.deltas,
            busyRuleId = localState.busyRuleId,
            message = localState.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RulesUiState())

    init {
        launchSafely {
            engine.seedBuiltInsIfEmpty()
            local.update { it.copy(deltas = tracker.weeklyDeltas()) }
        }
    }

    fun dryRun(rule: RuleEntity) {
        launchSafely {
            local.update { it.copy(busyRuleId = rule.id) }
            val result = engine.dryRun(rule)
            local.update { current ->
                current.copy(
                    busyRuleId = null,
                    dryRuns = current.dryRuns + (rule.id to result),
                    openDryRun = rule.id,
                )
            }
        }
    }

    fun closeDryRun() = local.update { it.copy(openDryRun = null) }

    /** INV-6: a rule cannot be switched on before its dry run has been looked at. */
    fun setEnabled(rule: RuleEntity, enabled: Boolean) {
        if (enabled && local.value.dryRuns[rule.id] == null) {
            local.update { it.copy(message = "Előbb nézd meg, mit érintene.", messageOk = false) }
            return
        }
        launchSafely { ruleDao.setEnabled(rule.id, enabled) }
    }

    fun runNow(rule: RuleEntity) {
        launchSafely {
            local.update { it.copy(busyRuleId = rule.id) }
            val result = engine.apply(rule)
            local.update { it.copy(busyRuleId = null, message = result.message, messageOk = true) }
        }
    }

    fun delete(rule: RuleEntity) {
        launchSafely { ruleDao.delete(rule.id) }
    }

    fun refreshDeltas() {
        launchSafely {
            tracker.snapshot()
            local.update { it.copy(deltas = tracker.weeklyDeltas()) }
        }
    }

    fun dismissMessage() = local.update { it.copy(message = null) }

    /**
     * Everything here reads or writes the whole index. A query that fails has to end in a
     * message and a card that stops saying "Számolás…", not in a crashed app.
     */
    private fun launchSafely(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                local.update { it.copy(busyRuleId = null, message = FAILED, messageOk = false) }
            }
        }
    }

    private data class LocalState(
        val dryRuns: Map<Long, DryRunResult> = emptyMap(),
        val openDryRun: Long? = null,
        val deltas: List<BucketDelta> = emptyList(),
        val busyRuleId: Long? = null,
        val message: String? = null,
        val messageOk: Boolean = true,
    )

    private companion object {
        const val FAILED = "Ez most nem sikerült. Próbáld újra."
    }
}

internal data class RulesUiState(
    val loading: Boolean = true,
    val rules: List<RuleEntity> = emptyList(),
    val dryRuns: Map<Long, DryRunResult> = emptyMap(),
    val openDryRun: Long? = null,
    val deltas: List<BucketDelta> = emptyList(),
    val busyRuleId: Long? = null,
    val message: String? = null,
    /** False when the message is a refusal rather than a result. */
    val messageOk: Boolean = true,
)

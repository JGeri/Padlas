package hu.padlas.feature.sort

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Small, synchronous settings. SharedPreferences is enough here: a handful of values that
 * the setup screen writes right before the session screen reads them.
 */
@Singleton
class SwipePreferences @Inject constructor(@ApplicationContext context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    private val _deleteMode = MutableStateFlow(readDeleteMode())
    val deleteMode: StateFlow<DeleteMode> = _deleteMode.asStateFlow()

    private val _deleteAtEnd = MutableStateFlow(prefs.getBoolean(KEY_DELETE_AT_END, true))
    val deleteAtEnd: StateFlow<Boolean> = _deleteAtEnd.asStateFlow()

    fun setDeleteMode(mode: DeleteMode) {
        prefs.edit().putString(KEY_DELETE_MODE, mode.name).apply()
        _deleteMode.value = mode
    }

    fun setDeleteAtEnd(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DELETE_AT_END, enabled).apply()
        _deleteAtEnd.value = enabled
    }

    /**
     * The element the next deck grows out of (a month tile, a folder row, the start bar).
     * Memory only: it describes one transition, not a setting.
     */
    var heroKey: String? = null

    var hintSeen: Boolean
        get() = prefs.getBoolean(KEY_HINT, false)
        set(value) {
            prefs.edit().putBoolean(KEY_HINT, value).apply()
        }

    var lastFilter: SwipeFilter?
        get() {
            if (!prefs.contains(KEY_MEDIA)) return null
            return SwipeFilter(
                buckets = prefs.getString(KEY_BUCKETS, "").orEmpty()
                    .split(',').mapNotNull { it.toLongOrNull() }.toSet(),
                months = prefs.getString(KEY_MONTHS, "").orEmpty()
                    .split(',').filter { it.isNotBlank() }.toSet(),
                media = enumOr(prefs.getString(KEY_MEDIA, null), MediaFilter.ALL),
                onlyUnreviewed = prefs.getBoolean(KEY_UNREVIEWED, true),
                order = enumOr(prefs.getString(KEY_ORDER, null), SwipeOrder.NEWEST),
            )
        }
        set(value) {
            val editor = prefs.edit()
            if (value == null) {
                editor.remove(KEY_BUCKETS).remove(KEY_MONTHS).remove(KEY_MEDIA)
                    .remove(KEY_UNREVIEWED).remove(KEY_ORDER)
            } else {
                editor.putString(KEY_BUCKETS, value.buckets.joinToString(","))
                    .putString(KEY_MONTHS, value.months.joinToString(","))
                    .putString(KEY_MEDIA, value.media.name)
                    .putBoolean(KEY_UNREVIEWED, value.onlyUnreviewed)
                    .putString(KEY_ORDER, value.order.name)
            }
            editor.apply()
        }

    private fun readDeleteMode(): DeleteMode = enumOr(prefs.getString(KEY_DELETE_MODE, null), DeleteMode.TRASH)

    private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
        name?.let { value -> enumValues<T>().firstOrNull { it.name == value } } ?: fallback

    private companion object {
        const val NAME = "swipe"
        const val KEY_DELETE_MODE = "delete_mode"
        const val KEY_DELETE_AT_END = "delete_at_end"
        const val KEY_HINT = "hint_seen"
        const val KEY_BUCKETS = "filter_buckets"
        const val KEY_MONTHS = "filter_months"
        const val KEY_MEDIA = "filter_media"
        const val KEY_UNREVIEWED = "filter_unreviewed"
        const val KEY_ORDER = "filter_order"
    }
}

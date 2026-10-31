package hu.padlas.core.common.text

import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

/**
 * A sentence for the screen that is not bound to a Context yet. A view model says what it
 * means; the UI resolves it in the language the app runs in, which can change while the
 * view model lives. Arguments may themselves be [UiText].
 */
sealed interface UiText {

    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    data class Plural(@PluralsRes val id: Int, val count: Int, val args: List<Any> = emptyList()) : UiText

    /** Text that is already final: a file name, a message from the system. */
    data class Raw(val value: String) : UiText

    fun resolve(resources: Resources): String = when (this) {
        is Res -> resources.getString(id, *resolved(args, resources))
        is Plural -> resources.getQuantityString(id, count, *resolved(args, resources))
        is Raw -> value
    }

    private fun resolved(args: List<Any>, resources: Resources): Array<Any> =
        args.map { if (it is UiText) it.resolve(resources) else it }.toTypedArray()
}

fun uiText(@StringRes id: Int, vararg args: Any): UiText = UiText.Res(id, args.toList())

fun uiPlural(@PluralsRes id: Int, count: Int, vararg args: Any): UiText = UiText.Plural(id, count, args.toList())

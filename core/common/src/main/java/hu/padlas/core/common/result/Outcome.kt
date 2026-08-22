package hu.padlas.core.common.result

/** Deliberately not kotlin.Result: we want a failure that carries a user-facing message. */
sealed interface Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>
    data class Failure(val message: String, val cause: Throwable? = null) : Outcome<Nothing>

    val isSuccess: Boolean get() = this is Success

    fun valueOrNull(): T? = (this as? Success)?.value
}

inline fun <T> outcomeOf(message: String = "Ismeretlen hiba.", block: () -> T): Outcome<T> = try {
    Outcome.Success(block())
} catch (t: Throwable) {
    if (t is kotlinx.coroutines.CancellationException) throw t
    Outcome.Failure(t.message ?: message, t)
}

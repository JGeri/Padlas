package hu.padlas.feature.archive.drive

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import dagger.hilt.android.qualifiers.ApplicationContext
import hu.padlas.core.common.text.UiText
import hu.padlas.core.common.text.uiText
import hu.padlas.feature.archive.R
import hu.padlas.feature.archive.data.ArchivePreferences
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface AuthState {
    /** No client id was supplied by the app module; nothing can be attempted. */
    data object Unconfigured : AuthState
    data object Disconnected : AuthState
    data class ConsentNeeded(val intent: PendingIntent) : AuthState
    data object Connected : AuthState
    data class Failed(val message: UiText) : AuthState
}

/**
 * Authorization through the Identity API, not the deprecated GoogleSignIn. The scope is
 * `drive.file`, which only ever sees files this app created - that keeps it off Google's
 * sensitive list, which is the difference between shipping and a multi-week review.
 */
@Singleton
class DriveAuth @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: ArchivePreferences,
) {

    private val _state = MutableStateFlow<AuthState>(AuthState.Disconnected)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private val mutex = Mutex()
    private var cachedToken: String? = null
    private var cachedUntil: Long = 0L

    /** Supplied by the app module as a string resource; never compiled into this module. */
    private val webClientId: String by lazy {
        val id = context.resources.getIdentifier(CLIENT_ID_RESOURCE, "string", context.packageName)
        if (id == 0) "" else runCatching { context.getString(id) }.getOrDefault("").trim()
    }

    val isConfigured: Boolean get() = webClientId.isNotEmpty()

    /**
     * Returns a usable bearer token or null. A null is never silent: the state flow
     * carries either the consent intent to show or the reason it cannot be obtained.
     */
    suspend fun accessToken(): String? = mutex.withLock {
        if (!isConfigured) {
            _state.value = AuthState.Unconfigured
            return null
        }
        val cached = cachedToken
        if (cached != null && System.currentTimeMillis() < cachedUntil) return cached
        val result = runCatching { authorize() }.getOrElse { failure ->
            _state.value = AuthState.Failed(failure.reason(R.string.archive_auth_failed))
            return null
        }
        val pending = result.pendingIntent
        if (result.hasResolution() && pending != null) {
            _state.value = AuthState.ConsentNeeded(pending)
            return null
        }
        adopt(result)
    }

    /** Result of the consent screen the app module launched from [AuthState.ConsentNeeded]. */
    suspend fun onConsentResult(data: Intent?): Boolean = mutex.withLock {
        val result = runCatching {
            Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)
        }.getOrElse { failure ->
            _state.value = AuthState.Failed(failure.reason(R.string.archive_auth_no_consent))
            return false
        }
        adopt(result) != null
    }

    suspend fun disconnect() = mutex.withLock {
        cachedToken = null
        cachedUntil = 0L
        preferences.setDriveGranted(false)
        _state.value = AuthState.Disconnected
    }

    suspend fun refreshState() {
        if (!isConfigured) {
            _state.value = AuthState.Unconfigured
            return
        }
        if (preferences.driveGranted()) _state.value = AuthState.Connected
    }

    private suspend fun authorize(): AuthorizationResult {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
            .requestOfflineAccess(webClientId, false)
            .build()
        return Identity.getAuthorizationClient(context).authorize(request).awaitResult()
    }

    /**
     * Play Services hands back a Task; the coroutines-play-services bridge is not a
     * dependency of this module, so the two callbacks are wired by hand.
     */
    private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { value -> continuation.resume(value) }
        addOnFailureListener { failure -> continuation.resumeWithException(failure) }
        addOnCanceledListener { continuation.cancel() }
    }

    private suspend fun adopt(result: AuthorizationResult): String? {
        val token = result.accessToken
        if (token.isNullOrBlank()) {
            _state.value = AuthState.Failed(uiText(R.string.archive_auth_no_token))
            return null
        }
        cachedToken = token
        cachedUntil = System.currentTimeMillis() + TOKEN_LIFETIME_MS
        preferences.setDriveGranted(true)
        _state.value = AuthState.Connected
        return token
    }

    /** Play Services' own words when it has any, ours when it does not. */
    private fun Throwable.reason(@StringRes fallback: Int): UiText = message?.let { UiText.Raw(it) } ?: uiText(fallback)

    private companion object {
        const val CLIENT_ID_RESOURCE = "drive_web_client_id"
        const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
        /** Google issues one-hour tokens; expiring early costs one silent re-authorize. */
        const val TOKEN_LIFETIME_MS = 45L * 60L * 1000L
    }
}

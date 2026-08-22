package hu.padlas.feature.archive.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.archiveStore: DataStore<Preferences> by preferencesDataStore(name = "padlas_archive")

/**
 * Small, boring state that has to survive process death: whether the Drive grant is
 * still believed to hold, whether the desktop-first note has been read, and the folder
 * ids we would otherwise re-resolve with a network round trip on every single upload.
 */
@Singleton
class ArchivePreferences @Inject constructor(@ApplicationContext private val context: Context) {

    private val store get() = context.archiveStore

    private val safe: Flow<Preferences>
        get() = store.data.catch { failure ->
            if (failure is IOException) emit(emptyPreferences()) else throw failure
        }

    val driveGrantedFlow: Flow<Boolean> get() = safe.map { it[DRIVE_GRANTED] ?: false }

    val desktopNoticeReadFlow: Flow<Boolean> get() = safe.map { it[DESKTOP_NOTICE] ?: false }

    suspend fun driveGranted(): Boolean = driveGrantedFlow.first()

    suspend fun setDriveGranted(granted: Boolean) {
        store.edit { it[DRIVE_GRANTED] = granted }
    }

    suspend fun markDesktopNoticeRead() {
        store.edit { it[DESKTOP_NOTICE] = true }
    }

    suspend fun folderId(path: String): String? = safe.first()[folderKey(path)]

    suspend fun setFolderId(path: String, id: String) {
        store.edit { it[folderKey(path)] = id }
    }

    suspend fun manifestFileId(path: String): String? = safe.first()[manifestKey(path)]

    suspend fun setManifestFileId(path: String, id: String) {
        store.edit { it[manifestKey(path)] = id }
    }

    /** Called by the global kill switch: the next run resolves everything again. */
    suspend fun forgetFolders() {
        store.edit { preferences ->
            preferences.asMap().keys
                .filter { it.name.startsWith(FOLDER_PREFIX) || it.name.startsWith(MANIFEST_PREFIX) }
                .forEach { key -> preferences -= key }
        }
    }

    private fun folderKey(path: String) = stringPreferencesKey(FOLDER_PREFIX + path)

    private fun manifestKey(path: String) = stringPreferencesKey(MANIFEST_PREFIX + path)

    private companion object {
        const val FOLDER_PREFIX = "folder:"
        const val MANIFEST_PREFIX = "manifest:"
        val DRIVE_GRANTED = booleanPreferencesKey("drive_granted")
        val DESKTOP_NOTICE = booleanPreferencesKey("desktop_notice_read")
    }
}

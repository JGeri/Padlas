package hu.padlas.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import hu.padlas.core.database.entity.AssetState
import hu.padlas.core.database.entity.SwipeChoice
import hu.padlas.core.database.entity.SwipeDecisionEntity
import java.io.File
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The index can be rebuilt, the audit log and the cards cannot, so destructive migration is
 * off: every schema step has to carry the old rows across. Runs against the JSON schemas
 * Room exported into `core/database/schemas`.
 *
 * The driver flavour of the helper on purpose: the SupportSQLite one compares database names
 * after the last '/', which a Windows path does not have.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    private val file: File = ApplicationProvider.getApplicationContext<Context>().getDatabasePath(DB)

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        file,
        AndroidSQLiteDriver(),
        PadlasDatabase::class,
    )

    @Test
    fun `version 1 becomes version 2 with its rows intact and the exported schema matched`() {
        helper.createDatabase(1).use { db ->
            db.insertAsset(id = 1, name = "IMG_1.jpg", state = "TRASHED")
            db.insertAsset(id = 2, name = "IMG_2.jpg", state = "LOCAL")
            db.execSQL(
                "INSERT INTO audit_entry (timestamp, action, assetId, sizeBytes, reversible) " +
                    "VALUES (100, 'TRASHED', 1, 4000, 1)",
            )
        }

        helper.runMigrationsAndValidate(2, emptyList()).use { db ->
            assertThat(db.text("SELECT displayName FROM asset WHERE mediaStoreId = 1")).isEqualTo("IMG_1.jpg")
            assertThat(db.text("SELECT state FROM asset WHERE mediaStoreId = 1")).isEqualTo("TRASHED")
            assertThat(db.long("SELECT COUNT(*) FROM asset")).isEqualTo(2)
            assertThat(db.long("SELECT COUNT(*) FROM audit_entry WHERE reversible = 1")).isEqualTo(1)
            assertThat(db.long("SELECT COUNT(*) FROM swipe_decision")).isEqualTo(0)
        }
    }

    @Test
    fun `the app opens a version 1 file through the auto migration and can swipe on it`() = runTest {
        helper.createDatabase(1).use { db -> db.insertAsset(id = 7, name = "IMG_7.jpg", state = "LOCAL") }

        val room = Room.databaseBuilder(ApplicationProvider.getApplicationContext<Context>(), PadlasDatabase::class.java, file.path)
            .setDriver(AndroidSQLiteDriver())
            .setQueryCoroutineContext(StandardTestDispatcher(testScheduler))
            .build()
        try {
            assertThat(room.assetDao().byId(7)?.state).isEqualTo(AssetState.LOCAL)

            room.swipeDao().upsert(SwipeDecisionEntity(7, SwipeChoice.DELETE, decidedAt = 1))

            assertThat(room.swipeDao().pendingAssets().map { it.mediaStoreId }).containsExactly(7L)
        } finally {
            room.close()
        }
    }

    /** Raw SQL on purpose: at version 1 there is no DAO to lean on, only the columns of 1.json. */
    private fun SQLiteConnection.insertAsset(id: Long, name: String, state: String) = execSQL(
        "INSERT INTO asset (mediaStoreId, uri, relativePath, displayName, mimeType, sizeBytes, dateTaken, " +
            "dateModified, width, height, bucketId, bucketName, isFavorite, valueClass, classifiedBy, state, " +
            "protectedManually) VALUES ($id, 'content://media/external/images/media/$id', 'DCIM/Camera/', " +
            "'$name', 'image/jpeg', 4000, 1721044800000, 1721044800, 4000, 3000, 1, 'Camera', 0, " +
            "'REPLACEABLE', 'path', '$state', 0)",
    )

    private fun SQLiteConnection.text(sql: String): String = prepare(sql).use { check(it.step()); it.getText(0) }

    private fun SQLiteConnection.long(sql: String): Long = prepare(sql).use { check(it.step()); it.getLong(0) }

    private companion object {
        const val DB = "migration-test.db"
    }
}

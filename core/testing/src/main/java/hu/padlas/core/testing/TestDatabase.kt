package hu.padlas.core.testing

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import hu.padlas.core.database.PadlasDatabase
import kotlinx.coroutines.CoroutineDispatcher

/**
 * An in-memory database whose queries, transactions and Flow invalidations all run on
 * [dispatcher]. Room then owns no thread of its own, so a test that drives the scheduler
 * sees every write and every re-query a view model reacts to, in order.
 */
fun testDatabase(dispatcher: CoroutineDispatcher): PadlasDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), PadlasDatabase::class.java)
        .setQueryCoroutineContext(dispatcher)
        // Robolectric runs the test body on the main looper's thread.
        .allowMainThreadQueries()
        .build()

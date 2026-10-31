package hu.padlas.core.testing

import android.database.sqlite.SQLiteFullException
import hu.padlas.core.database.dao.AssetDao

/**
 * A phone being cleaned is often a full one. Reads work, but the writes a delete flow makes
 * once the system dialog came back (binning, restoring, dropping rows) fail the way SQLite
 * fails on a full disk.
 */
class FullDiskAssetDao(delegate: AssetDao) : AssetDao by delegate {

    override suspend fun markTrashed(ids: List<Long>, now: Long) {
        throw SQLiteFullException(FULL)
    }

    override suspend fun markUntrashed(ids: List<Long>) {
        throw SQLiteFullException(FULL)
    }

    override suspend fun deleteByIds(ids: List<Long>) {
        throw SQLiteFullException(FULL)
    }

    private companion object {
        const val FULL = "database or disk is full (code 13 SQLITE_FULL)"
    }
}

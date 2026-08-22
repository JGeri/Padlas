package hu.padlas.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

/**
 * Standalone FTS table rather than a content-linked one: OCR text arrives long after the
 * asset row exists, and cards outlive their asset entirely.
 */
@Entity(tableName = "asset_search")
@Fts4
data class AssetSearchEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "rowid") val rowId: Long = 0,
    val assetId: String,
    val text: String,
)

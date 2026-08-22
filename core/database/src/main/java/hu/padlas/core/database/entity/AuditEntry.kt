package hu.padlas.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "audit_entry", indices = [Index("timestamp"), Index("assetId"), Index("action")])
data class AuditEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val action: AuditAction,
    val assetId: Long?,
    val sizeBytes: Long = 0,
    val reversible: Boolean = false,
    val payloadJson: String? = null,
    val note: String? = null,
)

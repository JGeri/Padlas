package hu.padlas.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rule")
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Serialized predicate tree, see hu.padlas.feature.rules.Predicate. */
    val predicateJson: String,
    val action: RuleAction,
    val enabled: Boolean = false,
    val lastRunAt: Long? = null,
    val lastMatchCount: Int = 0,
    val lastMatchBytes: Long = 0,
    val builtIn: Boolean = false,
)

package hu.padlas.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** What the user swiped. DELETE stays here until the system dialog confirms the batch. */
enum class SwipeChoice { KEEP, DELETE, FAVORITE }

@Entity(tableName = "swipe_decision", indices = [Index("choice"), Index("decidedAt")])
data class SwipeDecisionEntity(
    @PrimaryKey val assetId: Long,
    val choice: SwipeChoice,
    val decidedAt: Long,
)

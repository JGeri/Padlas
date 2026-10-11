package hu.padlas.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import hu.padlas.core.database.converter.Converters
import hu.padlas.core.database.dao.AssetDao
import hu.padlas.core.database.dao.AuditDao
import hu.padlas.core.database.dao.CardDao
import hu.padlas.core.database.dao.DupDao
import hu.padlas.core.database.dao.RuleDao
import hu.padlas.core.database.dao.SearchDao
import hu.padlas.core.database.dao.StorageDao
import hu.padlas.core.database.dao.SwipeDao
import hu.padlas.core.database.dao.TranscodeJobDao
import hu.padlas.core.database.dao.UploadJobDao
import hu.padlas.core.database.entity.Asset
import hu.padlas.core.database.entity.AssetSearchEntity
import hu.padlas.core.database.entity.AuditEntry
import hu.padlas.core.database.entity.BucketSnapshotEntity
import hu.padlas.core.database.entity.CardEntity
import hu.padlas.core.database.entity.DupGroupEntity
import hu.padlas.core.database.entity.DupMemberEntity
import hu.padlas.core.database.entity.RuleEntity
import hu.padlas.core.database.entity.StorageSnapshotEntity
import hu.padlas.core.database.entity.SwipeDecisionEntity
import hu.padlas.core.database.entity.TranscodeJobEntity
import hu.padlas.core.database.entity.UploadJobEntity

@Database(
    entities = [
        Asset::class,
        DupGroupEntity::class,
        DupMemberEntity::class,
        TranscodeJobEntity::class,
        UploadJobEntity::class,
        RuleEntity::class,
        AuditEntry::class,
        CardEntity::class,
        AssetSearchEntity::class,
        StorageSnapshotEntity::class,
        BucketSnapshotEntity::class,
        SwipeDecisionEntity::class,
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
@TypeConverters(Converters::class)
abstract class PadlasDatabase : RoomDatabase() {
    abstract fun assetDao(): AssetDao
    abstract fun dupDao(): DupDao
    abstract fun transcodeJobDao(): TranscodeJobDao
    abstract fun uploadJobDao(): UploadJobDao
    abstract fun ruleDao(): RuleDao
    abstract fun auditDao(): AuditDao
    abstract fun cardDao(): CardDao
    abstract fun searchDao(): SearchDao
    abstract fun storageDao(): StorageDao
    abstract fun swipeDao(): SwipeDao

    companion object {
        const val NAME = "padlas.db"
    }
}

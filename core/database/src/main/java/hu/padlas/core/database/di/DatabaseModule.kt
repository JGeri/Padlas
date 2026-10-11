package hu.padlas.core.database.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import hu.padlas.core.database.PadlasDatabase
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
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): PadlasDatabase =
        Room.databaseBuilder(context, PadlasDatabase::class.java, PadlasDatabase.NAME)
            // The index can always be rebuilt from MediaStore; the audit log and the
            // cards cannot, so destructive migration stays off.
            .build()

    @Provides fun assetDao(db: PadlasDatabase): AssetDao = db.assetDao()

    @Provides fun dupDao(db: PadlasDatabase): DupDao = db.dupDao()

    @Provides fun transcodeJobDao(db: PadlasDatabase): TranscodeJobDao = db.transcodeJobDao()

    @Provides fun uploadJobDao(db: PadlasDatabase): UploadJobDao = db.uploadJobDao()

    @Provides fun ruleDao(db: PadlasDatabase): RuleDao = db.ruleDao()

    @Provides fun auditDao(db: PadlasDatabase): AuditDao = db.auditDao()

    @Provides fun cardDao(db: PadlasDatabase): CardDao = db.cardDao()

    @Provides fun searchDao(db: PadlasDatabase): SearchDao = db.searchDao()

    @Provides fun storageDao(db: PadlasDatabase): StorageDao = db.storageDao()

    @Provides fun swipeDao(db: PadlasDatabase): SwipeDao = db.swipeDao()
}

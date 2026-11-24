package com.prism.docs.core.data.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.prism.docs.core.data.db.CollectionDao
import com.prism.docs.core.data.db.DocumentIndexDao
import com.prism.docs.core.data.db.IndexerStateDao
import com.prism.docs.core.data.db.PrismDatabase
import com.prism.docs.core.data.db.PrismMigrations
import com.prism.docs.core.data.db.RecycleBinDao
import com.prism.docs.core.data.db.TagDao
import com.prism.docs.core.data.storage.DocumentStorageGateway
import com.prism.docs.core.data.storage.UnifiedDocumentStorageGateway
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

  @Provides
  @Singleton
  fun providePrismDatabase(@ApplicationContext context: Context): PrismDatabase {
    return Room.databaseBuilder(
        context,
        PrismDatabase::class.java,
        "prism_docs.db",
      )
      .addMigrations(*PrismMigrations.ALL_MIGRATIONS)
      .build()
  }

  @Provides
  fun provideDocumentIndexDao(database: PrismDatabase): DocumentIndexDao =
    database.documentIndexDao()

  @Provides
  fun provideCollectionDao(database: PrismDatabase): CollectionDao = database.collectionDao()

  @Provides
  fun provideTagDao(database: PrismDatabase): TagDao = database.tagDao()

  @Provides
  fun provideRecycleBinDao(database: PrismDatabase): RecycleBinDao = database.recycleBinDao()

  @Provides
  fun provideIndexerStateDao(database: PrismDatabase): IndexerStateDao = database.indexerStateDao()

  @Provides
  @Singleton
  fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
    WorkManager.getInstance(context)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class StorageBindingModule {
  @Binds
  @Singleton
  abstract fun bindDocumentStorageGateway(
    impl: UnifiedDocumentStorageGateway,
  ): DocumentStorageGateway
}

package com.prism.docs.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
  entities = [
    IndexedDocumentEntity::class,
    CollectionEntity::class,
    DocumentCollectionCrossRef::class,
    TagEntity::class,
    DocumentTagCrossRef::class,
    RecycleBinEntryEntity::class,
    IndexerStateEntity::class,
  ],
  version = 4,
  exportSchema = true,
)
abstract class PrismDatabase : RoomDatabase() {
  abstract fun documentIndexDao(): DocumentIndexDao
  abstract fun collectionDao(): CollectionDao
  abstract fun tagDao(): TagDao
  abstract fun recycleBinDao(): RecycleBinDao
  abstract fun indexerStateDao(): IndexerStateDao
}

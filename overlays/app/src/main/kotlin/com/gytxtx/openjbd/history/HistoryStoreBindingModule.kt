package com.gytxtx.openjbd.history

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Binds the local SQLite implementation to the [HistoryStore] interface so screens depend on the
 * contract rather than the storage engine. Kept in its own file: a DI binding added inside a
 * screen file risks a duplicate-module clash when another surface injects the same contract.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class HistoryStoreBindingModule {
    @Binds
    abstract fun bindHistoryStore(store: SqliteHistoryStore): HistoryStore
}

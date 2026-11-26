package com.prism.docs

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.prism.docs.core.data.indexer.DocumentIndexScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PrismApplication : Application(), Configuration.Provider {

  @Inject lateinit var workerFactory: HiltWorkerFactory

  @Inject lateinit var documentIndexScheduler: DocumentIndexScheduler

  override val workManagerConfiguration: Configuration
    get() =
      Configuration.Builder()
        .setWorkerFactory(workerFactory)
        .build()

  override fun onCreate() {
    super.onCreate()
    runCatching {
      documentIndexScheduler.ensurePeriodicIndexingScheduled()
      documentIndexScheduler.triggerStartupSync()
    }
  }
}

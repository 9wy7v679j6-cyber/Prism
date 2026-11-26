package com.prism.docs.core.data.indexer

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.prism.docs.core.data.db.IndexerStateDao
import com.prism.docs.core.data.model.IndexWorkStatus
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine

@Singleton
class DocumentIndexScheduler
@Inject
constructor(
  private val workManagerProvider: Provider<WorkManager>,
  private val indexerStateDao: IndexerStateDao,
) {
  private val workManager: WorkManager
    get() = workManagerProvider.get()
  companion object {
    const val UNIQUE_PERIODIC_WORK_NAME = "prism_periodic_document_index"
    const val UNIQUE_ON_DEMAND_WORK_NAME = "prism_on_demand_document_index"
  }

  /**
   * Schedules battery-aware periodic indexing (every 6 hours) using KEEP policy so repeated
   * app launches do not reset the schedule.
   */
  fun ensurePeriodicIndexingScheduled() {
    val constraints = Constraints.Builder().setRequiresBatteryNotLow(true).build()
    val request =
      PeriodicWorkRequestBuilder<DocumentIndexWorker>(6, TimeUnit.HOURS)
        .setConstraints(constraints)
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
        .build()

    workManager.enqueueUniquePeriodicWork(
      UNIQUE_PERIODIC_WORK_NAME,
      ExistingPeriodicWorkPolicy.KEEP,
      request,
    )
  }

  /**
   * Triggers an immediate incremental scan (e.g., after granting storage permission, adding a
   * SAF folder, or pull-to-refresh).
   */
  fun triggerImmediateSync() {
    val request =
      OneTimeWorkRequestBuilder<DocumentIndexWorker>()
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
        .build()

    workManager.enqueueUniqueWork(
      UNIQUE_ON_DEMAND_WORK_NAME,
      ExistingWorkPolicy.REPLACE,
      request,
    )
  }

  /**
   * Queues a single incremental re-index on app launch using KEEP policy so upgraded installs
   * prune any stale directory rows without interrupting an already-running scan.
   */
  fun triggerStartupSync() {
    val request =
      OneTimeWorkRequestBuilder<DocumentIndexWorker>()
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
        .build()

    workManager.enqueueUniqueWork(
      UNIQUE_ON_DEMAND_WORK_NAME,
      ExistingWorkPolicy.KEEP,
      request,
    )
  }

  fun observeIndexStatus(): Flow<IndexWorkStatus> {
    return combine(
      workManager.getWorkInfosForUniqueWorkFlow(UNIQUE_ON_DEMAND_WORK_NAME),
      indexerStateDao.observeState(),
    ) { workInfos, stateEntity ->
      val activeInfo = workInfos.firstOrNull { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
      val isRunning = activeInfo != null
      val scannedProgress =
        activeInfo?.progress?.getInt(DocumentIndexWorker.KEY_SCANNED_COUNT, 0)
          ?: stateEntity?.indexedDocumentCount
          ?: 0
      val phaseLabel =
        activeInfo?.progress?.getString(DocumentIndexWorker.KEY_PHASE)
          ?: if (isRunning) "Queued" else stateEntity?.status ?: "Idle"

      IndexWorkStatus(
        isRunning = isRunning,
        scannedCount = scannedProgress,
        lastCompletedEpochMs = stateEntity?.lastFullScanEpochMs ?: 0L,
        totalIndexedCount = stateEntity?.indexedDocumentCount ?: 0,
        phaseLabel = phaseLabel,
      )
    }.catch {
      emit(IndexWorkStatus())
    }
  }
}

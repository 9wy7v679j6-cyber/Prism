package com.prism.docs.core.data.indexer

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

@HiltWorker
class DocumentIndexWorker
@AssistedInject
constructor(
  @Assisted appContext: Context,
  @Assisted workerParams: WorkerParameters,
  private val indexer: IncrementalDocumentIndexer,
) : CoroutineWorker(appContext, workerParams) {

  companion object {
    const val KEY_SCANNED_COUNT = "scanned_count"
    const val KEY_INSERTED_COUNT = "inserted_count"
    const val KEY_UPDATED_COUNT = "updated_count"
    const val KEY_REMOVED_COUNT = "removed_count"
    const val KEY_PHASE = "phase"
  }

  override suspend fun doWork(): Result {
    return try {
      setProgress(workDataOf(KEY_SCANNED_COUNT to 0, KEY_PHASE to "Scanning storage"))
      val summary =
        indexer.runIncrementalSync { count ->
          setProgress(workDataOf(KEY_SCANNED_COUNT to count, KEY_PHASE to "Indexing ($count)"))
        }
      val output: Data =
        workDataOf(
          KEY_SCANNED_COUNT to summary.scannedCount,
          KEY_INSERTED_COUNT to summary.insertedCount,
          KEY_UPDATED_COUNT to summary.updatedCount,
          KEY_REMOVED_COUNT to summary.removedCount,
          KEY_PHASE to "Completed",
        )
      Result.success(output)
    } catch (ce: CancellationException) {
      throw ce
    } catch (e: SecurityException) {
      Result.failure(workDataOf(KEY_PHASE to "Permission revoked"))
    } catch (e: Exception) {
      if (runAttemptCount < 3) Result.retry() else Result.failure()
    }
  }
}

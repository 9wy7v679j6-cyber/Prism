package com.prism.docs.core.data.duplicates

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/**
 * Background WorkManager job executing Prism's 3-stage duplicate scan with progress updates
 * and cooperative cancellation.
 */
@HiltWorker
class DuplicateScanWorker
@AssistedInject
constructor(
  @Assisted appContext: Context,
  @Assisted params: WorkerParameters,
  private val duplicateFinderEngine: DuplicateFinderEngine,
) : CoroutineWorker(appContext, params) {

  override suspend fun doWork(): Result {
    return try {
      var lastStage: DuplicateScanStage? = null
      var lastEmitNanos = 0L
      val result =
        duplicateFinderEngine.scanActiveDuplicates { progress ->
          val nowNanos = System.nanoTime()
          val isBoundary =
            progress.stage != lastStage ||
              (progress.totalCandidates > 0 && progress.processedCount >= progress.totalCandidates)
          if (isBoundary || nowNanos - lastEmitNanos >= PROGRESS_THROTTLE_NS) {
            lastStage = progress.stage
            lastEmitNanos = nowNanos
            setProgress(
              workDataOf(
                KEY_STAGE to progress.stage.name,
                KEY_TOTAL_CANDIDATES to progress.totalCandidates,
                KEY_PROCESSED_COUNT to progress.processedCount,
                KEY_SIZE_GROUPS to progress.sizeGroupCount,
                KEY_PARTIAL_MATCHES to progress.partialMatchCount,
                KEY_CONFIRMED_GROUPS to progress.confirmedGroupCount,
                KEY_RECLAIMABLE_BYTES to progress.reclaimableBytes,
              )
            )
          }
        }
      Result.success(
        workDataOf(
          KEY_STAGE to DuplicateScanStage.COMPLETED.name,
          KEY_CONFIRMED_GROUPS to result.groups.size,
          KEY_RECLAIMABLE_BYTES to result.totalReclaimableBytes,
          KEY_SIZE_GROUPS to result.sizeCandidateCount,
          KEY_PARTIAL_MATCHES to result.partialCandidateCount,
          KEY_FULL_HASHED_COUNT to result.fullHashedCount,
        )
      )
    } catch (ce: CancellationException) {
      throw ce
    } catch (t: Throwable) {
      Result.failure(workDataOf(KEY_ERROR_MESSAGE to (t.message ?: "Duplicate scan failed")))
    }
  }

  companion object {
    private const val PROGRESS_THROTTLE_NS = 150_000_000L
    const val UNIQUE_WORK_NAME = "prism_duplicate_finder_scan"
    const val KEY_STAGE = "stage"
    const val KEY_TOTAL_CANDIDATES = "total_candidates"
    const val KEY_PROCESSED_COUNT = "processed_count"
    const val KEY_SIZE_GROUPS = "size_groups"
    const val KEY_PARTIAL_MATCHES = "partial_matches"
    const val KEY_CONFIRMED_GROUPS = "confirmed_groups"
    const val KEY_RECLAIMABLE_BYTES = "reclaimable_bytes"
    const val KEY_FULL_HASHED_COUNT = "full_hashed_count"
    const val KEY_ERROR_MESSAGE = "error_message"
  }
}

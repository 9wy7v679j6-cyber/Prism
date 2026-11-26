package com.prism.docs.core.data.duplicates

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.prism.docs.core.data.model.FileOperationResult
import com.prism.docs.core.data.storage.FileOperationsManager
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Coordinates WorkManager-backed Duplicate Finder scans, live progress observation,
 * cancellation, and Recycle-Bin-only duplicate cleanup.
 */
@Singleton
class DuplicateScanScheduler
@Inject
constructor(
  private val workManagerProvider: Provider<WorkManager>,
  private val duplicateFinderEngine: DuplicateFinderEngine,
  private val fileOperationsManager: FileOperationsManager,
) {
  private val workManager: WorkManager
    get() = workManagerProvider.get()

  fun startScan() {
    val request = OneTimeWorkRequestBuilder<DuplicateScanWorker>().build()
    workManager.enqueueUniqueWork(
      DuplicateScanWorker.UNIQUE_WORK_NAME,
      ExistingWorkPolicy.REPLACE,
      request,
    )
  }

  fun cancelScan() {
    workManager.cancelUniqueWork(DuplicateScanWorker.UNIQUE_WORK_NAME)
  }

  fun observeDuplicateGroups(): Flow<List<DuplicateFileGroup>> =
    duplicateFinderEngine.observeConfirmedDuplicateGroups()

  fun observeScanProgress(): Flow<DuplicateScanProgress> {
    return combine(
      workManager.getWorkInfosForUniqueWorkFlow(DuplicateScanWorker.UNIQUE_WORK_NAME),
      duplicateFinderEngine.observeConfirmedDuplicateGroups(),
    ) { workInfos, groups ->
      val latest = workInfos.firstOrNull()
      val groupCount = groups.size
      val reclaimableBytes = groups.sumOf { it.reclaimableBytes }

      if (latest == null) {
        return@combine DuplicateScanProgress(
          stage = DuplicateScanStage.IDLE,
          confirmedGroupCount = groupCount,
          reclaimableBytes = reclaimableBytes,
        )
      }

      when (latest.state) {
        WorkInfo.State.ENQUEUED,
        WorkInfo.State.BLOCKED ->
          DuplicateScanProgress(
            stage = DuplicateScanStage.GROUPING_BY_SIZE,
            confirmedGroupCount = groupCount,
            reclaimableBytes = reclaimableBytes,
          )
        WorkInfo.State.RUNNING -> {
          val p = latest.progress
          val stage =
            runCatching {
                DuplicateScanStage.valueOf(
                  p.getString(DuplicateScanWorker.KEY_STAGE)
                    ?: DuplicateScanStage.GROUPING_BY_SIZE.name
                )
              }
              .getOrDefault(DuplicateScanStage.GROUPING_BY_SIZE)
          DuplicateScanProgress(
            stage = stage,
            totalCandidates = p.getInt(DuplicateScanWorker.KEY_TOTAL_CANDIDATES, 0),
            processedCount = p.getInt(DuplicateScanWorker.KEY_PROCESSED_COUNT, 0),
            sizeGroupCount = p.getInt(DuplicateScanWorker.KEY_SIZE_GROUPS, 0),
            partialMatchCount = p.getInt(DuplicateScanWorker.KEY_PARTIAL_MATCHES, 0),
            confirmedGroupCount = groupCount,
            reclaimableBytes = reclaimableBytes,
          )
        }
        WorkInfo.State.SUCCEEDED ->
          DuplicateScanProgress(
            stage = DuplicateScanStage.COMPLETED,
            sizeGroupCount = latest.outputData.getInt(DuplicateScanWorker.KEY_SIZE_GROUPS, 0),
            partialMatchCount = latest.outputData.getInt(DuplicateScanWorker.KEY_PARTIAL_MATCHES, 0),
            confirmedGroupCount = groupCount,
            reclaimableBytes = reclaimableBytes,
          )
        WorkInfo.State.CANCELLED ->
          DuplicateScanProgress(
            stage = DuplicateScanStage.CANCELLED,
            confirmedGroupCount = groupCount,
            reclaimableBytes = reclaimableBytes,
          )
        WorkInfo.State.FAILED ->
          DuplicateScanProgress(
            stage = DuplicateScanStage.FAILED,
            confirmedGroupCount = groupCount,
            reclaimableBytes = reclaimableBytes,
            errorMessage = latest.outputData.getString(DuplicateScanWorker.KEY_ERROR_MESSAGE),
          )
      }
    }
  }

  /**
   * Always routes duplicate deletions through [FileOperationsManager.moveToRecycleBin].
   * Never permanently deletes files directly.
   */
  suspend fun moveNonNewestToRecycleBin(groups: List<DuplicateFileGroup>): FileOperationResult {
    val idsToTrash = DuplicateFinderEngine.selectDocumentsToTrash(groups)
    if (idsToTrash.isEmpty()) {
      return FileOperationResult(success = false, message = "No duplicate copies to move to Bin")
    }
    return fileOperationsManager.moveToRecycleBin(idsToTrash)
  }
}

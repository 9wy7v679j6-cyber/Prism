package com.prism.docs.core.data.storage

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.prism.docs.core.data.model.NameCollisionPolicy
import com.prism.docs.core.data.model.TransferOperationType
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/**
 * WorkManager worker executing large batch Copy/Move operations off the UI lifecycle
 * with live progress updates and cooperative cancellation.
 */
@HiltWorker
class FileTransferWorker
@AssistedInject
constructor(
  @Assisted appContext: Context,
  @Assisted params: WorkerParameters,
  private val fileOperationsManager: FileOperationsManager,
) : CoroutineWorker(appContext, params) {

  override suspend fun doWork(): Result {
    val ids = inputData.getLongArray(KEY_DOCUMENT_IDS)?.toList().orEmpty()
    val dest = inputData.getString(KEY_DESTINATION).orEmpty()
    val opType =
      runCatching {
          TransferOperationType.valueOf(
            inputData.getString(KEY_OPERATION_TYPE) ?: TransferOperationType.COPY.name
          )
        }
        .getOrDefault(TransferOperationType.COPY)
    val policy =
      runCatching {
          NameCollisionPolicy.valueOf(
            inputData.getString(KEY_COLLISION_POLICY) ?: NameCollisionPolicy.AUTO_RENAME.name
          )
        }
        .getOrDefault(NameCollisionPolicy.AUTO_RENAME)

    if (ids.isEmpty() || dest.isBlank()) {
      return Result.failure(workDataOf(KEY_RESULT_MESSAGE to "Invalid transfer parameters"))
    }

    return try {
      val result =
        fileOperationsManager.copyOrMoveDocuments(
          documentIds = ids,
          destinationDirPathOrUri = dest,
          operationType = opType,
          collisionPolicy = policy,
          onProgress = { completed, currentName, bytesDone, totalBytes ->
            setProgress(
              workDataOf(
                KEY_TOTAL_FILES to ids.size,
                KEY_COMPLETED_FILES to completed,
                KEY_CURRENT_FILE to currentName,
                KEY_BYTES_DONE to bytesDone,
                KEY_TOTAL_BYTES to totalBytes,
              )
            )
          },
        )
      val outData =
        workDataOf(
          KEY_RESULT_MESSAGE to result.message,
          KEY_AFFECTED_COUNT to result.affectedCount,
          KEY_SKIPPED_COUNT to result.skippedCount,
          KEY_FAILED_COUNT to result.failedCount,
        )
      if (result.success) Result.success(outData) else Result.failure(outData)
    } catch (ce: CancellationException) {
      throw ce
    } catch (t: Throwable) {
      Result.failure(workDataOf(KEY_RESULT_MESSAGE to (t.message ?: "Transfer failed")))
    }
  }

  companion object {
    const val UNIQUE_WORK_NAME = "prism_batch_file_transfer"
    const val KEY_DOCUMENT_IDS = "document_ids"
    const val KEY_DESTINATION = "destination"
    const val KEY_OPERATION_TYPE = "operation_type"
    const val KEY_COLLISION_POLICY = "collision_policy"
    const val KEY_TOTAL_FILES = "total_files"
    const val KEY_COMPLETED_FILES = "completed_files"
    const val KEY_CURRENT_FILE = "current_file"
    const val KEY_BYTES_DONE = "bytes_done"
    const val KEY_TOTAL_BYTES = "total_bytes"
    const val KEY_RESULT_MESSAGE = "result_message"
    const val KEY_AFFECTED_COUNT = "affected_count"
    const val KEY_SKIPPED_COUNT = "skipped_count"
    const val KEY_FAILED_COUNT = "failed_count"
  }
}

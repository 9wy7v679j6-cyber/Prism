package com.prism.docs.core.data.storage

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.prism.docs.core.data.model.FileOperationResult
import com.prism.docs.core.data.model.NameCollisionPolicy
import com.prism.docs.core.data.model.TransferOperationType
import com.prism.docs.core.data.model.TransferProgress
import com.prism.docs.core.data.paging.DocumentRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Coordinates interactive small-batch transfers and WorkManager-backed large-batch transfers
 * behind a single observable [transferProgress] stream with unified cancellation.
 */
@Singleton
class FileTransferScheduler
@Inject
constructor(
  private val workManager: WorkManager,
  private val documentRepository: DocumentRepository,
  private val fileOperationsManager: FileOperationsManager,
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  private val _transferProgress = MutableStateFlow<TransferProgress?>(null)
  val transferProgress: StateFlow<TransferProgress?> = _transferProgress.asStateFlow()

  private var activeInteractiveJob: Job? = null
  private var activeWorkObserveJob: Job? = null
  private var activeWorkBacked: Boolean = false

  fun startCopyOrMove(
    documentIds: List<Long>,
    destinationDirPathOrUri: String,
    operationType: TransferOperationType,
    collisionPolicy: NameCollisionPolicy = NameCollisionPolicy.AUTO_RENAME,
    onCompleted: (FileOperationResult) -> Unit = {},
  ) {
    if (documentIds.isEmpty()) return

    cancelActiveTransfer()
    val opId = UUID.randomUUID().toString()

    activeInteractiveJob =
      scope.launch {
        val docs = documentRepository.getDocumentsByIds(documentIds)
        if (docs.isEmpty()) {
          onCompleted(FileOperationResult(success = false, message = "No documents found"))
          return@launch
        }

        if (fileOperationsManager.shouldUseWorkManager(docs)) {
          activeWorkBacked = true
          enqueueWorkManagerBatch(
            opId = opId,
            ids = docs.map { it.id },
            totalBytes = docs.sumOf { it.sizeBytes.coerceAtLeast(0L) },
            destinationDirPathOrUri = destinationDirPathOrUri,
            operationType = operationType,
            collisionPolicy = collisionPolicy,
            onCompleted = onCompleted,
          )
        } else {
          activeWorkBacked = false
          val totalBytes = docs.sumOf { it.sizeBytes.coerceAtLeast(0L) }
          _transferProgress.value =
            TransferProgress(
              operationId = opId,
              type = operationType,
              totalFiles = docs.size,
              completedFiles = 0,
              totalBytes = totalBytes,
              isRunning = true,
              isWorkManagerBacked = false,
            )

          try {
            val result =
              fileOperationsManager.copyOrMoveDocuments(
                documentIds = documentIds,
                destinationDirPathOrUri = destinationDirPathOrUri,
                operationType = operationType,
                collisionPolicy = collisionPolicy,
                onProgress = { completed, currentName, bytesDone, total ->
                  _transferProgress.value =
                    TransferProgress(
                      operationId = opId,
                      type = operationType,
                      totalFiles = docs.size,
                      completedFiles = completed,
                      currentFileName = currentName,
                      bytesTransferred = bytesDone,
                      totalBytes = total,
                      isRunning = true,
                      isWorkManagerBacked = false,
                    )
                },
              )
            _transferProgress.value =
              TransferProgress(
                operationId = opId,
                type = operationType,
                totalFiles = docs.size,
                completedFiles = docs.size,
                bytesTransferred = totalBytes,
                totalBytes = totalBytes,
                isRunning = false,
                isWorkManagerBacked = false,
                summaryMessage = result.message,
              )
            onCompleted(result)
          } catch (_: CancellationException) {
            _transferProgress.value =
              _transferProgress.value?.copy(
                isRunning = false,
                isCancelled = true,
                summaryMessage = "Transfer cancelled — unfinished sources preserved",
              )
          }
        }
      }
  }

  fun cancelActiveTransfer() {
    activeInteractiveJob?.cancel()
    activeInteractiveJob = null
    if (activeWorkBacked) {
      workManager.cancelUniqueWork(FileTransferWorker.UNIQUE_WORK_NAME)
      activeWorkBacked = false
    }
    activeWorkObserveJob?.cancel()
    activeWorkObserveJob = null
    val current = _transferProgress.value
    if (current != null && current.isRunning) {
      _transferProgress.value =
        current.copy(
          isRunning = false,
          isCancelled = true,
          summaryMessage = "Transfer cancelled — unfinished sources preserved",
        )
    }
  }

  fun dismissCompletedProgress() {
    if (_transferProgress.value?.isRunning == false) {
      _transferProgress.value = null
    }
  }

  private fun enqueueWorkManagerBatch(
    opId: String,
    ids: List<Long>,
    totalBytes: Long,
    destinationDirPathOrUri: String,
    operationType: TransferOperationType,
    collisionPolicy: NameCollisionPolicy,
    onCompleted: (FileOperationResult) -> Unit,
  ) {
    val request =
      OneTimeWorkRequestBuilder<FileTransferWorker>()
        .setInputData(
          workDataOf(
            FileTransferWorker.KEY_DOCUMENT_IDS to ids.toLongArray(),
            FileTransferWorker.KEY_DESTINATION to destinationDirPathOrUri,
            FileTransferWorker.KEY_OPERATION_TYPE to operationType.name,
            FileTransferWorker.KEY_COLLISION_POLICY to collisionPolicy.name,
          )
        )
        .build()

    _transferProgress.value =
      TransferProgress(
        operationId = opId,
        type = operationType,
        totalFiles = ids.size,
        completedFiles = 0,
        totalBytes = totalBytes,
        isRunning = true,
        isWorkManagerBacked = true,
      )

    workManager.enqueueUniqueWork(
      FileTransferWorker.UNIQUE_WORK_NAME,
      ExistingWorkPolicy.REPLACE,
      request,
    )

    activeWorkObserveJob?.cancel()
    activeWorkObserveJob =
      scope.launch {
        workManager.getWorkInfoByIdFlow(request.id).collect { info ->
          if (info == null) return@collect
          when (info.state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED -> {
              val p = info.progress
              _transferProgress.value =
                TransferProgress(
                  operationId = opId,
                  type = operationType,
                  totalFiles = p.getInt(FileTransferWorker.KEY_TOTAL_FILES, ids.size),
                  completedFiles = p.getInt(FileTransferWorker.KEY_COMPLETED_FILES, 0),
                  currentFileName = p.getString(FileTransferWorker.KEY_CURRENT_FILE).orEmpty(),
                  bytesTransferred = p.getLong(FileTransferWorker.KEY_BYTES_DONE, 0L),
                  totalBytes = p.getLong(FileTransferWorker.KEY_TOTAL_BYTES, totalBytes),
                  isRunning = true,
                  isWorkManagerBacked = true,
                )
            }
            WorkInfo.State.SUCCEEDED -> {
              val msg =
                info.outputData.getString(FileTransferWorker.KEY_RESULT_MESSAGE)
                  ?: "Batch transfer complete"
              val affected = info.outputData.getInt(FileTransferWorker.KEY_AFFECTED_COUNT, ids.size)
              val skipped = info.outputData.getInt(FileTransferWorker.KEY_SKIPPED_COUNT, 0)
              _transferProgress.value =
                TransferProgress(
                  operationId = opId,
                  type = operationType,
                  totalFiles = ids.size,
                  completedFiles = ids.size,
                  bytesTransferred = totalBytes,
                  totalBytes = totalBytes,
                  isRunning = false,
                  isWorkManagerBacked = true,
                  summaryMessage = msg,
                )
              activeWorkBacked = false
              onCompleted(
                FileOperationResult(
                  success = true,
                  affectedCount = affected,
                  skippedCount = skipped,
                  message = msg,
                )
              )
              return@collect
            }
            WorkInfo.State.CANCELLED -> {
              _transferProgress.value =
                _transferProgress.value?.copy(
                  isRunning = false,
                  isCancelled = true,
                  summaryMessage = "Background transfer cancelled",
                )
              activeWorkBacked = false
              return@collect
            }
            WorkInfo.State.FAILED -> {
              val msg =
                info.outputData.getString(FileTransferWorker.KEY_RESULT_MESSAGE)
                  ?: "Background transfer failed"
              _transferProgress.value =
                _transferProgress.value?.copy(
                  isRunning = false,
                  summaryMessage = msg,
                )
              activeWorkBacked = false
              onCompleted(FileOperationResult(success = false, message = msg))
              return@collect
            }
          }
        }
      }
  }
}

package com.prism.docs.core.data.duplicates

import com.prism.docs.core.data.db.IndexedDocumentEntity

enum class DuplicateScanStage {
  IDLE,
  GROUPING_BY_SIZE,
  PARTIAL_HASHING,
  FULL_HASHING,
  COMPLETED,
  CANCELLED,
  FAILED,
}

data class DuplicateFileGroup(
  val groupKey: String,
  val sizeBytes: Long,
  val contentHash: String,
  val documents: List<IndexedDocumentEntity>,
) {
  val newestToKeep: IndexedDocumentEntity
    get() = documents.first()

  val duplicatesToTrash: List<IndexedDocumentEntity>
    get() = if (documents.size > 1) documents.drop(1) else emptyList()

  val reclaimableBytes: Long
    get() = duplicatesToTrash.sumOf { it.sizeBytes.coerceAtLeast(0L) }
}

data class DuplicateScanProgress(
  val stage: DuplicateScanStage = DuplicateScanStage.IDLE,
  val totalCandidates: Int = 0,
  val processedCount: Int = 0,
  val sizeGroupCount: Int = 0,
  val partialMatchCount: Int = 0,
  val confirmedGroupCount: Int = 0,
  val reclaimableBytes: Long = 0L,
  val errorMessage: String? = null,
) {
  val isRunning: Boolean
    get() =
      stage == DuplicateScanStage.GROUPING_BY_SIZE ||
        stage == DuplicateScanStage.PARTIAL_HASHING ||
        stage == DuplicateScanStage.FULL_HASHING

  val fraction: Float
    get() =
      if (totalCandidates <= 0) 0f
      else (processedCount.toFloat() / totalCandidates.toFloat()).coerceIn(0f, 1f)
}

data class DuplicateScanResult(
  val groups: List<DuplicateFileGroup>,
  val sizeCandidateCount: Int,
  val partialCandidateCount: Int,
  val fullHashedCount: Int,
) {
  val totalReclaimableBytes: Long
    get() = groups.sumOf { it.reclaimableBytes }
}

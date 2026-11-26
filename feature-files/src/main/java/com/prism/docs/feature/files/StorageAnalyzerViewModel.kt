package com.prism.docs.feature.files

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prism.docs.core.data.db.CategoryCountRow
import com.prism.docs.core.data.db.FolderStorageStatRow
import com.prism.docs.core.data.db.IndexedDocumentEntity
import com.prism.docs.core.data.model.DocumentCategory
import com.prism.docs.core.data.paging.DocumentRepository
import com.prism.docs.core.data.thumbnail.DocumentThumbnailPipeline
import com.prism.docs.core.ui.theme.PrismColors
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

enum class StorageRingBucket(
  @StringRes val labelRes: Int,
  val color: Color,
  val darkColor: Color,
  val filterCategory: DocumentCategory?,
  val isOtherBucket: Boolean = false,
) {
  PDF(R.string.category_pdf, PrismColors.CoralPdf, PrismColors.DarkDestructiveAccent, DocumentCategory.PDF),
  WORD(R.string.category_word, PrismColors.Sapphire, Color(0xFF5B8DEF), DocumentCategory.DOCUMENTS),
  EXCEL(R.string.category_excel, PrismColors.EmeraldSheet, PrismColors.DarkSuccessAccent, DocumentCategory.SPREADSHEETS),
  POWERPOINT(R.string.category_slides, PrismColors.OrangeSlide, Color(0xFFFF9E57), DocumentCategory.SLIDES),
  IMAGES(R.string.category_images, PrismColors.OrchidImage, Color(0xFFD57CF7), DocumentCategory.IMAGES),
  OTHER(R.string.analyzer_category_other, PrismColors.GlacierBlue, Color(0xFF64C2FF), null, isOtherBucket = true);

  fun colorFor(isDark: Boolean): Color = if (isDark) darkColor else color
}

@Immutable
data class StorageRingSegment(
  val bucket: StorageRingBucket,
  val itemCount: Int,
  val totalBytes: Long,
  val fraction: Float,
)

@Immutable
data class StorageAnalyzerUiState(
  val includeAllIndexedFiles: Boolean = false,
  val totalBytes: Long = 0L,
  val totalDocumentCount: Int = 0,
  val segments: List<StorageRingSegment> = emptyList(),
  val biggestFolders: List<FolderStorageStatRow> = emptyList(),
  val largestFiles: List<IndexedDocumentEntity> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StorageAnalyzerViewModel
@Inject
constructor(
  private val documentRepository: DocumentRepository,
  val thumbnailPipeline: DocumentThumbnailPipeline,
) : ViewModel() {

  private val includeAllIndexedFilesFlow = MutableStateFlow(false)

  val uiState: StateFlow<StorageAnalyzerUiState> =
    includeAllIndexedFilesFlow
      .flatMapLatest { includeAll ->
        val bytesFlow =
          if (includeAll) documentRepository.observeTotalIndexedBytes()
          else documentRepository.observeTotalPrimaryDocumentBytes()
        val countFlow =
          if (includeAll) documentRepository.observeActiveCount()
          else documentRepository.observeActivePrimaryDocumentCount()
        val categoriesFlow =
          if (includeAll) documentRepository.observeCategoryCounts()
          else documentRepository.observePrimaryDocumentCategoryCounts()
        val foldersFlow =
          if (includeAll) documentRepository.observeFolderStorageStats(limit = 15)
          else documentRepository.observePrimaryDocumentFolderStorageStats(limit = 15)
        val largestFlow =
          if (includeAll) documentRepository.observeLargestDocuments(limit = 30)
          else documentRepository.observeLargestPrimaryDocuments(limit = 30)

        combine(bytesFlow, countFlow, categoriesFlow, foldersFlow, largestFlow) {
          totalBytes,
          activeCount,
          categoryRows,
          folders,
          largestFiles ->
          val segments = buildRingSegments(categoryRows, totalBytes)
          StorageAnalyzerUiState(
            includeAllIndexedFiles = includeAll,
            totalBytes = totalBytes,
            totalDocumentCount = activeCount,
            segments = segments,
            biggestFolders = folders,
            largestFiles = largestFiles,
          )
        }
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = StorageAnalyzerUiState(),
      )

  fun setIncludeAllIndexedFiles(includeAll: Boolean) {
    includeAllIndexedFilesFlow.value = includeAll
  }

  companion object {
    fun buildRingSegments(
      rows: List<CategoryCountRow>,
      totalBytes: Long,
    ): List<StorageRingSegment> {
      val byBucketCount = HashMap<StorageRingBucket, Int>()
      val byBucketBytes = HashMap<StorageRingBucket, Long>()

      for (row in rows) {
        val cat = runCatching { DocumentCategory.valueOf(row.category) }.getOrNull()
        val bucket =
          when (cat) {
            DocumentCategory.PDF -> StorageRingBucket.PDF
            DocumentCategory.DOCUMENTS -> StorageRingBucket.WORD
            DocumentCategory.SPREADSHEETS -> StorageRingBucket.EXCEL
            DocumentCategory.SLIDES -> StorageRingBucket.POWERPOINT
            DocumentCategory.IMAGES -> StorageRingBucket.IMAGES
            else -> StorageRingBucket.OTHER
          }
        byBucketCount[bucket] = (byBucketCount[bucket] ?: 0) + row.itemCount
        byBucketBytes[bucket] = (byBucketBytes[bucket] ?: 0L) + row.totalBytes.coerceAtLeast(0L)
      }

      val safeTotal = totalBytes.coerceAtLeast(1L).toFloat()
      return StorageRingBucket.entries.mapNotNull { bucket ->
        val bytes = byBucketBytes[bucket] ?: 0L
        val count = byBucketCount[bucket] ?: 0
        if (bytes <= 0L && count <= 0) {
          null
        } else {
          StorageRingSegment(
            bucket = bucket,
            itemCount = count,
            totalBytes = bytes,
            fraction = (bytes.toFloat() / safeTotal).coerceIn(0f, 1f),
          )
        }
      }
    }
  }
}

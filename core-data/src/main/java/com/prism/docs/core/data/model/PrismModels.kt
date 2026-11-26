package com.prism.docs.core.data.model

import kotlinx.serialization.Serializable

enum class ThemeMode {
  SYSTEM,
  LIGHT,
  DARK,
}

enum class GlassIntensity(val blurScale: Float, val tintScale: Float) {
  SUBTLE(blurScale = 0.72f, tintScale = 0.85f),
  STANDARD(blurScale = 1.0f, tintScale = 1.0f),
  RICH(blurScale = 1.25f, tintScale = 1.15f),
}

enum class StorageAccessLevel {
  /** API 30+ MANAGE_EXTERNAL_STORAGE or API 26-29 READ/WRITE_EXTERNAL_STORAGE granted. */
  FULL_ALL_FILES,

  /** Degraded privacy-preserving mode using Storage Access Framework persisted folder URIs. */
  SAF_DEGRADED,

  /** No broad or SAF folder access granted yet (MediaStore & explicit Open With still work). */
  LIMITED_NONE,
}

enum class StorageSourceType {
  FILESYSTEM,
  MEDIASTORE,
  SAF,
}

enum class DocumentCategory(val displayName: String, val shortLabel: String) {
  PDF("PDFs", "PDF"),
  DOCUMENTS("Documents", "Docs"),
  SPREADSHEETS("Spreadsheets", "Sheets"),
  SLIDES("Slides", "Slides"),
  IMAGES("Images", "Images"),
  VIDEOS("Videos", "Video"),
  AUDIO("Audio", "Audio"),
  ARCHIVES("Archives", "ZIP"),
  EBOOKS("E-books", "Books"),
  CODE_TEXT("Code & Text", "Code"),
  OTHER("Other", "Other"),
}

enum class FileSortField {
  NAME,
  DATE_MODIFIED,
  SIZE,
  TYPE,
}

enum class SortDirection {
  ASC,
  DESC,
}

enum class DirectoryChangeKind {
  CREATED,
  DELETED,
  MODIFIED,
  MOVED,
  GENERIC_REFRESH,
}

@Serializable
data class SafFolderGrant(
  val uriString: String,
  val displayName: String,
  val grantedAtEpochMs: Long,
)

data class PrismPreferences(
  val onboardingCompleted: Boolean = false,
  val themeMode: ThemeMode = ThemeMode.LIGHT,
  val useDynamicColor: Boolean = false,
  val glassIntensity: GlassIntensity = GlassIntensity.STANDARD,
  val glassClarity: Float = 0.35f,
  val reduceTransparency: Boolean = false,
  val reduceMotion: Boolean = false,
  val safFolderGrants: List<SafFolderGrant> = emptyList(),
  val fullAccessBannerDismissed: Boolean = false,
)

@Serializable
data class IncomingDocumentRequest(
  val uriString: String,
  val displayName: String,
  val mimeType: String,
  val sizeBytes: Long = -1L,
  val sourceAction: String = "VIEW",
)

data class StorageAccessState(
  val level: StorageAccessLevel,
  val sdkInt: Int,
  val hasAllFilesPermission: Boolean,
  val safFolders: List<SafFolderGrant>,
) {
  val canBrowseFullDevice: Boolean
    get() = level == StorageAccessLevel.FULL_ALL_FILES

  val isDegradedMode: Boolean
    get() = level != StorageAccessLevel.FULL_ALL_FILES
}

data class StorageVolumeRoot(
  val id: String,
  val displayName: String,
  val pathOrUri: String,
  val sourceType: StorageSourceType,
  val isRemovable: Boolean = false,
  val availableBytes: Long = -1L,
  val totalBytes: Long = -1L,
)

data class ScannedDocumentMetadata(
  val uriString: String,
  val displayName: String,
  val parentPath: String,
  val mimeType: String,
  val extension: String,
  val category: DocumentCategory,
  val sizeBytes: Long,
  val lastModifiedEpochMs: Long,
  val isDirectory: Boolean,
  val sourceType: StorageSourceType,
)

data class DirectoryChangeEvent(
  val directoryPathOrUri: String,
  val changedChildName: String?,
  val kind: DirectoryChangeKind,
  val observedAtEpochMs: Long = System.currentTimeMillis(),
)

data class FileListQuery(
  val category: DocumentCategory? = null,
  val otherCategoriesOnly: Boolean = false,
  val documentsOnly: Boolean = true,
  val parentPath: String? = null,
  val collectionId: Long? = null,
  val tagId: Long? = null,
  val favoritesOnly: Boolean = false,
  val recentsOnly: Boolean = false,
  val namePrefix: String? = null,
  val minSizeBytes: Long? = null,
  val maxSizeBytes: Long? = null,
  val modifiedAfterEpochMs: Long? = null,
  val sortField: FileSortField = FileSortField.DATE_MODIFIED,
  val sortDirection: SortDirection = SortDirection.DESC,
)

data class IndexSyncSummary(
  val scannedCount: Int,
  val insertedCount: Int,
  val updatedCount: Int,
  val removedCount: Int,
  val expiredTrashPurged: Int,
  val completedAtEpochMs: Long,
)

data class IndexWorkStatus(
  val isRunning: Boolean = false,
  val scannedCount: Int = 0,
  val lastCompletedEpochMs: Long = 0L,
  val totalIndexedCount: Int = 0,
  val phaseLabel: String = "Idle",
)

enum class NameCollisionPolicy {
  AUTO_RENAME,
  SKIP,
  OVERWRITE,
}

enum class TransferOperationType {
  COPY,
  MOVE,
}

data class FileOperationResult(
  val success: Boolean,
  val affectedCount: Int = 0,
  val skippedCount: Int = 0,
  val failedCount: Int = 0,
  val message: String,
  val createdUriString: String? = null,
)

data class TransferProgress(
  val operationId: String,
  val type: TransferOperationType,
  val totalFiles: Int,
  val completedFiles: Int,
  val currentFileName: String = "",
  val bytesTransferred: Long = 0L,
  val totalBytes: Long = 0L,
  val isRunning: Boolean = true,
  val isCancelled: Boolean = false,
  val isWorkManagerBacked: Boolean = false,
  val summaryMessage: String? = null,
) {
  val fraction: Float
    get() =
      when {
        totalBytes > 0L -> (bytesTransferred.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        totalFiles > 0 -> (completedFiles.toFloat() / totalFiles.toFloat()).coerceIn(0f, 1f)
        else -> 0f
      }
}

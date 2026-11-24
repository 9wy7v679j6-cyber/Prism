package com.prism.docs.core.data.storage

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.prism.docs.core.data.model.DirectoryChangeEvent
import com.prism.docs.core.data.model.DirectoryChangeKind
import com.prism.docs.core.data.model.SafFolderGrant
import com.prism.docs.core.data.model.ScannedDocumentMetadata
import com.prism.docs.core.data.model.StorageAccessLevel
import com.prism.docs.core.data.model.StorageAccessState
import com.prism.docs.core.data.model.StorageSourceType
import com.prism.docs.core.data.model.StorageVolumeRoot
import com.prism.docs.core.data.preferences.PrismPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Unified storage abstraction shielding the indexer and UI from whether the device has granted
 * All Files Access (API 30+ / legacy API 26-29) or operates in degraded SAF + MediaStore mode.
 */
interface DocumentStorageGateway {
  suspend fun currentAccessState(): StorageAccessState

  suspend fun listStorageRoots(): List<StorageVolumeRoot>

  suspend fun listDirectoryChildren(directoryPathOrUri: String): List<ScannedDocumentMetadata>

  /**
   * Streams accessible non-directory documents in batches for memory-safe incremental indexing.
   * Uses MediaStore + direct filesystem traversal when All Files Access is granted, or
   * DocumentsContract tree queries + MediaStore when operating in SAF degraded mode.
   */
  suspend fun scanAccessibleDocuments(
    batchSize: Int = 250,
    onBatch: suspend (List<ScannedDocumentMetadata>) -> Unit,
  ): Int

  /**
   * Observes changes ONLY inside the single currently-open directory.
   * Uses Android [FileObserver] for direct filesystem directories (non-recursive by design)
   * and [ContentObserver] for SAF document tree URIs.
   */
  fun observeOpenDirectory(directoryPathOrUri: String): Flow<DirectoryChangeEvent>
}

@Singleton
class UnifiedDocumentStorageGateway
@Inject
constructor(
  @ApplicationContext private val context: Context,
  private val permissionManager: StoragePermissionManager,
  private val preferencesRepository: PrismPreferencesRepository,
) : DocumentStorageGateway {

  override suspend fun currentAccessState(): StorageAccessState {
    val prefs = preferencesRepository.preferencesFlow.first()
    return permissionManager.computeAccessState(prefs.safFolderGrants)
  }

  override suspend fun listStorageRoots(): List<StorageVolumeRoot> =
    withContext(Dispatchers.IO) {
      val state = currentAccessState()
      val roots = mutableListOf<StorageVolumeRoot>()

      if (state.level == StorageAccessLevel.FULL_ALL_FILES) {
        val primaryDir = Environment.getExternalStorageDirectory()
        if (primaryDir != null && primaryDir.exists()) {
          val (avail, total) = readStatFs(primaryDir)
          roots +=
            StorageVolumeRoot(
              id = "primary_internal",
              displayName = "Internal Storage",
              pathOrUri = primaryDir.absolutePath,
              sourceType = StorageSourceType.FILESYSTEM,
              isRemovable = false,
              availableBytes = avail,
              totalBytes = total,
            )
        }

        // Discover removable SD cards / USB volumes via StorageManager on API 30+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
          val storageManager = context.getSystemService(StorageManager::class.java)
          storageManager?.storageVolumes?.forEach { volume ->
            val dir = volume.directory
            if (
              dir != null &&
                dir.exists() &&
                dir.absolutePath != primaryDir?.absolutePath &&
                volume.state == Environment.MEDIA_MOUNTED
            ) {
              val (avail, total) = readStatFs(dir)
              roots +=
                StorageVolumeRoot(
                  id = "vol_${volume.uuid ?: dir.name}",
                  displayName = volume.getDescription(context) ?: dir.name,
                  pathOrUri = dir.absolutePath,
                  sourceType = StorageSourceType.FILESYSTEM,
                  isRemovable = volume.isRemovable,
                  availableBytes = avail,
                  totalBytes = total,
                )
            }
          }
        }
      }

      // Always expose persisted SAF folder grants (primary roots in degraded mode, or USB-OTG in full mode)
      state.safFolders.forEach { grant ->
        roots +=
          StorageVolumeRoot(
            id = "saf_${grant.uriString.hashCode()}",
            displayName = grant.displayName,
            pathOrUri = grant.uriString,
            sourceType = StorageSourceType.SAF,
            isRemovable = false,
          )
      }

      roots
    }

  override suspend fun listDirectoryChildren(
    directoryPathOrUri: String,
  ): List<ScannedDocumentMetadata> =
    withContext(Dispatchers.IO) {
      if (directoryPathOrUri.startsWith("content://")) {
        listSafChildren(Uri.parse(directoryPathOrUri))
      } else {
        listFilesystemChildren(File(directoryPathOrUri))
      }
    }

  override suspend fun scanAccessibleDocuments(
    batchSize: Int,
    onBatch: suspend (List<ScannedDocumentMetadata>) -> Unit,
  ): Int =
    withContext(Dispatchers.IO) {
      val state = currentAccessState()
      val seenUris = HashSet<String>(1024)
      val buffer = ArrayList<ScannedDocumentMetadata>(batchSize)
      var totalScanned = 0

      suspend fun emitItem(item: ScannedDocumentMetadata) {
        if (item.isDirectory || isDirectoryMimeType(item.mimeType)) return
        if (isHiddenOrSystemPath(item.parentPath) || item.displayName.startsWith(".")) return
        if (!seenUris.add(item.uriString)) return
        buffer.add(item)
        totalScanned++
        if (buffer.size >= batchSize) {
          onBatch(buffer.toList())
          buffer.clear()
        }
      }

      // 1. Fast MediaStore.Files sweep (works broadly and captures system-indexed media/documents)
      scanMediaStoreFiles { emitItem(it) }

      // 2. When All Files Access is granted, walk storage roots to catch non-MediaStore documents
      //    (e.g., .docx, .xlsx, .pptx, .epub, .7z, code files in arbitrary folders)
      if (state.level == StorageAccessLevel.FULL_ALL_FILES) {
        val roots = listStorageRoots().filter { it.sourceType == StorageSourceType.FILESYSTEM }
        for (root in roots) {
          walkFilesystemDirectory(File(root.pathOrUri)) { emitItem(it) }
        }
      }

      // 3. Walk persisted SAF trees (primary source in SAF degraded mode, plus USB-OTG roots)
      for (grant in state.safFolders) {
        walkSafTree(grant) { emitItem(it) }
      }

      if (buffer.isNotEmpty()) {
        onBatch(buffer.toList())
        buffer.clear()
      }

      totalScanned
    }

  override fun observeOpenDirectory(directoryPathOrUri: String): Flow<DirectoryChangeEvent> =
    callbackFlow {
      if (directoryPathOrUri.startsWith("content://")) {
        val uri = Uri.parse(directoryPathOrUri)
        val observer =
          object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, changedUri: Uri?) {
              trySend(
                DirectoryChangeEvent(
                  directoryPathOrUri = directoryPathOrUri,
                  changedChildName = changedUri?.lastPathSegment,
                  kind = DirectoryChangeKind.GENERIC_REFRESH,
                )
              )
            }
          }
        val registered =
          runCatching {
              context.contentResolver.registerContentObserver(uri, true, observer)
              true
            }
            .getOrDefault(false)
        awaitClose {
          if (registered) {
            runCatching { context.contentResolver.unregisterContentObserver(observer) }
          }
        }
      } else {
        val dir = File(directoryPathOrUri)
        if (!dir.exists() || !dir.isDirectory) {
          awaitClose {}
          return@callbackFlow
        }
        val mask =
          FileObserver.CREATE or
            FileObserver.DELETE or
            FileObserver.CLOSE_WRITE or
            FileObserver.MOVED_FROM or
            FileObserver.MOVED_TO

        val dispatchEvent: (Int, String?) -> Unit = { event, childPath ->
          val kind =
            when {
              (event and FileObserver.CREATE) != 0 -> DirectoryChangeKind.CREATED
              (event and FileObserver.DELETE) != 0 -> DirectoryChangeKind.DELETED
              (event and FileObserver.MOVED_FROM) != 0 || (event and FileObserver.MOVED_TO) != 0 ->
                DirectoryChangeKind.MOVED
              else -> DirectoryChangeKind.MODIFIED
            }
          trySend(
            DirectoryChangeEvent(
              directoryPathOrUri = directoryPathOrUri,
              changedChildName = childPath,
              kind = kind,
            )
          )
        }

        val observer: FileObserver =
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            object : FileObserver(dir, mask) {
              override fun onEvent(event: Int, path: String?) = dispatchEvent(event, path)
            }
          } else {
            @Suppress("DEPRECATION")
            object : FileObserver(dir.absolutePath, mask) {
              override fun onEvent(event: Int, path: String?) = dispatchEvent(event, path)
            }
          }

        runCatching { observer.startWatching() }
        awaitClose { runCatching { observer.stopWatching() } }
      }
    }

  private fun listFilesystemChildren(directory: File): List<ScannedDocumentMetadata> {
    if (!directory.exists() || !directory.isDirectory) return emptyList()
    val children = directory.listFiles() ?: return emptyList()
    return children
      .filter { !it.name.startsWith(".") }
      .map { file -> fileToMetadata(file) }
  }

  internal suspend fun walkFilesystemDirectory(
    root: File,
    onFile: suspend (ScannedDocumentMetadata) -> Unit,
  ) {
    if (!root.exists() || !root.isDirectory) return
    val stack = ArrayDeque<File>()
    stack.add(root)
    while (stack.isNotEmpty()) {
      val current = stack.removeLast()
      val children = runCatching { current.listFiles() }.getOrNull() ?: continue
      for (child in children) {
        val name = child.name
        if (name.startsWith(".")) continue
        if (child.isDirectory) {
          val abs = child.absolutePath
          if (isHiddenOrSystemPath(abs)) continue
          stack.add(child)
        } else if (child.isFile && !child.isDirectory) {
          if (isHiddenOrSystemPath(child.absolutePath)) continue
          onFile(fileToMetadata(child))
        }
      }
    }
  }

  private fun fileToMetadata(file: File): ScannedDocumentMetadata {
    val name = file.name
    val isDir = file.isDirectory
    val mime = if (isDir) DocumentsContract.Document.MIME_TYPE_DIR else DocumentCategoryClassifier.inferMimeType(name, null)
    val ext = if (isDir) "" else DocumentCategoryClassifier.extractExtension(name)
    return ScannedDocumentMetadata(
      uriString = Uri.fromFile(file).toString(),
      displayName = name,
      parentPath = file.parent ?: "",
      mimeType = mime,
      extension = ext,
      category = DocumentCategoryClassifier.classify(mime, name),
      sizeBytes = if (isDir) 0L else file.length(),
      lastModifiedEpochMs = file.lastModified(),
      isDirectory = isDir,
      sourceType = StorageSourceType.FILESYSTEM,
    )
  }

  private suspend fun scanMediaStoreFiles(onFile: suspend (ScannedDocumentMetadata) -> Unit) {
    val collection =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
      } else {
        MediaStore.Files.getContentUri("external")
      }

    val projection =
      mutableListOf(
          MediaStore.Files.FileColumns._ID,
          MediaStore.Files.FileColumns.DISPLAY_NAME,
          MediaStore.Files.FileColumns.MIME_TYPE,
          MediaStore.Files.FileColumns.SIZE,
          MediaStore.Files.FileColumns.DATE_MODIFIED,
          MediaStore.Files.FileColumns.MEDIA_TYPE,
          @Suppress("DEPRECATION") MediaStore.Files.FileColumns.DATA,
        )
        .apply {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(MediaStore.Files.FileColumns.RELATIVE_PATH)
            add(MediaStore.Files.FileColumns.IS_PENDING)
          }
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            add(MediaStore.Files.FileColumns.IS_TRASHED)
          }
        }
        .toTypedArray()

    runCatching {
      context.contentResolver
        .query(
          collection,
          projection,
          "${MediaStore.Files.FileColumns.SIZE} > 0",
          null,
          "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC",
        )
        ?.use { cursor ->
          val idCol = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID)
          val nameCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
          val mimeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)
          val sizeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
          val modCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED)
          val mediaTypeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.MEDIA_TYPE)
          @Suppress("DEPRECATION")
          val dataCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)
          val relPathCol =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
              cursor.getColumnIndex(MediaStore.Files.FileColumns.RELATIVE_PATH)
            } else {
              -1
            }
          val pendingCol =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
              cursor.getColumnIndex(MediaStore.Files.FileColumns.IS_PENDING)
            } else {
              -1
            }
          val trashedCol =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
              cursor.getColumnIndex(MediaStore.Files.FileColumns.IS_TRASHED)
            } else {
              -1
            }

          val primaryExternalDir = runCatching { Environment.getExternalStorageDirectory() }.getOrNull()

          while (cursor.moveToNext()) {
            if (idCol < 0 || nameCol < 0) continue
            val id = cursor.getLong(idCol)
            val name = cursor.getString(nameCol)?.takeIf { it.isNotBlank() } ?: continue
            val rawMime = if (mimeCol >= 0) cursor.getString(mimeCol) else null
            val mediaType =
              if (mediaTypeCol >= 0 && !cursor.isNull(mediaTypeCol)) cursor.getInt(mediaTypeCol)
              else MediaStore.Files.FileColumns.MEDIA_TYPE_NONE
            val dataPath = if (dataCol >= 0) cursor.getString(dataCol).orEmpty() else ""
            val relPath = if (relPathCol >= 0) cursor.getString(relPathCol).orEmpty() else ""
            val isPending = pendingCol >= 0 && !cursor.isNull(pendingCol) && cursor.getInt(pendingCol) != 0
            val isTrashed = trashedCol >= 0 && !cursor.isNull(trashedCol) && cursor.getInt(trashedCol) != 0

            if (
              shouldSkipMediaStoreRow(
                displayName = name,
                rawMimeType = rawMime,
                mediaType = mediaType,
                dataPath = dataPath,
                relativePath = relPath,
                isPending = isPending,
                isTrashed = isTrashed,
                primaryExternalRoot = primaryExternalDir,
              )
            ) {
              continue
            }

            val mime = DocumentCategoryClassifier.inferMimeType(name, rawMime)
            val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
            val modSec = if (modCol >= 0) cursor.getLong(modCol) else 0L
            val parent =
              when {
                dataPath.isNotBlank() -> File(dataPath).parent.orEmpty()
                relPath.isNotBlank() -> relPath.trimEnd('/')
                else -> ""
              }

            val contentUri = ContentUris.withAppendedId(collection, id)
            onFile(
              ScannedDocumentMetadata(
                uriString = contentUri.toString(),
                displayName = name,
                parentPath = parent,
                mimeType = mime,
                extension = DocumentCategoryClassifier.extractExtension(name),
                category = DocumentCategoryClassifier.classify(mime, name),
                sizeBytes = size,
                lastModifiedEpochMs = modSec * 1000L,
                isDirectory = false,
                sourceType = StorageSourceType.MEDIASTORE,
              )
            )
          }
        }
    }
  }

  private fun listSafChildren(treeOrDocUri: Uri): List<ScannedDocumentMetadata> {
    val treeUri =
      if (DocumentsContract.isTreeUri(treeOrDocUri)) treeOrDocUri else return emptyList()
    val docId =
      runCatching { DocumentsContract.getDocumentId(treeOrDocUri) }
        .getOrElse { DocumentsContract.getTreeDocumentId(treeUri) }
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
    val projection =
      arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
      )
    val items = mutableListOf<ScannedDocumentMetadata>()
    runCatching {
      context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
        val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
        val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
        val sizeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
        val modCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

        while (cursor.moveToNext()) {
          if (idCol < 0 || nameCol < 0) continue
          val childId = cursor.getString(idCol) ?: continue
          val name = cursor.getString(nameCol) ?: continue
          if (name.startsWith(".")) continue
          val rawMime = if (mimeCol >= 0) cursor.getString(mimeCol) else null
          val isDir = isDirectoryMimeType(rawMime)
          val mime =
            if (isDir) DocumentsContract.Document.MIME_TYPE_DIR
            else DocumentCategoryClassifier.inferMimeType(name, rawMime)
          val size = if (sizeCol >= 0 && !cursor.isNull(sizeCol)) cursor.getLong(sizeCol) else 0L
          val mod = if (modCol >= 0 && !cursor.isNull(modCol)) cursor.getLong(modCol) else 0L
          val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)

          items +=
            ScannedDocumentMetadata(
              uriString = childUri.toString(),
              displayName = name,
              parentPath = treeOrDocUri.toString(),
              mimeType = mime,
              extension = if (isDir) "" else DocumentCategoryClassifier.extractExtension(name),
              category = DocumentCategoryClassifier.classify(mime, name),
              sizeBytes = if (isDir) 0L else size,
              lastModifiedEpochMs = mod,
              isDirectory = isDir,
              sourceType = StorageSourceType.SAF,
            )
        }
      }
    }
    return items
  }

  private suspend fun walkSafTree(
    grant: SafFolderGrant,
    onFile: suspend (ScannedDocumentMetadata) -> Unit,
  ) {
    val treeUri = runCatching { Uri.parse(grant.uriString) }.getOrNull() ?: return
    val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return
    if (!rootDoc.canRead()) return

    val queue = ArrayDeque<Uri>()
    queue.add(treeUri)
    while (queue.isNotEmpty()) {
      val currentUri = queue.removeFirst()
      val children = listSafChildren(currentUri)
      for (child in children) {
        if (child.isDirectory || isDirectoryMimeType(child.mimeType)) {
          queue.add(Uri.parse(child.uriString))
        } else {
          // parentPath is already canonical currentUri.toString() from listSafChildren
          onFile(child)
        }
      }
    }
  }

  companion object {
    private val DIRECTORY_MIME_TYPES =
      setOf(
        DocumentsContract.Document.MIME_TYPE_DIR.lowercase(),
        "resource/folder",
        "inode/directory",
      )

    fun isDirectoryMimeType(mimeType: String?): Boolean {
      if (mimeType.isNullOrBlank()) return false
      return mimeType.trim().lowercase() in DIRECTORY_MIME_TYPES
    }

    fun isHiddenOrSystemPath(path: String?): Boolean {
      if (path.isNullOrBlank()) return false
      val normalized = path.replace('\\', '/')
      if (normalized.contains("/Android/data") || normalized.contains("/Android/obb")) return true
      if (normalized.contains("/.PrismTrash")) return true
      val segments = normalized.split('/')
      return segments.any { it.startsWith(".") && it.length > 1 }
    }

    /**
     * Filters out MediaStore rows that represent directories (e.g., "Android", "media", "DCIM"),
     * pending/trashed system records, hidden dot-entries, or restricted app-private directories.
     */
    internal fun shouldSkipMediaStoreRow(
      displayName: String,
      rawMimeType: String?,
      mediaType: Int,
      dataPath: String,
      relativePath: String,
      isPending: Boolean,
      isTrashed: Boolean,
      primaryExternalRoot: File?,
    ): Boolean {
      if (isPending || isTrashed) return true
      if (displayName.startsWith(".")) return true
      if (isDirectoryMimeType(rawMimeType)) return true
      if (isHiddenOrSystemPath(dataPath) || isHiddenOrSystemPath("/$relativePath")) return true

      if (dataPath.isNotBlank()) {
        val candidate = File(dataPath)
        if (candidate.isDirectory) return true
        if (candidate.exists() && !candidate.isFile) return true
      }

      if (dataPath.isBlank() && primaryExternalRoot != null) {
        val parentDir =
          if (relativePath.isNotBlank()) File(primaryExternalRoot, relativePath)
          else primaryExternalRoot
        val candidate = File(parentDir, displayName)
        if (candidate.isDirectory) return true
        if (candidate.exists() && !candidate.isFile) return true
      }

      // In Android MediaStore, directory entries carry MEDIA_TYPE_NONE (0), null MIME type,
      // and no file extension (e.g. "Android", "media"). If we could not confirm a regular file
      // on disk, skip such entries rather than surfacing folders as documents.
      val confirmedRegularFile = dataPath.isNotBlank() && File(dataPath).isFile
      if (
        !confirmedRegularFile &&
          rawMimeType.isNullOrBlank() &&
          mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_NONE &&
          !displayName.contains('.')
      ) {
        return true
      }

      return false
    }
  }

  private fun readStatFs(dir: File): Pair<Long, Long> {
    return runCatching {
        val stat = StatFs(dir.absolutePath)
        stat.availableBytes to stat.totalBytes
      }
      .getOrDefault(-1L to -1L)
  }
}

package com.prism.docs.core.data.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import com.prism.docs.core.data.db.DocumentIndexDao
import com.prism.docs.core.data.db.IndexedDocumentEntity
import com.prism.docs.core.data.db.PrismDatabase
import com.prism.docs.core.data.db.RecycleBinDao
import com.prism.docs.core.data.db.RecycleBinEntryEntity
import com.prism.docs.core.data.model.FileOperationResult
import com.prism.docs.core.data.model.NameCollisionPolicy
import com.prism.docs.core.data.model.StorageSourceType
import com.prism.docs.core.data.model.TransferOperationType
import com.prism.docs.core.data.paging.DocumentRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

internal typealias CopyIntegrityVerifier =
  suspend (destFile: File?, destUri: Uri?, expectedBytes: Long, expectedSha256: String) -> Boolean

/**
 * Core on-device file operations & 30-day same-volume Recycle Bin engine.
 *
 * Guarantees:
 * - Rename, same-volume Move, and Recycle Bin delete/restore update the existing Room row
 *   in-place (preserving `documentId`) so favorites, tags, and collections survive intact.
 * - Cross-volume moves & all copies stream to a temporary staging file (or SAF doc), compute
 *   source byte length + SHA-256 on the fly, re-read the destination to verify exact length &
 *   SHA-256 match, and NEVER delete the source until verification succeeds.
 * - Local Recycle Bin uses a hidden `.PrismTrash` folder (with `.nomedia`) on the SAME storage
 *   volume as the file so delete and restore are instant O(1) renames.
 * - SAF-tree Recycle Bin stages a verified copy into app-private trash (`filesDir/prism_saf_trash`)
 *   before deleting the SAF document (since Android SAF does not universally support atomic
 *   same-volume renames into hidden dot-folders across providers).
 */
@Singleton
class FileOperationsManager
internal constructor(
  private val context: Context,
  private val database: PrismDatabase,
  private val documentIndexDao: DocumentIndexDao,
  private val recycleBinDao: RecycleBinDao,
  private val storageGateway: DocumentStorageGateway,
  private val volumeRootResolver: suspend (File) -> File,
  private val customVerifier: CopyIntegrityVerifier?,
) {

  @Inject
  constructor(
    @ApplicationContext context: Context,
    database: PrismDatabase,
    documentIndexDao: DocumentIndexDao,
    recycleBinDao: RecycleBinDao,
    storageGateway: DocumentStorageGateway,
  ) : this(
    context = context,
    database = database,
    documentIndexDao = documentIndexDao,
    recycleBinDao = recycleBinDao,
    storageGateway = storageGateway,
    volumeRootResolver = { file -> resolveVolumeRootFromGateway(storageGateway, file) },
    customVerifier = null,
  )

  companion object {
    const val HIDDEN_TRASH_DIR_NAME = ".PrismTrash"
    const val SAF_TRASH_VAULT_DIR_NAME = "prism_saf_trash"
    const val WORKMANAGER_BATCH_FILE_THRESHOLD = 5
    const val WORKMANAGER_BATCH_BYTES_THRESHOLD = 25L * 1024L * 1024L
    private const val COPY_BUFFER_SIZE = 64 * 1024
    private const val SQLITE_IN_CHUNK_SIZE = 500
    private const val PROGRESS_MIN_INTERVAL_NS = 120_000_000L
    private const val PROGRESS_MIN_BYTES_DELTA = 512L * 1024L
    private val INVALID_NAME_CHARS = charArrayOf('/', '\\', '\u0000')
    private val HEX_CHARS = charArrayOf(
      '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f',
    )

    internal suspend fun resolveVolumeRootFromGateway(
      gateway: DocumentStorageGateway,
      file: File,
    ): File {
      val canonicalPath = runCatching { file.canonicalPath }.getOrElse { file.absolutePath }
      val roots =
        runCatching { gateway.listStorageRoots() }
          .getOrDefault(emptyList())
          .filter { it.sourceType == StorageSourceType.FILESYSTEM }
          .sortedByDescending { it.pathOrUri.length }

      val matched =
        roots.firstOrNull { root ->
          val rootCanonical =
            runCatching { File(root.pathOrUri).canonicalPath }.getOrElse { root.pathOrUri }
          canonicalPath == rootCanonical || canonicalPath.startsWith("$rootCanonical/")
        }
      if (matched != null) {
        return File(matched.pathOrUri)
      }
      // Fallback: walk up to nearest writable ancestor (preserves same mount in unit tests)
      return file.parentFile ?: file
    }
  }

  fun validateFileName(rawName: String): String? {
    val trimmed = rawName.trim()
    if (trimmed.isEmpty() || trimmed == "." || trimmed == "..") return null
    if (trimmed.any { it in INVALID_NAME_CHARS }) return null
    return trimmed
  }

  fun shouldUseWorkManager(documents: List<IndexedDocumentEntity>): Boolean {
    if (documents.size > WORKMANAGER_BATCH_FILE_THRESHOLD) return true
    val totalBytes = documents.sumOf { it.sizeBytes.coerceAtLeast(0L) }
    return totalBytes > WORKMANAGER_BATCH_BYTES_THRESHOLD
  }

  suspend fun createFolder(
    parentPathOrUri: String,
    folderName: String,
  ): FileOperationResult =
    withContext(Dispatchers.IO) {
      val safeName =
        validateFileName(folderName)
          ?: return@withContext FileOperationResult(
            success = false,
            message = "Invalid folder name",
          )

      if (parentPathOrUri.startsWith("content://")) {
        val parentDoc =
          DocumentFile.fromTreeUri(context, Uri.parse(parentPathOrUri))
            ?: return@withContext FileOperationResult(
              success = false,
              message = "SAF folder is not accessible",
            )
        if (parentDoc.findFile(safeName) != null) {
          return@withContext FileOperationResult(
            success = false,
            message = "A folder named \"$safeName\" already exists",
          )
        }
        val created = parentDoc.createDirectory(safeName)
        if (created != null) {
          FileOperationResult(
            success = true,
            affectedCount = 1,
            message = "Created folder \"$safeName\"",
            createdUriString = created.uri.toString(),
          )
        } else {
          FileOperationResult(
            success = false,
            message = "Could not create folder in this SAF directory",
          )
        }
      } else {
        val parentDir = File(parentPathOrUri)
        if (!parentDir.exists() || !parentDir.isDirectory) {
          return@withContext FileOperationResult(
            success = false,
            message = "Parent directory does not exist",
          )
        }
        val target = File(parentDir, safeName)
        if (target.exists()) {
          return@withContext FileOperationResult(
            success = false,
            message = "\"$safeName\" already exists",
          )
        }
        if (target.mkdir()) {
          FileOperationResult(
            success = true,
            affectedCount = 1,
            message = "Created folder \"$safeName\"",
            createdUriString = Uri.fromFile(target).toString(),
          )
        } else {
          FileOperationResult(
            success = false,
            message = "Failed to create folder \"$safeName\"",
          )
        }
      }
    }

  suspend fun renameDocument(
    documentId: Long,
    newName: String,
  ): FileOperationResult =
    withContext(Dispatchers.IO) {
      val safeName =
        validateFileName(newName)
          ?: return@withContext FileOperationResult(
            success = false,
            message = "Invalid file name",
          )

      val doc =
        documentIndexDao.getById(documentId)
          ?: return@withContext FileOperationResult(
            success = false,
            message = "Document not found in index",
          )

      if (doc.displayName == safeName) {
        return@withContext FileOperationResult(
          success = true,
          affectedCount = 0,
          message = "Name unchanged",
        )
      }

      val localFile = resolveLocalFile(doc.uriString)
      if (localFile != null && localFile.exists()) {
        val parentDir =
          localFile.parentFile
            ?: return@withContext FileOperationResult(
              success = false,
              message = "Unable to resolve parent directory",
            )
        val targetFile = File(parentDir, safeName)
        if (targetFile.exists()) {
          return@withContext FileOperationResult(
            success = false,
            message = "\"$safeName\" already exists in this folder",
          )
        }
        if (!localFile.renameTo(targetFile)) {
          return@withContext FileOperationResult(
            success = false,
            message = "Rename failed on storage",
          )
        }
        val mime = DocumentCategoryClassifier.inferMimeType(safeName, null)
        val ext = DocumentCategoryClassifier.extractExtension(safeName)
        val category = DocumentCategoryClassifier.classify(mime, safeName)
        documentIndexDao.updateIdentityInPlace(
          id = doc.id,
          uriString = Uri.fromFile(targetFile).toString(),
          displayName = safeName,
          normalizedName = safeName.lowercase(),
          parentPath = parentDir.absolutePath,
          mimeType = mime,
          extension = ext,
          category = category.name,
          lastModifiedEpochMs = targetFile.lastModified(),
          sourceType = StorageSourceType.FILESYSTEM.name,
        )
        return@withContext FileOperationResult(
          success = true,
          affectedCount = 1,
          message = "Renamed to \"$safeName\"",
          createdUriString = Uri.fromFile(targetFile).toString(),
        )
      }

      // SAF content:// rename via DocumentsContract (updates URI returned by provider in-place)
      if (doc.uriString.startsWith("content://")) {
        val renamedUri =
          runCatching {
              DocumentsContract.renameDocument(
                context.contentResolver,
                Uri.parse(doc.uriString),
                safeName,
              )
            }
            .getOrNull()
            ?: return@withContext FileOperationResult(
              success = false,
              message = "This SAF provider does not allow renaming this document",
            )
        val mime = DocumentCategoryClassifier.inferMimeType(safeName, doc.mimeType)
        val ext = DocumentCategoryClassifier.extractExtension(safeName)
        val category = DocumentCategoryClassifier.classify(mime, safeName)
        documentIndexDao.updateIdentityInPlace(
          id = doc.id,
          uriString = renamedUri.toString(),
          displayName = safeName,
          normalizedName = safeName.lowercase(),
          parentPath = doc.parentPath,
          mimeType = mime,
          extension = ext,
          category = category.name,
          lastModifiedEpochMs = System.currentTimeMillis(),
          sourceType = doc.sourceType,
        )
        return@withContext FileOperationResult(
          success = true,
          affectedCount = 1,
          message = "Renamed to \"$safeName\"",
          createdUriString = renamedUri.toString(),
        )
      }

      FileOperationResult(success = false, message = "Source file no longer exists on device")
    }

  suspend fun copyOrMoveDocuments(
    documentIds: List<Long>,
    destinationDirPathOrUri: String,
    operationType: TransferOperationType,
    collisionPolicy: NameCollisionPolicy = NameCollisionPolicy.AUTO_RENAME,
    onProgress: suspend (completedFiles: Int, currentName: String, bytesDone: Long, totalBytes: Long) -> Unit =
      { _, _, _, _ -> },
  ): FileOperationResult =
    withContext(Dispatchers.IO) {
      val docs = getDocumentsByIdsChunked(documentIds)
      if (docs.isEmpty()) {
        return@withContext FileOperationResult(success = false, message = "No documents selected")
      }

      val totalBytes = docs.sumOf { it.sizeBytes.coerceAtLeast(0L) }
      var bytesCompletedBeforeCurrent = 0L
      var affected = 0
      var skipped = 0
      var failed = 0

      for ((index, doc) in docs.withIndex()) {
        coroutineContext.ensureActive()
        onProgress(index, doc.displayName, bytesCompletedBeforeCurrent, totalBytes)

        val singleResult =
          transferSingleDocument(
            doc = doc,
            destinationDirPathOrUri = destinationDirPathOrUri,
            operationType = operationType,
            collisionPolicy = collisionPolicy,
            onBytesCopied = { deltaBytes ->
              onProgress(index, doc.displayName, bytesCompletedBeforeCurrent + deltaBytes, totalBytes)
            },
          )

        bytesCompletedBeforeCurrent += doc.sizeBytes.coerceAtLeast(0L)
        when {
          singleResult.skippedCount > 0 -> skipped++
          singleResult.success -> affected++
          else -> failed++
        }
      }

      onProgress(docs.size, "", totalBytes, totalBytes)

      val verb = if (operationType == TransferOperationType.COPY) "Copied" else "Moved"
      val summary =
        buildString {
          append("$verb $affected item${if (affected == 1) "" else "s"}")
          if (skipped > 0) append(", $skipped skipped")
          if (failed > 0) append(", $failed failed")
        }

      FileOperationResult(
        success = failed == 0 && (affected > 0 || skipped > 0),
        affectedCount = affected,
        skippedCount = skipped,
        failedCount = failed,
        message = summary,
      )
    }

  /**
   * Moves documents to Prism's 30-day Recycle Bin:
   * - Local filesystem files: hidden `.PrismTrash` on the SAME volume via atomic O(1) rename.
   * - SAF-tree files: stages a verified copy into app-private `filesDir/prism_saf_trash` and then
   *   deletes the SAF document via `DocumentsContract.deleteDocument` (leaving source untouched if
   *   staging fails or provider refuses delete).
   */
  suspend fun moveToRecycleBin(
    documentIds: List<Long>,
    nowEpochMs: Long = System.currentTimeMillis(),
  ): FileOperationResult =
    withContext(Dispatchers.IO) {
      val docs = getDocumentsByIdsChunked(documentIds).filter { !it.isTrashed }
      if (docs.isEmpty()) {
        return@withContext FileOperationResult(success = false, message = "No active files to trash")
      }

      var trashed = 0
      var failed = 0

      for (doc in docs) {
        val ok = trashSingleDocument(doc, nowEpochMs)
        if (ok) trashed++ else failed++
      }

      FileOperationResult(
        success = trashed > 0 && failed == 0,
        affectedCount = trashed,
        failedCount = failed,
        message =
          if (failed == 0) "Moved $trashed item${if (trashed == 1) "" else "s"} to Recycle Bin"
          else "Moved $trashed to Recycle Bin ($failed failed)",
      )
    }

  suspend fun restoreFromRecycleBin(
    documentIds: List<Long>,
    collisionPolicy: NameCollisionPolicy = NameCollisionPolicy.AUTO_RENAME,
  ): FileOperationResult =
    withContext(Dispatchers.IO) {
      val entries = getRecycleBinByDocIdsChunked(documentIds)
      if (entries.isEmpty()) {
        return@withContext FileOperationResult(success = false, message = "No trashed items found")
      }

      var restored = 0
      var skipped = 0
      var failed = 0

      for (entry in entries) {
        val result = restoreSingleEntry(entry, collisionPolicy)
        when {
          result.skippedCount > 0 -> skipped++
          result.success -> restored++
          else -> failed++
        }
      }

      val msg =
        buildString {
          append("Restored $restored item${if (restored == 1) "" else "s"}")
          if (skipped > 0) append(", $skipped skipped")
          if (failed > 0) append(", $failed failed")
        }
      FileOperationResult(
        success = failed == 0 && (restored > 0 || skipped > 0),
        affectedCount = restored,
        skippedCount = skipped,
        failedCount = failed,
        message = msg,
      )
    }

  suspend fun permanentlyDeleteFromRecycleBin(documentIds: List<Long>): FileOperationResult =
    withContext(Dispatchers.IO) {
      val entries = getRecycleBinByDocIdsChunked(documentIds)
      if (entries.isEmpty()) {
        return@withContext FileOperationResult(success = false, message = "No trashed items found")
      }
      for (entry in entries) {
        deletePhysicalTrashFile(entry.trashedFilePath)
      }
      val ids = entries.map { it.documentId }
      database.withTransaction {
        deleteRecycleBinByDocIdsChunked(ids)
        deleteDocumentsByIdsChunked(ids)
      }
      FileOperationResult(
        success = true,
        affectedCount = entries.size,
        message = "Permanently deleted ${entries.size} item${if (entries.size == 1) "" else "s"}",
      )
    }

  suspend fun emptyRecycleBin(): FileOperationResult =
    withContext(Dispatchers.IO) {
      val entries = recycleBinDao.getAllEntries()
      if (entries.isEmpty()) {
        return@withContext FileOperationResult(success = true, message = "Recycle Bin is empty")
      }
      for (entry in entries) {
        deletePhysicalTrashFile(entry.trashedFilePath)
      }
      val docIds = entries.map { it.documentId }
      database.withTransaction {
        recycleBinDao.deleteAll()
        deleteDocumentsByIdsChunked(docIds)
      }
      FileOperationResult(
        success = true,
        affectedCount = entries.size,
        message = "Emptied Recycle Bin (${entries.size} items removed)",
      )
    }

  suspend fun purgeExpiredRecycleBin(nowEpochMs: Long = System.currentTimeMillis()): Int =
    withContext(Dispatchers.IO) {
      val expired = recycleBinDao.getExpiredEntries(nowEpochMs)
      if (expired.isEmpty()) return@withContext 0
      for (entry in expired) {
        deletePhysicalTrashFile(entry.trashedFilePath)
      }
      val docIds = expired.map { it.documentId }
      database.withTransaction {
        recycleBinDao.deleteExpiredEntries(nowEpochMs)
        deleteDocumentsByIdsChunked(docIds)
      }
      expired.size
    }

  fun buildShareIntent(documents: List<IndexedDocumentEntity>): Intent? {
    if (documents.isEmpty()) return null
    val resolvedPairs = ArrayList<Pair<IndexedDocumentEntity, Uri>>(documents.size)
    for (doc in documents) {
      val localFile = resolveLocalFile(doc.uriString)
      val uri =
        if (localFile != null && localFile.exists()) {
          runCatching {
              FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                localFile,
              )
            }
            .getOrNull()
        } else if (doc.uriString.startsWith("content://")) {
          Uri.parse(doc.uriString)
        } else {
          null
        }
      if (uri != null) resolvedPairs.add(doc to uri)
    }
    if (resolvedPairs.isEmpty()) return null

    return if (resolvedPairs.size == 1) {
      val (doc, uri) = resolvedPairs.first()
      Intent(Intent.ACTION_SEND).apply {
        type = doc.mimeType.ifBlank { "*/*" }
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
    } else {
      val shareUris = ArrayList<Uri>(resolvedPairs.size).apply {
        resolvedPairs.forEach { add(it.second) }
      }
      val commonMime =
        resolvedPairs.map { it.first.mimeType }.distinct().singleOrNull()?.ifBlank { "*/*" } ?: "*/*"
      Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = commonMime
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, shareUris)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
    }
  }

  internal fun resolveLocalCollisionFile(
    targetDir: File,
    desiredName: String,
    policy: NameCollisionPolicy,
  ): File? {
    val initial = File(targetDir, desiredName)
    if (!initial.exists()) return initial
    return when (policy) {
      NameCollisionPolicy.SKIP -> null
      NameCollisionPolicy.OVERWRITE -> initial
      NameCollisionPolicy.AUTO_RENAME -> {
        val (base, extSuffix) = splitBaseAndExtension(desiredName)
        var counter = 1
        while (counter < 10_000) {
          val candidate = File(targetDir, "$base ($counter)$extSuffix")
          if (!candidate.exists()) return candidate
          counter++
        }
        File(targetDir, "${base}_${UUID.randomUUID().toString().take(6)}$extSuffix")
      }
    }
  }

  internal fun resolveNameCollisionAgainstExisting(
    existingNamesLower: Set<String>,
    desiredName: String,
    policy: NameCollisionPolicy,
  ): String? {
    if (desiredName.lowercase() !in existingNamesLower) return desiredName
    return when (policy) {
      NameCollisionPolicy.SKIP -> null
      NameCollisionPolicy.OVERWRITE -> desiredName
      NameCollisionPolicy.AUTO_RENAME -> {
        val (base, extSuffix) = splitBaseAndExtension(desiredName)
        var counter = 1
        while (counter < 10_000) {
          val candidate = "$base ($counter)$extSuffix"
          if (candidate.lowercase() !in existingNamesLower) return candidate
          counter++
        }
        "${base}_${UUID.randomUUID().toString().take(6)}$extSuffix"
      }
    }
  }

  private suspend fun transferSingleDocument(
    doc: IndexedDocumentEntity,
    destinationDirPathOrUri: String,
    operationType: TransferOperationType,
    collisionPolicy: NameCollisionPolicy,
    onBytesCopied: suspend (Long) -> Unit,
  ): FileOperationResult {
    val sourceLocal = resolveLocalFile(doc.uriString)
    val isDestSaf = destinationDirPathOrUri.startsWith("content://")

    // Fast path: local -> local
    if (!isDestSaf) {
      val destDir = File(destinationDirPathOrUri)
      if (!destDir.exists() && !destDir.mkdirs()) {
        return FileOperationResult(success = false, failedCount = 1, message = "Destination unavailable")
      }

      // Skip moving a file into its own current directory under AUTO_RENAME/OVERWRITE if same path
      if (
        operationType == TransferOperationType.MOVE &&
          sourceLocal != null &&
          sameCanonicalPath(sourceLocal.parentFile, destDir)
      ) {
        return FileOperationResult(success = true, skippedCount = 1, message = "Already in destination")
      }

      val finalDest =
        resolveLocalCollisionFile(destDir, doc.displayName, collisionPolicy)
          ?: return FileOperationResult(success = true, skippedCount = 1, message = "Skipped collision")

      // Same-volume local MOVE fast path (atomic rename) when no overwrite-temp-dance needed
      if (
        operationType == TransferOperationType.MOVE &&
          sourceLocal != null &&
          sourceLocal.exists() &&
          customVerifier == null &&
          !finalDest.exists() &&
          isSameVolume(sourceLocal, destDir)
      ) {
        if (sourceLocal.renameTo(finalDest)) {
          updateMovedDocumentRowInPlace(doc, finalDest)
          onBytesCopied(finalDest.length())
          return FileOperationResult(
            success = true,
            affectedCount = 1,
            message = "Moved",
            createdUriString = Uri.fromFile(finalDest).toString(),
          )
        }
      }

      // Copy-then-verify-then-delete via temp staging file
      val tempFile = File(destDir, ".prism_part_${UUID.randomUUID()}.tmp")
      try {
        val (copiedBytes, sourceSha256) =
          streamCopyWithSha256(
            openSource = { openInputStream(doc.uriString) },
            openDest = { FileOutputStream(tempFile) },
            onBytesCopied = onBytesCopied,
          )
            ?: return FileOperationResult(
              success = false,
              failedCount = 1,
              message = "Unable to open source stream",
            )

        // Verify copied bytes + SHA-256 on disk BEFORE touching destination or deleting source!
        val verified =
          verifyCopiedTarget(
            destFile = tempFile,
            destUri = null,
            expectedBytes = copiedBytes,
            expectedSha256 = sourceSha256,
          )
        if (!verified) {
          tempFile.delete()
          return FileOperationResult(
            success = false,
            failedCount = 1,
            message = "Copy verification failed; source preserved",
          )
        }

        if (finalDest.exists() && collisionPolicy == NameCollisionPolicy.OVERWRITE) {
          if (!finalDest.delete()) {
            tempFile.delete()
            return FileOperationResult(
              success = false,
              failedCount = 1,
              message = "Could not overwrite existing destination",
            )
          }
        }

        if (!tempFile.renameTo(finalDest)) {
          tempFile.delete()
          return FileOperationResult(
            success = false,
            failedCount = 1,
            message = "Could not finalize destination file",
          )
        }

        if (operationType == TransferOperationType.MOVE) {
          // Delete source ONLY after verified copy is safely committed at finalDest
          val sourceDeleted = deleteSourceAfterVerifiedCopy(doc, sourceLocal)
          if (!sourceDeleted) {
            return FileOperationResult(
              success = false,
              failedCount = 1,
              message = "Copied safely, but failed to delete source",
            )
          }
          updateMovedDocumentRowInPlace(doc, finalDest)
        } else {
          insertCopiedLocalDocumentRow(finalDest)
        }

        return FileOperationResult(
          success = true,
          affectedCount = 1,
          message = if (operationType == TransferOperationType.COPY) "Copied" else "Moved",
          createdUriString = Uri.fromFile(finalDest).toString(),
        )
      } catch (ce: CancellationException) {
        tempFile.delete()
        throw ce
      } catch (_: Throwable) {
        tempFile.delete()
        return FileOperationResult(
          success = false,
          failedCount = 1,
          message = "Transfer failed; source preserved",
        )
      }
    }

    // Destination is a SAF tree URI
    val destTree =
      DocumentFile.fromTreeUri(context, Uri.parse(destinationDirPathOrUri))
        ?: return FileOperationResult(
          success = false,
          failedCount = 1,
          message = "SAF destination not writable",
        )
    val existingSiblings = destTree.listFiles()
    val existingNames = existingSiblings.mapNotNull { it.name?.lowercase() }.toSet()
    val resolvedName =
      resolveNameCollisionAgainstExisting(existingNames, doc.displayName, collisionPolicy)
        ?: return FileOperationResult(success = true, skippedCount = 1, message = "Skipped collision")

    if (collisionPolicy == NameCollisionPolicy.OVERWRITE) {
      existingSiblings.firstOrNull { it.name.equals(resolvedName, ignoreCase = true) }?.delete()
    }

    val createdDoc =
      destTree.createFile(doc.mimeType.ifBlank { "application/octet-stream" }, resolvedName)
        ?: return FileOperationResult(
          success = false,
          failedCount = 1,
          message = "Could not create destination SAF document",
        )

    return try {
      val (copiedBytes, sourceSha256) =
        streamCopyWithSha256(
          openSource = { openInputStream(doc.uriString) },
          openDest = { context.contentResolver.openOutputStream(createdDoc.uri, "wt") },
          onBytesCopied = onBytesCopied,
        )
          ?: run {
            createdDoc.delete()
            return FileOperationResult(success = false, failedCount = 1, message = "Stream error")
          }

      val verified =
        verifyCopiedTarget(
          destFile = null,
          destUri = createdDoc.uri,
          expectedBytes = copiedBytes,
          expectedSha256 = sourceSha256,
        )
      if (!verified) {
        createdDoc.delete()
        return FileOperationResult(
          success = false,
          failedCount = 1,
          message = "SAF copy verification failed; source preserved",
        )
      }

      val mime = DocumentCategoryClassifier.inferMimeType(resolvedName, doc.mimeType)
      val ext = DocumentCategoryClassifier.extractExtension(resolvedName)
      val category = DocumentCategoryClassifier.classify(mime, resolvedName)
      val now = System.currentTimeMillis()

      if (operationType == TransferOperationType.MOVE) {
        val deleted = deleteSourceAfterVerifiedCopy(doc, sourceLocal)
        if (!deleted) {
          return FileOperationResult(
            success = false,
            failedCount = 1,
            message = "Copied safely to SAF, but failed to delete source",
          )
        }
        documentIndexDao.updateIdentityInPlace(
          id = doc.id,
          uriString = createdDoc.uri.toString(),
          displayName = resolvedName,
          normalizedName = resolvedName.lowercase(),
          parentPath = destinationDirPathOrUri,
          mimeType = mime,
          extension = ext,
          category = category.name,
          lastModifiedEpochMs = now,
          sourceType = StorageSourceType.SAF.name,
        )
      } else {
        documentIndexDao.upsert(
          IndexedDocumentEntity(
            uriString = createdDoc.uri.toString(),
            displayName = resolvedName,
            normalizedName = resolvedName.lowercase(),
            parentPath = destinationDirPathOrUri,
            mimeType = mime,
            extension = ext,
            category = category.name,
            sizeBytes = copiedBytes,
            lastModifiedEpochMs = now,
            sourceType = StorageSourceType.SAF.name,
            lastIndexedEpochMs = now,
          )
        )
      }
      FileOperationResult(
        success = true,
        affectedCount = 1,
        message = if (operationType == TransferOperationType.COPY) "Copied" else "Moved",
        createdUriString = createdDoc.uri.toString(),
      )
    } catch (ce: CancellationException) {
      createdDoc.delete()
      throw ce
    } catch (_: Throwable) {
      createdDoc.delete()
      FileOperationResult(
        success = false,
        failedCount = 1,
        message = "SAF transfer failed; source preserved",
      )
    }
  }

  private suspend fun trashSingleDocument(
    doc: IndexedDocumentEntity,
    nowEpochMs: Long,
  ): Boolean {
    val safeDisplayName = sanitizeStagedFileName(doc.displayName)
    val localFile = resolveLocalFile(doc.uriString)
    if (localFile != null && localFile.exists()) {
      val volumeRoot = volumeRootResolver(localFile)
      val trashDir = ensureHiddenTrashDirectory(volumeRoot) ?: return false
      val stagedName = "${UUID.randomUUID().toString().take(8)}_$safeDisplayName"
      val stagedFile = File(trashDir, stagedName)

      // Same-volume O(1) rename into <volumeRoot>/.PrismTrash/
      if (!localFile.renameTo(stagedFile)) {
        return false
      }

      database.withTransaction {
        documentIndexDao.markTrashed(
          id = doc.id,
          trashedAtEpochMs = nowEpochMs,
          originalParentPath = doc.parentPath,
        )
        recycleBinDao.upsert(
          RecycleBinEntryEntity(
            documentId = doc.id,
            originalUriString = doc.uriString,
            originalPath = localFile.absolutePath,
            trashedFilePath = stagedFile.absolutePath,
            displayName = doc.displayName,
            sizeBytes = doc.sizeBytes,
            trashedAtEpochMs = nowEpochMs,
            expiresAtEpochMs = nowEpochMs + DocumentRepository.RECYCLE_BIN_RETENTION_MS,
          )
        )
      }
      return true
    }

    // SAF-tree file: stage a verified copy into app-private vault before deleting the SAF document
    if (doc.uriString.startsWith("content://")) {
      val safVault = ensureSafTrashVaultDirectory() ?: return false
      val stagedFile = File(safVault, "${UUID.randomUUID().toString().take(8)}_$safeDisplayName")
      val copied =
        runCatching {
            streamCopyWithSha256(
              openSource = { openInputStream(doc.uriString) },
              openDest = { FileOutputStream(stagedFile) },
              onBytesCopied = {},
            )
          }
          .getOrNull()
          ?: run {
            stagedFile.delete()
            return false
          }

      val verified =
        verifyCopiedTarget(
          destFile = stagedFile,
          destUri = null,
          expectedBytes = copied.first,
          expectedSha256 = copied.second,
        )
      if (!verified) {
        stagedFile.delete()
        return false
      }

      val deletedSaf =
        runCatching {
            DocumentsContract.deleteDocument(context.contentResolver, Uri.parse(doc.uriString))
          }
          .getOrDefault(false)
      if (!deletedSaf) {
        stagedFile.delete()
        return false
      }

      database.withTransaction {
        documentIndexDao.markTrashed(
          id = doc.id,
          trashedAtEpochMs = nowEpochMs,
          originalParentPath = doc.parentPath,
        )
        recycleBinDao.upsert(
          RecycleBinEntryEntity(
            documentId = doc.id,
            originalUriString = doc.uriString,
            originalPath = doc.parentPath,
            trashedFilePath = stagedFile.absolutePath,
            displayName = doc.displayName,
            sizeBytes = copied.first,
            trashedAtEpochMs = nowEpochMs,
            expiresAtEpochMs = nowEpochMs + DocumentRepository.RECYCLE_BIN_RETENTION_MS,
          )
        )
      }
      return true
    }

    return false
  }

  private suspend fun restoreSingleEntry(
    entry: RecycleBinEntryEntity,
    collisionPolicy: NameCollisionPolicy,
  ): FileOperationResult {
    val stagedFile = File(entry.trashedFilePath)
    if (!stagedFile.exists()) {
      return FileOperationResult(
        success = false,
        failedCount = 1,
        message = "Staged trash file is missing on disk",
      )
    }

    // Case 1: Original was a SAF document
    if (entry.originalUriString.startsWith("content://")) {
      val targetTree =
        runCatching { DocumentFile.fromTreeUri(context, Uri.parse(entry.originalPath)) }.getOrNull()
      if (targetTree != null && targetTree.canWrite()) {
        val existingSiblings = targetTree.listFiles()
        val existingNames = existingSiblings.mapNotNull { it.name?.lowercase() }.toSet()
        val resolvedName =
          resolveNameCollisionAgainstExisting(existingNames, entry.displayName, collisionPolicy)
            ?: return FileOperationResult(success = true, skippedCount = 1, message = "Skipped")
        if (collisionPolicy == NameCollisionPolicy.OVERWRITE) {
          existingSiblings.firstOrNull { it.name.equals(resolvedName, true) }?.delete()
        }
        val mime = DocumentCategoryClassifier.inferMimeType(resolvedName, null)
        val created =
          targetTree.createFile(mime, resolvedName)
            ?: return FileOperationResult(success = false, failedCount = 1, message = "SAF restore failed")
        val copied =
          streamCopyWithSha256(
            openSource = { FileInputStream(stagedFile) },
            openDest = { context.contentResolver.openOutputStream(created.uri, "wt") },
            onBytesCopied = {},
          )
            ?: run {
              created.delete()
              return FileOperationResult(success = false, failedCount = 1, message = "Restore write failed")
            }
        if (!verifyCopiedTarget(null, created.uri, copied.first, copied.second)) {
          created.delete()
          return FileOperationResult(success = false, failedCount = 1, message = "Restore verify failed")
        }
        stagedFile.delete()
        database.withTransaction {
          documentIndexDao.restoreFromTrashAtLocation(
            id = entry.documentId,
            uriString = created.uri.toString(),
            displayName = resolvedName,
            normalizedName = resolvedName.lowercase(),
            parentPath = entry.originalPath,
            lastModifiedEpochMs = System.currentTimeMillis(),
          )
          recycleBinDao.deleteByDocumentId(entry.documentId)
        }
        return FileOperationResult(success = true, affectedCount = 1, message = "Restored to SAF")
      }
      return FileOperationResult(
        success = false,
        failedCount = 1,
        message = "Original SAF folder permission is no longer writable",
      )
    }

    // Case 2: Original was a local filesystem file — O(1) same-volume rename back to originalPath
    val originalFile = File(entry.originalPath)
    val parentDir = originalFile.parentFile ?: return FileOperationResult(
      success = false,
      failedCount = 1,
      message = "Original directory cannot be resolved",
    )
    if (!parentDir.exists() && !parentDir.mkdirs()) {
      return FileOperationResult(
        success = false,
        failedCount = 1,
        message = "Could not recreate original directory",
      )
    }

    val targetFile =
      resolveLocalCollisionFile(parentDir, originalFile.name, collisionPolicy)
        ?: return FileOperationResult(success = true, skippedCount = 1, message = "Skipped collision")

    if (targetFile.exists() && collisionPolicy == NameCollisionPolicy.OVERWRITE) {
      if (!targetFile.delete()) {
        return FileOperationResult(
          success = false,
          failedCount = 1,
          message = "Could not overwrite existing file on restore",
        )
      }
    }

    val movedBack = stagedFile.renameTo(targetFile)
    if (!movedBack) {
      return FileOperationResult(
        success = false,
        failedCount = 1,
        message = "Could not rename trashed file back to original path",
      )
    }

    database.withTransaction {
      documentIndexDao.restoreFromTrashAtLocation(
        id = entry.documentId,
        uriString = Uri.fromFile(targetFile).toString(),
        displayName = targetFile.name,
        normalizedName = targetFile.name.lowercase(),
        parentPath = parentDir.absolutePath,
        lastModifiedEpochMs = targetFile.lastModified(),
      )
      recycleBinDao.deleteByDocumentId(entry.documentId)
    }

    return FileOperationResult(
      success = true,
      affectedCount = 1,
      message = "Restored",
      createdUriString = Uri.fromFile(targetFile).toString(),
    )
  }

  private suspend fun streamCopyWithSha256(
    openSource: () -> InputStream?,
    openDest: () -> OutputStream?,
    onBytesCopied: suspend (Long) -> Unit,
  ): Pair<Long, String>? {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(COPY_BUFFER_SIZE)
    var total = 0L
    var lastReportedBytes = 0L
    var lastReportNanos = System.nanoTime()

    openSource()?.use { input ->
      openDest()?.use { output ->
        while (true) {
          coroutineContext.ensureActive()
          val read = input.read(buffer)
          if (read <= 0) break
          output.write(buffer, 0, read)
          digest.update(buffer, 0, read)
          total += read
          val nowNanos = System.nanoTime()
          if (
            total - lastReportedBytes >= PROGRESS_MIN_BYTES_DELTA ||
              nowNanos - lastReportNanos >= PROGRESS_MIN_INTERVAL_NS
          ) {
            lastReportedBytes = total
            lastReportNanos = nowNanos
            onBytesCopied(total)
          }
        }
        if (total != lastReportedBytes) {
          onBytesCopied(total)
        }
        output.flush()
      } ?: return null
    } ?: return null

    return total to digest.digest().toHexString()
  }

  private suspend fun verifyCopiedTarget(
    destFile: File?,
    destUri: Uri?,
    expectedBytes: Long,
    expectedSha256: String,
  ): Boolean {
    customVerifier?.let { verifier ->
      return verifier(destFile, destUri, expectedBytes, expectedSha256)
    }

    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(COPY_BUFFER_SIZE)
    var actualBytes = 0L

    val stream: InputStream =
      when {
        destFile != null -> {
          if (!destFile.exists() || destFile.length() != expectedBytes) return false
          FileInputStream(destFile)
        }
        destUri != null -> context.contentResolver.openInputStream(destUri) ?: return false
        else -> return false
      }

    stream.use { input ->
      while (true) {
        coroutineContext.ensureActive()
        val read = input.read(buffer)
        if (read <= 0) break
        digest.update(buffer, 0, read)
        actualBytes += read
      }
    }

    return actualBytes == expectedBytes && digest.digest().toHexString() == expectedSha256
  }

  private fun deleteSourceAfterVerifiedCopy(
    doc: IndexedDocumentEntity,
    sourceLocal: File?,
  ): Boolean {
    return if (sourceLocal != null && sourceLocal.exists()) {
      sourceLocal.delete()
    } else if (doc.uriString.startsWith("content://")) {
      runCatching {
          DocumentsContract.deleteDocument(context.contentResolver, Uri.parse(doc.uriString))
        }
        .getOrDefault(false)
    } else {
      false
    }
  }

  private suspend fun updateMovedDocumentRowInPlace(
    doc: IndexedDocumentEntity,
    newFile: File,
  ) {
    val mime = DocumentCategoryClassifier.inferMimeType(newFile.name, doc.mimeType)
    val ext = DocumentCategoryClassifier.extractExtension(newFile.name)
    val category = DocumentCategoryClassifier.classify(mime, newFile.name)
    documentIndexDao.updateIdentityInPlace(
      id = doc.id,
      uriString = Uri.fromFile(newFile).toString(),
      displayName = newFile.name,
      normalizedName = newFile.name.lowercase(),
      parentPath = newFile.parent.orEmpty(),
      mimeType = mime,
      extension = ext,
      category = category.name,
      lastModifiedEpochMs = newFile.lastModified(),
      sourceType = StorageSourceType.FILESYSTEM.name,
    )
  }

  private suspend fun insertCopiedLocalDocumentRow(file: File) {
    val mime = DocumentCategoryClassifier.inferMimeType(file.name, null)
    val ext = DocumentCategoryClassifier.extractExtension(file.name)
    val category = DocumentCategoryClassifier.classify(mime, file.name)
    val now = System.currentTimeMillis()
    documentIndexDao.upsert(
      IndexedDocumentEntity(
        uriString = Uri.fromFile(file).toString(),
        displayName = file.name,
        normalizedName = file.name.lowercase(),
        parentPath = file.parent.orEmpty(),
        mimeType = mime,
        extension = ext,
        category = category.name,
        sizeBytes = file.length(),
        lastModifiedEpochMs = file.lastModified(),
        sourceType = StorageSourceType.FILESYSTEM.name,
        lastIndexedEpochMs = now,
      )
    )
  }

  private fun ensureHiddenTrashDirectory(volumeRoot: File): File? {
    val trashDir = File(volumeRoot, HIDDEN_TRASH_DIR_NAME)
    if (!trashDir.exists() && !trashDir.mkdirs()) return null
    val nomedia = File(trashDir, ".nomedia")
    if (!nomedia.exists()) {
      runCatching { nomedia.createNewFile() }
    }
    return trashDir
  }

  private fun ensureSafTrashVaultDirectory(): File? {
    val dir = File(context.filesDir, SAF_TRASH_VAULT_DIR_NAME)
    if (!dir.exists() && !dir.mkdirs()) return null
    return dir
  }

  private fun deletePhysicalTrashFile(trashedPath: String) {
    if (trashedPath.isBlank() || trashedPath.startsWith("content://")) return
    runCatching { File(trashedPath).delete() }
  }

  private suspend fun isSameVolume(source: File, destDir: File): Boolean {
    val srcRoot = volumeRootResolver(source)
    val dstRoot = volumeRootResolver(destDir)
    return sameCanonicalPath(srcRoot, dstRoot)
  }

  private fun sameCanonicalPath(a: File?, b: File?): Boolean {
    if (a == null || b == null) return false
    val pA = runCatching { a.canonicalPath }.getOrElse { a.absolutePath }
    val pB = runCatching { b.canonicalPath }.getOrElse { b.absolutePath }
    return pA == pB
  }

  private fun openInputStream(uriString: String): InputStream? {
    val local = resolveLocalFile(uriString)
    return if (local != null && local.exists()) {
      FileInputStream(local)
    } else {
      context.contentResolver.openInputStream(Uri.parse(uriString))
    }
  }

  private fun resolveLocalFile(uriString: String): File? =
    when {
      uriString.startsWith("file://") -> Uri.parse(uriString).path?.let { File(it) }
      uriString.startsWith("/") -> File(uriString)
      else -> null
    }

  private fun splitBaseAndExtension(name: String): Pair<String, String> {
    val dot = name.lastIndexOf('.')
    return if (dot > 0 && dot < name.length - 1) {
      name.substring(0, dot) to name.substring(dot)
    } else {
      name to ""
    }
  }

  private suspend fun getDocumentsByIdsChunked(ids: List<Long>): List<IndexedDocumentEntity> {
    if (ids.isEmpty()) return emptyList()
    val distinct = ids.distinct()
    if (distinct.size <= SQLITE_IN_CHUNK_SIZE) return documentIndexDao.getByIds(distinct)
    return distinct.chunked(SQLITE_IN_CHUNK_SIZE).flatMap { documentIndexDao.getByIds(it) }
  }

  private suspend fun getRecycleBinByDocIdsChunked(ids: List<Long>): List<RecycleBinEntryEntity> {
    if (ids.isEmpty()) return emptyList()
    val distinct = ids.distinct()
    if (distinct.size <= SQLITE_IN_CHUNK_SIZE) return recycleBinDao.getByDocumentIds(distinct)
    return distinct.chunked(SQLITE_IN_CHUNK_SIZE).flatMap { recycleBinDao.getByDocumentIds(it) }
  }

  private suspend fun deleteDocumentsByIdsChunked(ids: List<Long>) {
    if (ids.isEmpty()) return
    ids.distinct().chunked(SQLITE_IN_CHUNK_SIZE).forEach { chunk ->
      documentIndexDao.deleteByIds(chunk)
    }
  }

  private suspend fun deleteRecycleBinByDocIdsChunked(ids: List<Long>) {
    if (ids.isEmpty()) return
    ids.distinct().chunked(SQLITE_IN_CHUNK_SIZE).forEach { chunk ->
      recycleBinDao.deleteByDocumentIds(chunk)
    }
  }

  private fun sanitizeStagedFileName(rawName: String): String {
    val sanitized = rawName.replace('/', '_').replace('\\', '_').replace('\u0000', '_').trim()
    return if (sanitized.isEmpty() || sanitized == "." || sanitized == "..") "document" else sanitized
  }

  private fun ByteArray.toHexString(): String {
    val chars = CharArray(size * 2)
    for (i in indices) {
      val v = this[i].toInt() and 0xFF
      chars[i * 2] = HEX_CHARS[v ushr 4]
      chars[i * 2 + 1] = HEX_CHARS[v and 0x0F]
    }
    return String(chars)
  }
}

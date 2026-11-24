package com.prism.docs.core.data.storage

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.OpenableColumns
import android.provider.Settings
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.prism.docs.core.data.model.IncomingDocumentRequest
import com.prism.docs.core.data.model.SafFolderGrant
import com.prism.docs.core.data.model.StorageAccessLevel
import com.prism.docs.core.data.model.StorageAccessState
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StoragePermissionManager
@Inject
constructor(
  @ApplicationContext private val context: Context,
) {
  fun hasAllFilesAccess(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      runCatching { Environment.isExternalStorageManager() }.getOrDefault(false)
    } else {
      val readGranted =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
          PackageManager.PERMISSION_GRANTED
      val writeGranted =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
          PackageManager.PERMISSION_GRANTED
      readGranted && writeGranted
    }
  }

  fun requiresLegacyStoragePermissions(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.R

  fun legacyStoragePermissions(): Array<String> =
    arrayOf(
      Manifest.permission.READ_EXTERNAL_STORAGE,
      Manifest.permission.WRITE_EXTERNAL_STORAGE,
    )

  /**
   * Builds the system settings intent to request MANAGE_EXTERNAL_STORAGE on Android 11+ (API 30+).
   * Falls back to the generic All Files Access list if the package-scoped intent cannot resolve.
   */
  fun buildAllFilesAccessIntent(): Intent {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      val packageIntent =
        Intent(
          Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
          Uri.parse("package:${context.packageName}"),
        )
      if (packageIntent.resolveActivity(context.packageManager) != null) {
        return packageIntent
      }
      return Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
    }
    return Intent(
      Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
      Uri.parse("package:${context.packageName}"),
    )
  }

  fun persistSafTreePermission(treeUri: Uri): SafFolderGrant? {
    return runCatching {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(treeUri, flags)
        val docFile = DocumentFile.fromTreeUri(context, treeUri)
        val name =
          docFile?.name?.takeIf { it.isNotBlank() }
            ?: treeUri.lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/')
            ?: "Selected folder"
        SafFolderGrant(
          uriString = treeUri.toString(),
          displayName = name,
          grantedAtEpochMs = System.currentTimeMillis(),
        )
      }
      .getOrNull()
  }

  fun releaseSafTreePermission(uriString: String) {
    runCatching {
      val uri = Uri.parse(uriString)
      val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
      context.contentResolver.releasePersistableUriPermission(uri, flags)
    }
  }

  fun computeAccessState(savedGrants: List<SafFolderGrant>): StorageAccessState {
    val activePersisted =
      context.contentResolver.persistedUriPermissions
        .filter { it.isReadPermission }
        .map { it.uri.toString() }
        .toSet()

    val validGrants = savedGrants.filter { it.uriString in activePersisted }
    val fullAccess = hasAllFilesAccess()

    val level =
      when {
        fullAccess -> StorageAccessLevel.FULL_ALL_FILES
        validGrants.isNotEmpty() -> StorageAccessLevel.SAF_DEGRADED
        else -> StorageAccessLevel.LIMITED_NONE
      }

    return StorageAccessState(
      level = level,
      sdkInt = Build.VERSION.SDK_INT,
      hasAllFilesPermission = fullAccess,
      safFolders = validGrants,
    )
  }

  fun resolveIncomingDocument(
    uri: Uri,
    explicitMimeType: String?,
    sourceAction: String,
  ): IncomingDocumentRequest {
    var displayName = ""
    var sizeBytes = -1L

    if (uri.scheme.equals("file", ignoreCase = true)) {
      val file = uri.path?.let { File(it) }
      if (file != null) {
        displayName = file.name
        if (file.exists()) sizeBytes = file.length()
      }
    } else {
      runCatching {
        context.contentResolver
          .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
          ?.use { cursor ->
            if (cursor.moveToFirst()) {
              val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
              val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
              if (nameIdx >= 0 && !cursor.isNull(nameIdx)) {
                displayName = cursor.getString(nameIdx)
              }
              if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                sizeBytes = cursor.getLong(sizeIdx)
              }
            }
          }
      }
    }

    if (displayName.isBlank()) {
      displayName = uri.lastPathSegment?.substringAfterLast('/') ?: "Untitled document"
    }

    val resolvedMime =
      explicitMimeType?.takeIf { it.isNotBlank() && it != "*/*" }
        ?: runCatching { context.contentResolver.getType(uri) }.getOrNull()
        ?: inferMimeFromName(displayName)
        ?: "application/octet-stream"

    return IncomingDocumentRequest(
      uriString = uri.toString(),
      displayName = displayName,
      mimeType = resolvedMime,
      sizeBytes = sizeBytes,
      sourceAction = sourceAction,
    )
  }

  private fun inferMimeFromName(name: String): String? {
    val ext = name.substringAfterLast('.', "").lowercase()
    if (ext.isBlank()) return null
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
  }
}

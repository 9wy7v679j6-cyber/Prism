package com.prism.docs.feature.files

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prism.docs.core.data.db.IndexedDocumentEntity
import com.prism.docs.core.data.model.NameCollisionPolicy
import com.prism.docs.core.data.model.ScannedDocumentMetadata
import com.prism.docs.core.data.model.StorageSourceType
import com.prism.docs.core.data.model.StorageVolumeRoot
import com.prism.docs.core.data.model.TransferOperationType
import com.prism.docs.core.data.model.TransferProgress
import com.prism.docs.core.ui.glass.GlassElevation
import com.prism.docs.core.ui.glass.GlassSurface
import com.prism.docs.core.ui.theme.PrismColors

@Composable
fun CreateFolderDialog(
  onDismiss: () -> Unit,
  onCreate: (String) -> Unit,
) {
  var name by remember { mutableStateOf("") }
  val isValid =
    name.trim().let { it.isNotEmpty() && it != "." && it != ".." && !it.contains('/') && !it.contains('\\') }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("New Folder") },
    text = {
      OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("Folder name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("create_folder_input"),
      )
    },
    confirmButton = {
      Button(
        onClick = { onCreate(name.trim()) },
        enabled = isValid,
        modifier = Modifier.testTag("create_folder_confirm"),
      ) {
        Text("Create")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@Composable
fun RenameDocumentDialog(
  document: IndexedDocumentEntity,
  onDismiss: () -> Unit,
  onConfirm: (String) -> Unit,
) {
  var name by remember(document.id) { mutableStateOf(document.displayName) }
  val trimmed = name.trim()
  val isValid =
    trimmed.isNotEmpty() &&
      trimmed != "." &&
      trimmed != ".." &&
      !trimmed.contains('/') &&
      !trimmed.contains('\\')

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Rename Document") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("New file name") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("rename_document_input"),
        )
        Text(
          text = "Favorites, tags, and collections remain linked after renaming.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onConfirm(trimmed) },
        enabled = isValid && trimmed != document.displayName,
        modifier = Modifier.testTag("rename_document_confirm"),
      ) {
        Text("Rename")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@Composable
fun DestinationFolderPickerDialog(
  state: DestinationPickerState,
  onSelectRoot: (StorageVolumeRoot) -> Unit,
  onSelectSubfolder: (ScannedDocumentMetadata) -> Unit,
  onSelectBreadcrumb: (Int) -> Unit,
  onCollisionPolicyChange: (NameCollisionPolicy) -> Unit,
  onDismiss: () -> Unit,
  onConfirm: () -> Unit,
) {
  val colorScheme = MaterialTheme.colorScheme
  val actionLabel =
    if (state.operationType == TransferOperationType.COPY) "Copy Here" else "Move Here"

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text =
          if (state.operationType == TransferOperationType.COPY)
            "Copy ${state.targetDocumentIds.size} item(s) to…"
          else "Move ${state.targetDocumentIds.size} item(s) to…"
      )
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Breadcrumbs
        LazyRow(
          horizontalArrangement = Arrangement.spacedBy(4.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          item {
            Text(
              text = "Volumes",
              style = MaterialTheme.typography.labelLarge,
              color = colorScheme.primary,
              modifier =
                Modifier.clip(RoundedCornerShape(8.dp))
                  .clickable { onSelectBreadcrumb(-1) }
                  .padding(horizontal = 6.dp, vertical = 4.dp),
            )
          }
          itemsIndexed(state.breadcrumbs) { idx, seg ->
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
              )
              Text(
                text = seg.label,
                style = MaterialTheme.typography.labelLarge,
                color =
                  if (idx == state.breadcrumbs.lastIndex) colorScheme.onSurface
                  else colorScheme.primary,
                modifier =
                  Modifier.clip(RoundedCornerShape(8.dp))
                    .clickable { onSelectBreadcrumb(idx) }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
              )
            }
          }
        }

        // Collision policy selector
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          val policies =
            listOf(
              NameCollisionPolicy.AUTO_RENAME to "Auto-rename",
              NameCollisionPolicy.SKIP to "Skip",
              NameCollisionPolicy.OVERWRITE to "Overwrite",
            )
          items(policies) { (policy, label) ->
            FilterChip(
              selected = state.collisionPolicy == policy,
              onClick = { onCollisionPolicyChange(policy) },
              label = { Text(label) },
            )
          }
        }

        // Folder browser list
        LazyColumn(
          modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp, max = 260.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          if (state.breadcrumbs.isEmpty()) {
            items(state.roots, key = { it.id }) { root ->
              Row(
                modifier =
                  Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .clickable { onSelectRoot(root) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Icon(
                  imageVector =
                    if (root.sourceType == StorageSourceType.SAF) Icons.Rounded.FolderOpen
                    else Icons.Rounded.Smartphone,
                  contentDescription = null,
                  tint = colorScheme.primary,
                )
                Text(
                  text = root.displayName,
                  style = MaterialTheme.typography.bodyMedium,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.weight(1f),
                )
                Icon(imageVector = Icons.Rounded.ChevronRight, contentDescription = null)
              }
            }
          } else if (state.subfolders.isEmpty()) {
            item {
              Text(
                text = "No subfolders here. Tap \"$actionLabel\" to use this folder.",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp),
              )
            }
          } else {
            items(state.subfolders, key = { it.uriString }) { folder ->
              Row(
                modifier =
                  Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .clickable { onSelectSubfolder(folder) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Icon(
                  imageVector = Icons.Rounded.Folder,
                  contentDescription = null,
                  tint = PrismColors.AmberSlide,
                )
                Text(
                  text = folder.displayName,
                  style = MaterialTheme.typography.bodyMedium,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.weight(1f),
                )
                Icon(imageVector = Icons.Rounded.ChevronRight, contentDescription = null)
              }
            }
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = onConfirm,
        enabled = state.currentPathOrUri != null,
        modifier = Modifier.testTag("picker_confirm_button"),
      ) {
        Text(actionLabel)
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@Composable
fun TransferProgressBanner(
  progress: TransferProgress,
  onCancel: () -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme
  GlassSurface(
    cornerRadius = 22.dp,
    elevation = GlassElevation.FLOATING,
    accentTint = colorScheme.primary,
    modifier = modifier.fillMaxWidth().testTag("transfer_progress_banner"),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          val title =
            if (progress.isRunning) {
              val verb = if (progress.type == TransferOperationType.COPY) "Copying" else "Moving"
              "$verb ${progress.completedFiles}/${progress.totalFiles}" +
                if (progress.isWorkManagerBacked) " • Background" else ""
            } else {
              progress.summaryMessage ?: "Transfer complete"
            }
          Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          if (progress.isRunning && progress.currentFileName.isNotBlank()) {
            Text(
              text = progress.currentFileName,
              style = MaterialTheme.typography.bodySmall,
              color = colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
        if (progress.isRunning) {
          TextButton(
            onClick = onCancel,
            modifier = Modifier.testTag("cancel_transfer_button"),
          ) {
            Text("Cancel")
          }
        } else {
          IconButton(onClick = onDismiss) {
            Icon(imageVector = Icons.Rounded.Close, contentDescription = "Dismiss transfer summary")
          }
        }
      }
      if (progress.isRunning) {
        LinearProgressIndicator(
          progress = { progress.fraction },
          modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp)),
        )
      }
    }
  }
}

@Composable
fun MultiSelectBatchBar(
  selectedCount: Int,
  onCopy: () -> Unit,
  onMove: () -> Unit,
  onShare: () -> Unit,
  onTrash: () -> Unit,
  onClear: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme
  GlassSurface(
    cornerRadius = 26.dp,
    elevation = GlassElevation.FLOATING,
    accentTint = colorScheme.primary,
    modifier = modifier.testTag("multi_select_batch_bar"),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      IconButton(onClick = onClear, modifier = Modifier.testTag("batch_clear_selection")) {
        Icon(imageVector = Icons.Rounded.Close, contentDescription = "Clear selection")
      }
      Text(
        text = "$selectedCount",
        style = MaterialTheme.typography.titleSmall,
        color = colorScheme.primary,
      )
      Spacer(modifier = Modifier.size(6.dp))
      IconButton(onClick = onCopy, modifier = Modifier.testTag("batch_copy_button")) {
        Icon(imageVector = Icons.Rounded.ContentCopy, contentDescription = "Copy selected")
      }
      IconButton(onClick = onMove, modifier = Modifier.testTag("batch_move_button")) {
        Icon(
          imageVector = Icons.AutoMirrored.Rounded.DriveFileMove,
          contentDescription = "Move selected",
        )
      }
      IconButton(onClick = onShare, modifier = Modifier.testTag("batch_share_button")) {
        Icon(imageVector = Icons.Rounded.Share, contentDescription = "Share selected")
      }
      IconButton(onClick = onTrash, modifier = Modifier.testTag("batch_trash_button")) {
        Icon(
          imageVector = Icons.Rounded.DeleteOutline,
          contentDescription = "Move selected to Recycle Bin",
          tint = PrismColors.CoralPdf,
        )
      }
    }
  }
}

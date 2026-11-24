package com.prism.docs.feature.files

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.material.icons.automirrored.rounded.Label
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
import com.prism.docs.core.ui.theme.LocalPrismVisualTokens
import com.prism.docs.core.ui.theme.PrismDimens

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
    title = { Text(stringResource(R.string.dialog_new_folder_title)) },
    text = {
      OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text(stringResource(R.string.dialog_new_folder_label)) },
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
        Text(stringResource(R.string.dialog_new_folder_create))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
    },
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
    title = { Text(stringResource(R.string.dialog_rename_title)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(PrismDimens.SpaceSmallMedium)) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text(stringResource(R.string.dialog_rename_label)) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("rename_document_input"),
        )
        Text(
          text = stringResource(R.string.dialog_rename_hint),
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
        Text(stringResource(R.string.action_rename))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
    },
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
  val tokens = LocalPrismVisualTokens.current
  val actionLabel =
    stringResource(
      if (state.operationType == TransferOperationType.COPY) R.string.picker_copy_here
      else R.string.picker_move_here
    )
  val itemCount = state.targetDocumentIds.size
  val dialogTitle =
    pluralStringResource(
      id =
        if (state.operationType == TransferOperationType.COPY) R.plurals.picker_copy_title
        else R.plurals.picker_move_title,
      count = itemCount,
      itemCount,
    )
  val selectedStateLabel = stringResource(R.string.state_selected)
  val notSelectedStateLabel = stringResource(R.string.state_not_selected)

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(text = dialogTitle) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(PrismDimens.SpaceMediumLarge)) {
        LazyRow(
          horizontalArrangement = Arrangement.spacedBy(PrismDimens.SpaceSmall),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          item {
            Text(
              text = stringResource(R.string.storage_volumes_breadcrumb),
              style = MaterialTheme.typography.labelLarge,
              color = colorScheme.primary,
              modifier =
                Modifier.defaultMinSize(minHeight = PrismDimens.TouchTargetMin)
                  .clip(RoundedCornerShape(8.dp))
                  .clickable(role = Role.Button) { onSelectBreadcrumb(-1) }
                  .padding(horizontal = 6.dp, vertical = 12.dp),
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
                  Modifier.defaultMinSize(minHeight = PrismDimens.TouchTargetMin)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button) { onSelectBreadcrumb(idx) }
                    .padding(horizontal = 6.dp, vertical = 12.dp),
              )
            }
          }
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(PrismDimens.SpaceSmallMedium)) {
          val policies =
            listOf(
              NameCollisionPolicy.AUTO_RENAME to R.string.picker_policy_auto_rename,
              NameCollisionPolicy.SKIP to R.string.picker_policy_skip,
              NameCollisionPolicy.OVERWRITE to R.string.picker_policy_overwrite,
            )
          items(policies) { (policy, labelRes) ->
            val selected = state.collisionPolicy == policy
            FilterChip(
              selected = selected,
              onClick = { onCollisionPolicyChange(policy) },
              label = { Text(stringResource(labelRes)) },
              modifier =
                Modifier.defaultMinSize(minHeight = PrismDimens.TouchTargetMin).semantics {
                  stateDescription = if (selected) selectedStateLabel else notSelectedStateLabel
                },
            )
          }
        }

        LazyColumn(
          modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp, max = 260.dp),
          verticalArrangement = Arrangement.spacedBy(PrismDimens.SpaceSmallMedium),
        ) {
          if (state.breadcrumbs.isEmpty()) {
            items(state.roots, key = { it.id }) { root ->
              Row(
                modifier =
                  Modifier.fillMaxWidth()
                    .defaultMinSize(minHeight = PrismDimens.TouchTargetMin)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .clickable(role = Role.Button) { onSelectRoot(root) }
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
                text = stringResource(R.string.picker_no_subfolders, actionLabel),
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
                    .defaultMinSize(minHeight = PrismDimens.TouchTargetMin)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .clickable(role = Role.Button) { onSelectSubfolder(folder) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Icon(
                  imageVector = Icons.Rounded.Folder,
                  contentDescription = null,
                  tint = tokens.warningAccent,
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
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
    },
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
      verticalArrangement = Arrangement.spacedBy(PrismDimens.SpaceMedium),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          val title =
            if (progress.isRunning) {
              val verb =
                stringResource(
                  if (progress.type == TransferOperationType.COPY) R.string.transfer_copying
                  else R.string.transfer_moving
                )
              val base =
                stringResource(
                  R.string.transfer_progress_format,
                  verb,
                  progress.completedFiles,
                  progress.totalFiles,
                )
              if (progress.isWorkManagerBacked) {
                stringResource(R.string.transfer_progress_background_suffix, base)
              } else {
                base
              }
            } else {
              progress.summaryMessage ?: stringResource(R.string.transfer_complete)
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
            modifier =
              Modifier.defaultMinSize(PrismDimens.TouchTargetMin, PrismDimens.TouchTargetMin)
                .testTag("cancel_transfer_button"),
          ) {
            Text(stringResource(R.string.action_cancel))
          }
        } else {
          IconButton(
            onClick = onDismiss,
            modifier = Modifier.defaultMinSize(PrismDimens.TouchTargetMin, PrismDimens.TouchTargetMin),
          ) {
            Icon(
              imageVector = Icons.Rounded.Close,
              contentDescription = stringResource(R.string.transfer_dismiss_cd),
            )
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
  onTagsCollections: () -> Unit,
  onCopy: () -> Unit,
  onMove: () -> Unit,
  onShare: () -> Unit,
  onTrash: () -> Unit,
  onClear: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme
  GlassSurface(
    cornerRadius = PrismDimens.RecentCardCornerRadius,
    elevation = GlassElevation.FLOATING,
    accentTint = colorScheme.primary,
    modifier = modifier.testTag("multi_select_batch_bar"),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(PrismDimens.SpaceXSmall),
    ) {
      IconButton(
        onClick = onClear,
        modifier =
          Modifier.defaultMinSize(PrismDimens.TouchTargetMin, PrismDimens.TouchTargetMin)
            .testTag("batch_clear_selection"),
      ) {
        Icon(imageVector = Icons.Rounded.Close, contentDescription = stringResource(R.string.batch_clear_cd))
      }
      Text(
        text = "$selectedCount",
        style = MaterialTheme.typography.titleSmall,
        color = colorScheme.primary,
      )
      IconButton(
        onClick = onTagsCollections,
        modifier =
          Modifier.defaultMinSize(PrismDimens.TouchTargetMin, PrismDimens.TouchTargetMin)
            .testTag("batch_tags_button"),
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Rounded.Label,
          contentDescription = stringResource(R.string.batch_tags_cd),
          tint = colorScheme.primary,
        )
      }
      IconButton(
        onClick = onCopy,
        modifier =
          Modifier.defaultMinSize(PrismDimens.TouchTargetMin, PrismDimens.TouchTargetMin)
            .testTag("batch_copy_button"),
      ) {
        Icon(imageVector = Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.batch_copy_cd))
      }
      IconButton(
        onClick = onMove,
        modifier =
          Modifier.defaultMinSize(PrismDimens.TouchTargetMin, PrismDimens.TouchTargetMin)
            .testTag("batch_move_button"),
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Rounded.DriveFileMove,
          contentDescription = stringResource(R.string.batch_move_cd),
        )
      }
      IconButton(
        onClick = onShare,
        modifier =
          Modifier.defaultMinSize(PrismDimens.TouchTargetMin, PrismDimens.TouchTargetMin)
            .testTag("batch_share_button"),
      ) {
        Icon(imageVector = Icons.Rounded.Share, contentDescription = stringResource(R.string.batch_share_cd))
      }
      IconButton(
        onClick = onTrash,
        modifier =
          Modifier.defaultMinSize(PrismDimens.TouchTargetMin, PrismDimens.TouchTargetMin)
            .testTag("batch_trash_button"),
      ) {
        Icon(
          imageVector = Icons.Rounded.DeleteOutline,
          contentDescription = stringResource(R.string.batch_trash_cd),
          tint = colorScheme.error,
        )
      }
    }
  }
}

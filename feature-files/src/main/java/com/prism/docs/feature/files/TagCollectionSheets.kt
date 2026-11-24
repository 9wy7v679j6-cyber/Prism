package com.prism.docs.feature.files

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prism.docs.core.data.db.CollectionWithCountRow
import com.prism.docs.core.data.db.TagWithCountRow
import com.prism.docs.core.ui.glass.GlassBottomSheet
import com.prism.docs.core.ui.glass.GlassElevation
import com.prism.docs.core.ui.glass.GlassSheetDetent
import com.prism.docs.core.ui.glass.GlassSurface
import com.prism.docs.core.ui.glass.glassClickable
import com.prism.docs.core.ui.theme.PrismDimens

internal val PrismAccentHexPalette =
  listOf(
    "#3B5EF6", // Indigo Blue
    "#6C4AF6", // Violet Accent
    "#157A42", // Emerald
    "#D9631B", // Amber/Orange
    "#D9383A", // Coral Red
    "#B44CE0", // Orchid
    "#0E9AA7", // Teal
    "#54B4FF", // Glacier Blue
  )

internal fun parseHexColorOrDefault(hex: String?, fallback: Color): Color {
  if (hex.isNullOrBlank()) return fallback
  return runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(fallback)
}

internal enum class LabelKind {
  COLLECTION,
  TAG,
}

private sealed interface LabelEditorDialogState {
  data class Create(val kind: LabelKind) : LabelEditorDialogState

  data class RenameCollection(val collection: CollectionWithCountRow) : LabelEditorDialogState

  data class RenameTag(val tag: TagWithCountRow) : LabelEditorDialogState

  data class DeleteCollection(val collection: CollectionWithCountRow) : LabelEditorDialogState

  data class DeleteTag(val tag: TagWithCountRow) : LabelEditorDialogState
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AssignTagsCollectionsSheet(
  visible: Boolean,
  detent: GlassSheetDetent,
  onDetentChange: (GlassSheetDetent) -> Unit,
  onDismiss: () -> Unit,
  targetDocIds: List<Long>,
  collections: List<CollectionWithCountRow>,
  tags: List<TagWithCountRow>,
  collectionAssignedCounts: Map<Long, Int>,
  tagAssignedCounts: Map<Long, Int>,
  onToggleCollection: (collectionId: Long, assignAll: Boolean) -> Unit,
  onToggleTag: (tagId: Long, assignAll: Boolean) -> Unit,
  onCreateCollection: (name: String, accentHex: String) -> Unit,
  onCreateTag: (name: String, colorHex: String) -> Unit,
) {
  if (visible) {
    BackHandler(onBack = onDismiss)
  }

  val colorScheme = MaterialTheme.colorScheme
  val totalTargets = targetDocIds.size.coerceAtLeast(1)
  var createDialogKind by remember { mutableStateOf<LabelKind?>(null) }
  val selectedLabel = stringResource(R.string.state_selected)
  val notSelectedLabel = stringResource(R.string.state_not_selected)

  GlassBottomSheet(
    visible = visible,
    detent = detent,
    onDetentChange = onDetentChange,
    onDismiss = onDismiss,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = stringResource(R.string.assign_sheet_title),
          style = MaterialTheme.typography.titleLarge,
          color = colorScheme.onSurface,
        )
        Text(
          text = pluralStringResource(R.plurals.assign_sheet_target_count, totalTargets, totalTargets),
          style = MaterialTheme.typography.bodySmall,
          color = colorScheme.onSurfaceVariant,
        )
      }
      TextButton(
        onClick = onDismiss,
        modifier =
          Modifier.defaultMinSize(PrismDimens.TouchTargetMin, PrismDimens.TouchTargetMin)
            .testTag("assign_sheet_done_button"),
      ) {
        Text(stringResource(R.string.options_done))
      }
    }

    Spacer(Modifier.height(PrismDimens.SpaceMediumLarge))

    Column(
      modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(PrismDimens.SpaceSection),
    ) {
      // Collections section
      Column(verticalArrangement = Arrangement.spacedBy(PrismDimens.SpaceMedium)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = stringResource(R.string.collections_section_title),
            style = MaterialTheme.typography.titleSmall,
            color = colorScheme.onSurfaceVariant,
          )
          TextButton(
            onClick = { createDialogKind = LabelKind.COLLECTION },
            modifier =
              Modifier.defaultMinSize(PrismDimens.TouchTargetMin, PrismDimens.TouchTargetMin)
                .testTag("assign_new_collection_button"),
          ) {
            Text(stringResource(R.string.collection_new_button))
          }
        }

        if (collections.isEmpty()) {
          Text(
            text = stringResource(R.string.collections_empty_hint),
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onSurfaceVariant,
          )
        } else {
          collections.forEach { col ->
            val assignedCount = collectionAssignedCounts[col.id] ?: 0
            val isAllAssigned = assignedCount >= totalTargets
            val isPartial = assignedCount in 1 until totalTargets
            val accent = parseHexColorOrDefault(col.accentHex, colorScheme.primary)

            GlassSurface(
              cornerRadius = PrismDimens.FilterBarCornerRadius,
              elevation = GlassElevation.SUBTLE,
              useBackdropBlur = false,
              accentTint = if (isAllAssigned || isPartial) accent else null,
              onClick = { onToggleCollection(col.id, !isAllAssigned) },
              clickRole = Role.Checkbox,
              modifier =
                Modifier.fillMaxWidth()
                  .defaultMinSize(minHeight = PrismDimens.TouchTargetMin)
                  .semantics {
                    selected = isAllAssigned
                    stateDescription = if (isAllAssigned) selectedLabel else notSelectedLabel
                  }
                  .testTag("assign_collection_row_${col.id}"),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PrismDimens.SpaceStandard),
              ) {
                Box(
                  modifier = Modifier.size(12.dp).clip(CircleShape).background(accent)
                )
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = col.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  Text(
                    text =
                      pluralStringResource(
                        R.plurals.collection_doc_count,
                        col.documentCount,
                        col.documentCount,
                      ),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                  )
                }
                Box(
                  modifier =
                    Modifier.size(26.dp)
                      .clip(CircleShape)
                      .background(
                        if (isAllAssigned || isPartial) accent
                        else colorScheme.onSurface.copy(alpha = 0.10f)
                      ),
                  contentAlignment = Alignment.Center,
                ) {
                  if (isAllAssigned) {
                    Icon(
                      imageVector = Icons.Rounded.Check,
                      contentDescription = null,
                      tint = Color.White,
                      modifier = Modifier.size(16.dp),
                    )
                  } else if (isPartial) {
                    Icon(
                      imageVector = Icons.Rounded.Remove,
                      contentDescription = null,
                      tint = Color.White,
                      modifier = Modifier.size(16.dp),
                    )
                  } else {
                    Icon(
                      imageVector = Icons.Rounded.Add,
                      contentDescription = null,
                      tint = colorScheme.onSurfaceVariant,
                      modifier = Modifier.size(16.dp),
                    )
                  }
                }
              }
            }
          }
        }
      }

      // Tags section
      Column(verticalArrangement = Arrangement.spacedBy(PrismDimens.SpaceMedium)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = stringResource(R.string.tags_section_title),
            style = MaterialTheme.typography.titleSmall,
            color = colorScheme.onSurfaceVariant,
          )
          TextButton(
            onClick = { createDialogKind = LabelKind.TAG },
            modifier =
              Modifier.defaultMinSize(PrismDimens.TouchTargetMin, PrismDimens.TouchTargetMin)
                .testTag("assign_new_tag_button"),
          ) {
            Text(stringResource(R.string.tag_new_button))
          }
        }

        if (tags.isEmpty()) {
          Text(
            text = stringResource(R.string.tags_empty_hint),
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onSurfaceVariant,
          )
        } else {
          FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PrismDimens.SpaceMedium),
            verticalArrangement = Arrangement.spacedBy(PrismDimens.SpaceMedium),
          ) {
            tags.forEach { tag ->
              val assignedCount = tagAssignedCounts[tag.id] ?: 0
              val isAllAssigned = assignedCount >= totalTargets
              val isPartial = assignedCount in 1 until totalTargets
              FilterChip(
                selected = isAllAssigned || isPartial,
                onClick = { onToggleTag(tag.id, !isAllAssigned) },
                shape = RoundedCornerShape(16.dp),
                leadingIcon = {
                  Icon(
                    imageVector =
                      when {
                        isAllAssigned -> Icons.Rounded.Check
                        isPartial -> Icons.Rounded.Remove
                        else -> Icons.Rounded.Add
                      },
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                  )
                },
                label = { Text(stringResource(R.string.filter_part_tag, tag.name)) },
                modifier =
                  Modifier.defaultMinSize(minHeight = PrismDimens.TouchTargetMin)
                    .semantics {
                      stateDescription = if (isAllAssigned) selectedLabel else notSelectedLabel
                    }
                    .testTag("assign_tag_chip_${tag.id}"),
              )
            }
          }
        }
      }

      Spacer(Modifier.height(PrismDimens.SpaceLarge))
    }
  }

  createDialogKind?.let { kind ->
    NameAndColorInputDialog(
      title =
        stringResource(
          if (kind == LabelKind.COLLECTION) R.string.collection_create_title
          else R.string.tag_create_title
        ),
      fieldLabel =
        stringResource(
          if (kind == LabelKind.COLLECTION) R.string.collection_name_label
          else R.string.tag_name_label
        ),
      confirmLabel = stringResource(R.string.action_create),
      initialName = "",
      showColorPicker = true,
      onDismiss = { createDialogKind = null },
      onConfirm = { name, hex ->
        createDialogKind = null
        if (kind == LabelKind.COLLECTION) onCreateCollection(name, hex)
        else onCreateTag(name, hex)
      },
    )
  }
}

@Composable
internal fun ManageTagsCollectionsSheet(
  visible: Boolean,
  detent: GlassSheetDetent,
  onDetentChange: (GlassSheetDetent) -> Unit,
  onDismiss: () -> Unit,
  collections: List<CollectionWithCountRow>,
  tags: List<TagWithCountRow>,
  onOpenCollection: (Long) -> Unit,
  onCreateCollection: (name: String, accentHex: String) -> Unit,
  onRenameCollection: (collectionId: Long, newName: String) -> Unit,
  onDeleteCollection: (collectionId: Long) -> Unit,
  onCreateTag: (name: String, colorHex: String) -> Unit,
  onRenameTag: (tagId: Long, newName: String) -> Unit,
  onDeleteTag: (tagId: Long) -> Unit,
) {
  if (visible) {
    BackHandler(onBack = onDismiss)
  }

  val colorScheme = MaterialTheme.colorScheme
  var editorDialog by remember { mutableStateOf<LabelEditorDialogState?>(null) }

  GlassBottomSheet(
    visible = visible,
    detent = detent,
    onDetentChange = onDetentChange,
    onDismiss = onDismiss,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = stringResource(R.string.tags_collections_title),
        style = MaterialTheme.typography.titleLarge,
        color = colorScheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
      )
      TextButton(
        onClick = onDismiss,
        modifier =
          Modifier.defaultMinSize(PrismDimens.TouchTargetMin, PrismDimens.TouchTargetMin)
            .testTag("manage_labels_done_button"),
      ) {
        Text(stringResource(R.string.options_done))
      }
    }

    Spacer(Modifier.height(PrismDimens.SpaceMediumLarge))

    Column(
      modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(PrismDimens.SpaceSection),
    ) {
      // Collections management
      Column(verticalArrangement = Arrangement.spacedBy(PrismDimens.SpaceMedium)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = stringResource(R.string.collections_section_title),
            style = MaterialTheme.typography.titleSmall,
            color = colorScheme.onSurfaceVariant,
          )
          TextButton(
            onClick = { editorDialog = LabelEditorDialogState.Create(LabelKind.COLLECTION) },
            modifier =
              Modifier.defaultMinSize(PrismDimens.TouchTargetMin, PrismDimens.TouchTargetMin)
                .testTag("manage_new_collection_button"),
          ) {
            Text(stringResource(R.string.collection_new_button))
          }
        }

        if (collections.isEmpty()) {
          Text(
            text = stringResource(R.string.collections_empty_hint),
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onSurfaceVariant,
          )
        } else {
          collections.forEach { col ->
            val accent = parseHexColorOrDefault(col.accentHex, colorScheme.primary)
            GlassSurface(
              cornerRadius = PrismDimens.FilterBarCornerRadius,
              elevation = GlassElevation.SUBTLE,
              useBackdropBlur = false,
              onClick = {
                onDismiss()
                onOpenCollection(col.id)
              },
              clickRole = Role.Button,
              modifier =
                Modifier.fillMaxWidth()
                  .defaultMinSize(minHeight = PrismDimens.TouchTargetMin)
                  .testTag("manage_collection_row_${col.id}"),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PrismDimens.SpaceMedium),
              ) {
                Box(
                  modifier =
                    Modifier.size(36.dp)
                      .clip(RoundedCornerShape(10.dp))
                      .background(accent.copy(alpha = 0.16f)),
                  contentAlignment = Alignment.Center,
                ) {
                  Icon(
                    imageVector = Icons.Rounded.CollectionsBookmark,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp),
                  )
                }
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = col.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  Text(
                    text =
                      pluralStringResource(
                        R.plurals.collection_doc_count,
                        col.documentCount,
                        col.documentCount,
                      ),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                  )
                }
                IconButton(
                  onClick = { editorDialog = LabelEditorDialogState.RenameCollection(col) },
                  modifier =
                    Modifier.size(PrismDimens.TouchTargetMin)
                      .testTag("manage_rename_collection_${col.id}"),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.DriveFileRenameOutline,
                    contentDescription = stringResource(R.string.action_rename_cd, col.name),
                    tint = colorScheme.onSurfaceVariant,
                  )
                }
                IconButton(
                  onClick = { editorDialog = LabelEditorDialogState.DeleteCollection(col) },
                  modifier =
                    Modifier.size(PrismDimens.TouchTargetMin)
                      .testTag("manage_delete_collection_${col.id}"),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = stringResource(R.string.action_delete_cd, col.name),
                    tint = colorScheme.error,
                  )
                }
              }
            }
          }
        }
      }

      // Tags management
      Column(verticalArrangement = Arrangement.spacedBy(PrismDimens.SpaceMedium)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = stringResource(R.string.tags_section_title),
            style = MaterialTheme.typography.titleSmall,
            color = colorScheme.onSurfaceVariant,
          )
          TextButton(
            onClick = { editorDialog = LabelEditorDialogState.Create(LabelKind.TAG) },
            modifier =
              Modifier.defaultMinSize(PrismDimens.TouchTargetMin, PrismDimens.TouchTargetMin)
                .testTag("manage_new_tag_button"),
          ) {
            Text(stringResource(R.string.tag_new_button))
          }
        }

        if (tags.isEmpty()) {
          Text(
            text = stringResource(R.string.tags_empty_hint),
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onSurfaceVariant,
          )
        } else {
          tags.forEach { tag ->
            val accent = parseHexColorOrDefault(tag.colorHex, colorScheme.primary)
            GlassSurface(
              cornerRadius = PrismDimens.FilterBarCornerRadius,
              elevation = GlassElevation.SUBTLE,
              useBackdropBlur = false,
              modifier =
                Modifier.fillMaxWidth()
                  .defaultMinSize(minHeight = PrismDimens.TouchTargetMin)
                  .testTag("manage_tag_row_${tag.id}"),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PrismDimens.SpaceMedium),
              ) {
                Box(
                  modifier = Modifier.size(12.dp).clip(CircleShape).background(accent)
                )
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = stringResource(R.string.filter_part_tag, tag.name),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  Text(
                    text =
                      pluralStringResource(
                        R.plurals.collection_doc_count,
                        tag.documentCount,
                        tag.documentCount,
                      ),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                  )
                }
                IconButton(
                  onClick = { editorDialog = LabelEditorDialogState.RenameTag(tag) },
                  modifier =
                    Modifier.size(PrismDimens.TouchTargetMin).testTag("manage_rename_tag_${tag.id}"),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.DriveFileRenameOutline,
                    contentDescription = stringResource(R.string.action_rename_cd, tag.name),
                    tint = colorScheme.onSurfaceVariant,
                  )
                }
                IconButton(
                  onClick = { editorDialog = LabelEditorDialogState.DeleteTag(tag) },
                  modifier =
                    Modifier.size(PrismDimens.TouchTargetMin).testTag("manage_delete_tag_${tag.id}"),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = stringResource(R.string.action_delete_cd, tag.name),
                    tint = colorScheme.error,
                  )
                }
              }
            }
          }
        }
      }

      Spacer(Modifier.height(PrismDimens.SpaceLarge))
    }
  }

  when (val dlg = editorDialog) {
    is LabelEditorDialogState.Create -> {
      NameAndColorInputDialog(
        title =
          stringResource(
            if (dlg.kind == LabelKind.COLLECTION) R.string.collection_create_title
            else R.string.tag_create_title
          ),
        fieldLabel =
          stringResource(
            if (dlg.kind == LabelKind.COLLECTION) R.string.collection_name_label
            else R.string.tag_name_label
          ),
        confirmLabel = stringResource(R.string.action_create),
        initialName = "",
        showColorPicker = true,
        onDismiss = { editorDialog = null },
        onConfirm = { name, hex ->
          editorDialog = null
          if (dlg.kind == LabelKind.COLLECTION) onCreateCollection(name, hex)
          else onCreateTag(name, hex)
        },
      )
    }

    is LabelEditorDialogState.RenameCollection -> {
      NameAndColorInputDialog(
        title = stringResource(R.string.collection_rename_title),
        fieldLabel = stringResource(R.string.collection_name_label),
        confirmLabel = stringResource(R.string.action_save),
        initialName = dlg.collection.name,
        showColorPicker = false,
        onDismiss = { editorDialog = null },
        onConfirm = { newName, _ ->
          editorDialog = null
          onRenameCollection(dlg.collection.id, newName)
        },
      )
    }

    is LabelEditorDialogState.RenameTag -> {
      NameAndColorInputDialog(
        title = stringResource(R.string.tag_rename_title),
        fieldLabel = stringResource(R.string.tag_name_label),
        confirmLabel = stringResource(R.string.action_save),
        initialName = dlg.tag.name,
        showColorPicker = false,
        onDismiss = { editorDialog = null },
        onConfirm = { newName, _ ->
          editorDialog = null
          onRenameTag(dlg.tag.id, newName)
        },
      )
    }

    is LabelEditorDialogState.DeleteCollection -> {
      ConfirmDeleteEntityDialog(
        title = stringResource(R.string.confirm_delete_collection_title),
        body = stringResource(R.string.confirm_delete_collection_body, dlg.collection.name),
        onDismiss = { editorDialog = null },
        onConfirm = {
          editorDialog = null
          onDeleteCollection(dlg.collection.id)
        },
      )
    }

    is LabelEditorDialogState.DeleteTag -> {
      ConfirmDeleteEntityDialog(
        title = stringResource(R.string.confirm_delete_tag_title),
        body = stringResource(R.string.confirm_delete_tag_body, dlg.tag.name),
        onDismiss = { editorDialog = null },
        onConfirm = {
          editorDialog = null
          onDeleteTag(dlg.tag.id)
        },
      )
    }

    null -> Unit
  }
}

@Composable
internal fun NameAndColorInputDialog(
  title: String,
  fieldLabel: String,
  confirmLabel: String,
  initialName: String,
  showColorPicker: Boolean,
  onDismiss: () -> Unit,
  onConfirm: (name: String, hexColor: String) -> Unit,
) {
  var name by remember(initialName) { mutableStateOf(initialName) }
  var selectedHex by remember { mutableStateOf(PrismAccentHexPalette.first()) }
  val trimmed = name.trim()
  val colorScheme = MaterialTheme.colorScheme

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(text = title) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(PrismDimens.SpaceStandard)) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text(fieldLabel) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("label_name_input"),
        )
        if (showColorPicker) {
          FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PrismDimens.SpaceSmall),
            verticalArrangement = Arrangement.spacedBy(PrismDimens.SpaceSmall),
          ) {
            PrismAccentHexPalette.forEach { hex ->
              val swatchColor = parseHexColorOrDefault(hex, colorScheme.primary)
              val isSelected = selectedHex == hex
              Box(
                modifier =
                  Modifier.size(PrismDimens.TouchTargetMin)
                    .clip(CircleShape)
                    .clickable(role = Role.RadioButton) { selectedHex = hex },
                contentAlignment = Alignment.Center,
              ) {
                Box(
                  modifier =
                    Modifier.size(32.dp)
                      .clip(CircleShape)
                      .background(swatchColor)
                      .then(
                        if (isSelected) Modifier.border(2.5.dp, colorScheme.onSurface, CircleShape)
                        else Modifier
                      ),
                  contentAlignment = Alignment.Center,
                ) {
                  if (isSelected) {
                    Icon(
                      imageVector = Icons.Rounded.Check,
                      contentDescription = null,
                      tint = Color.White,
                      modifier = Modifier.size(16.dp),
                    )
                  }
                }
              }
            }
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { onConfirm(trimmed, selectedHex) },
        enabled = trimmed.isNotEmpty(),
        modifier = Modifier.testTag("label_dialog_confirm"),
      ) {
        Text(confirmLabel)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
    },
  )
}

@Composable
internal fun ConfirmDeleteEntityDialog(
  title: String,
  body: String,
  onDismiss: () -> Unit,
  onConfirm: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(text = title) },
    text = { Text(text = body) },
    confirmButton = {
      Button(
        onClick = onConfirm,
        modifier = Modifier.testTag("confirm_delete_entity_button"),
      ) {
        Text(stringResource(R.string.action_delete))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
    },
  )
}

package com.prism.docs.feature.files

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.prism.docs.core.data.db.RecycleBinEntryEntity
import com.prism.docs.core.ui.glass.GlassElevation
import com.prism.docs.core.ui.glass.GlassSurface
import com.prism.docs.core.ui.theme.LocalHazeState
import com.prism.docs.core.ui.theme.LocalPrismVisualTokens
import com.prism.docs.core.ui.theme.PrismColors
import com.prism.docs.core.ui.glass.rememberPrismHaptics
import com.prism.docs.core.ui.theme.PrismDimens
import dev.chrisbanes.haze.hazeSource
import kotlin.math.ceil

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecycleBinSection(
  entries: List<RecycleBinEntryEntity>,
  totalBytes: Long,
  onNavigateBack: () -> Unit,
  onRestoreEntry: (Long) -> Unit,
  onDeleteEntryPermanently: (Long) -> Unit,
  onRestoreAll: () -> Unit,
  onEmptyRecycleBin: () -> Unit,
  modifier: Modifier = Modifier,
  topContentPadding: Dp = 148.dp,
  listState: LazyListState = rememberLazyListState(),
) {
  val hazeState = LocalHazeState.current
  val colorScheme = MaterialTheme.colorScheme
  val tokens = LocalPrismVisualTokens.current
  val haptics = rememberPrismHaptics()
  val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
  var confirmEmpty by remember { mutableStateOf(false) }
  var pendingPermanentDelete by remember { mutableStateOf<RecycleBinEntryEntity?>(null) }
  val formattedTotalSize = rememberFormattedByteSize(totalBytes)

  BackHandler { onNavigateBack() }

  LazyColumn(
    state = listState,
    contentPadding =
      PaddingValues(
        top = topContentPadding,
        bottom = navBarBottom + PrismDimens.ListBottomContentPadding,
        start = 16.dp,
        end = 16.dp,
      ),
    verticalArrangement = Arrangement.spacedBy(PrismDimens.SpaceStandard),
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier =
      modifier
        .fillMaxSize()
        .then(if (hazeState != null) Modifier.hazeSource(hazeState, zIndex = 1f) else Modifier)
        .testTag("recycle_bin_list"),
  ) {
    item {
      GlassSurface(
        cornerRadius = 22.dp,
        elevation = GlassElevation.SUBTLE,
        accentTint = tokens.destructiveAccent,
        modifier = Modifier.widthIn(max = PrismDimens.ContentMaxWidth).fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(PrismDimens.SpaceSection),
          verticalArrangement = Arrangement.spacedBy(PrismDimens.SpaceMediumLarge),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PrismDimens.SpaceMedium),
          ) {
            IconButton(
              onClick = {
                haptics.tap()
                onNavigateBack()
              },
              modifier =
                Modifier.defaultMinSize(PrismDimens.TouchTargetMin, PrismDimens.TouchTargetMin)
                  .testTag("recycle_bin_back_button"),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.recycle_back_cd),
              )
            }
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = stringResource(R.string.recycle_title),
                style = MaterialTheme.typography.titleMedium,
                color = colorScheme.onSurface,
              )
              Text(
                text = pluralStringResource(R.plurals.recycle_items_summary, entries.size, entries.size, formattedTotalSize),
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
              )
            }
          }

          Text(
            text = stringResource(R.string.recycle_explanation),
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onSurfaceVariant,
          )

          if (entries.isNotEmpty()) {
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(PrismDimens.SpaceMediumLarge),
              verticalArrangement = Arrangement.spacedBy(PrismDimens.SpaceSmall),
            ) {
              OutlinedButton(
                onClick = {
                  haptics.confirm()
                  onRestoreAll()
                },
                modifier =
                  Modifier.defaultMinSize(minHeight = PrismDimens.TouchTargetMin)
                    .testTag("recycle_bin_restore_all"),
              ) {
                Text(stringResource(R.string.recycle_restore_all))
              }
              Button(
                onClick = {
                  haptics.reject()
                  confirmEmpty = true
                },
                colors =
                  ButtonDefaults.buttonColors(
                    containerColor = PrismColors.CoralPdf,
                    contentColor = PrismColors.OnAccent,
                  ),
                modifier =
                  Modifier.defaultMinSize(minHeight = PrismDimens.TouchTargetMin)
                    .testTag("recycle_bin_empty_button"),
              ) {
                Text(stringResource(R.string.recycle_empty_bin))
              }
            }
          }
        }
      }
    }

    if (entries.isEmpty()) {
      item {
        GlassSurface(
          cornerRadius = 22.dp,
          elevation = GlassElevation.SUBTLE,
          modifier = Modifier.widthIn(max = PrismDimens.ContentMaxWidth).fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(28.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PrismDimens.SpaceMedium),
          ) {
            Icon(
              imageVector = Icons.Rounded.DeleteOutline,
              contentDescription = null,
              tint = colorScheme.onSurfaceVariant,
              modifier = Modifier.size(34.dp),
            )
            Text(
              text = stringResource(R.string.recycle_empty_title),
              style = MaterialTheme.typography.titleSmall,
              color = colorScheme.onSurface,
            )
            Text(
              text = stringResource(R.string.recycle_empty_body),
              style = MaterialTheme.typography.bodySmall,
              color = colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    } else {
      items(entries, key = { it.documentId }) { entry ->
        RecycleBinItemRow(
          entry = entry,
          onRestore = { onRestoreEntry(entry.documentId) },
          onDeleteForever = { pendingPermanentDelete = entry },
          modifier = Modifier.widthIn(max = PrismDimens.ContentMaxWidth).fillMaxWidth(),
        )
      }
    }
  }

  if (confirmEmpty) {
    AlertDialog(
      onDismissRequest = { confirmEmpty = false },
      title = { Text(stringResource(R.string.recycle_confirm_empty_title)) },
      text = {
        Text(
          pluralStringResource(R.plurals.recycle_confirm_empty_body, entries.size, entries.size, formattedTotalSize)
        )
      },
      confirmButton = {
        Button(
          onClick = {
            haptics.reject()
            confirmEmpty = false
            onEmptyRecycleBin()
          },
          colors =
            ButtonDefaults.buttonColors(
              containerColor = PrismColors.CoralPdf,
              contentColor = PrismColors.OnAccent,
            ),
          modifier = Modifier.testTag("confirm_empty_recycle_bin"),
        ) {
          Text(stringResource(R.string.recycle_delete_forever))
        }
      },
      dismissButton = {
        TextButton(onClick = { confirmEmpty = false }) { Text(stringResource(R.string.action_cancel)) }
      },
    )
  }

  pendingPermanentDelete?.let { target ->
    AlertDialog(
      onDismissRequest = { pendingPermanentDelete = null },
      title = { Text(stringResource(R.string.recycle_confirm_delete_one_title)) },
      text = { Text(stringResource(R.string.recycle_confirm_delete_one_body, target.displayName)) },
      confirmButton = {
        Button(
          onClick = {
            haptics.reject()
            val id = target.documentId
            pendingPermanentDelete = null
            onDeleteEntryPermanently(id)
          },
          colors =
            ButtonDefaults.buttonColors(
              containerColor = PrismColors.CoralPdf,
              contentColor = PrismColors.OnAccent,
            ),
        ) {
          Text(stringResource(R.string.recycle_delete_forever))
        }
      },
      dismissButton = {
        TextButton(onClick = { pendingPermanentDelete = null }) { Text(stringResource(R.string.action_cancel)) }
      },
    )
  }
}

@Composable
private fun RecycleBinItemRow(
  entry: RecycleBinEntryEntity,
  onRestore: () -> Unit,
  onDeleteForever: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme
  val tokens = LocalPrismVisualTokens.current
  val remainingMs = (entry.expiresAtEpochMs - System.currentTimeMillis()).coerceAtLeast(0L)
  val daysLeft = ceil(remainingMs / (24.0 * 60.0 * 60.0 * 1000.0)).toInt().coerceAtLeast(0)
  val formattedSize = rememberFormattedByteSize(entry.sizeBytes)

  GlassSurface(
    cornerRadius = PrismDimens.StickyHeaderCornerRadius,
    elevation = GlassElevation.SUBTLE,
    modifier = modifier.testTag("trash_row_${entry.documentId}"),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(PrismDimens.SpaceMediumLarge),
    ) {
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(PrismDimens.SpaceMedium),
        ) {
          Text(
            text = entry.displayName,
            style = MaterialTheme.typography.titleSmall,
            color = colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
          )
          Box(
            modifier =
              Modifier.clip(RoundedCornerShape(8.dp))
                .background(tokens.warningAccent.copy(alpha = 0.16f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
          ) {
            Text(
              text = pluralStringResource(R.plurals.recycle_days_left, daysLeft, daysLeft),
              style = MaterialTheme.typography.labelSmall,
              color = tokens.warningAccent,
            )
          }
        }
        Text(
          text = "$formattedSize • ${entry.originalPath}",
          style = MaterialTheme.typography.bodySmall,
          color = colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      val itemHaptics = rememberPrismHaptics()
      IconButton(
        onClick = {
          itemHaptics.confirm()
          onRestore()
        },
        modifier =
          Modifier.defaultMinSize(PrismDimens.TouchTargetMin, PrismDimens.TouchTargetMin)
            .testTag("trash_restore_${entry.documentId}"),
      ) {
        Icon(
          imageVector = Icons.Rounded.RestoreFromTrash,
          contentDescription = stringResource(R.string.recycle_restore_cd, entry.displayName),
          tint = tokens.successAccent,
        )
      }
      IconButton(
        onClick = {
          itemHaptics.reject()
          onDeleteForever()
        },
        modifier =
          Modifier.defaultMinSize(PrismDimens.TouchTargetMin, PrismDimens.TouchTargetMin)
            .testTag("trash_delete_${entry.documentId}"),
      ) {
        Icon(
          imageVector = Icons.Rounded.DeleteForever,
          contentDescription = stringResource(R.string.recycle_delete_forever_cd, entry.displayName),
          tint = tokens.destructiveAccent,
        )
      }
    }
  }
}

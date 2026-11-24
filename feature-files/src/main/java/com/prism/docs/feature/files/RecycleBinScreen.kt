package com.prism.docs.feature.files

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prism.docs.core.data.db.RecycleBinEntryEntity
import com.prism.docs.core.ui.glass.GlassElevation
import com.prism.docs.core.ui.glass.GlassSurface
import com.prism.docs.core.ui.theme.LocalHazeState
import com.prism.docs.core.ui.theme.PrismColors
import dev.chrisbanes.haze.hazeSource
import kotlin.math.ceil

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
  listState: LazyListState = rememberLazyListState(),
) {
  val hazeState = LocalHazeState.current
  val colorScheme = MaterialTheme.colorScheme
  var confirmEmpty by remember { mutableStateOf(false) }
  var pendingPermanentDelete by remember { mutableStateOf<RecycleBinEntryEntity?>(null) }

  BackHandler { onNavigateBack() }

  LazyColumn(
    state = listState,
    contentPadding = PaddingValues(top = 146.dp, bottom = 128.dp, start = 16.dp, end = 16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier =
      modifier
        .fillMaxSize()
        .statusBarsPadding()
        .then(if (hazeState != null) Modifier.hazeSource(hazeState, zIndex = 1f) else Modifier)
        .testTag("recycle_bin_list"),
  ) {
    item {
      GlassSurface(
        cornerRadius = 22.dp,
        elevation = GlassElevation.SUBTLE,
        accentTint = PrismColors.CoralPdf,
        modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            IconButton(
              onClick = onNavigateBack,
              modifier = Modifier.testTag("recycle_bin_back_button"),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back to Library",
              )
            }
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Recycle Bin (30-Day)",
                style = MaterialTheme.typography.titleMedium,
                color = colorScheme.onSurface,
              )
              Text(
                text = "${entries.size} item${if (entries.size == 1) "" else "s"} • ${formatByteSize(totalBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
              )
            }
          }

          Text(
            text =
              "Local files stay in a hidden .PrismTrash folder on the same volume so delete and restore are instant renames. SAF files are staged in app-private trash when supported. Items purge automatically after 30 days.",
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onSurfaceVariant,
          )

          if (entries.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              OutlinedButton(
                onClick = onRestoreAll,
                modifier = Modifier.testTag("recycle_bin_restore_all"),
              ) {
                Text("Restore All")
              }
              Button(
                onClick = { confirmEmpty = true },
                colors = ButtonDefaults.buttonColors(containerColor = PrismColors.CoralPdf),
                modifier = Modifier.testTag("recycle_bin_empty_button"),
              ) {
                Text("Empty Bin")
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
          modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
        ) {
          Column(
            modifier = Modifier.padding(28.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.DeleteOutline,
              contentDescription = null,
              tint = colorScheme.onSurfaceVariant,
              modifier = Modifier.size(34.dp),
            )
            Text(
              text = "Recycle Bin is empty",
              style = MaterialTheme.typography.titleSmall,
              color = colorScheme.onSurface,
            )
            Text(
              text = "Deleted documents appear here for 30 days before automatic purge.",
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
          modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
        )
      }
    }
  }

  if (confirmEmpty) {
    AlertDialog(
      onDismissRequest = { confirmEmpty = false },
      title = { Text("Empty Recycle Bin?") },
      text = {
        Text(
          "Permanently delete all ${entries.size} item(s) (${formatByteSize(totalBytes)})? This cannot be undone."
        )
      },
      confirmButton = {
        Button(
          onClick = {
            confirmEmpty = false
            onEmptyRecycleBin()
          },
          colors = ButtonDefaults.buttonColors(containerColor = PrismColors.CoralPdf),
          modifier = Modifier.testTag("confirm_empty_recycle_bin"),
        ) {
          Text("Delete Forever")
        }
      },
      dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text("Cancel") } },
    )
  }

  pendingPermanentDelete?.let { target ->
    AlertDialog(
      onDismissRequest = { pendingPermanentDelete = null },
      title = { Text("Delete permanently?") },
      text = { Text("Remove \"${target.displayName}\" permanently from disk? This cannot be undone.") },
      confirmButton = {
        Button(
          onClick = {
            val id = target.documentId
            pendingPermanentDelete = null
            onDeleteEntryPermanently(id)
          },
          colors = ButtonDefaults.buttonColors(containerColor = PrismColors.CoralPdf),
        ) {
          Text("Delete Forever")
        }
      },
      dismissButton = { TextButton(onClick = { pendingPermanentDelete = null }) { Text("Cancel") } },
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
  val remainingMs = (entry.expiresAtEpochMs - System.currentTimeMillis()).coerceAtLeast(0L)
  val daysLeft = ceil(remainingMs / (24.0 * 60.0 * 60.0 * 1000.0)).toInt().coerceAtLeast(0)

  GlassSurface(
    cornerRadius = 18.dp,
    elevation = GlassElevation.SUBTLE,
    modifier = modifier.testTag("trash_row_${entry.documentId}"),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                .background(PrismColors.AmberSlide.copy(alpha = 0.16f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
          ) {
            Text(
              text = "${daysLeft}d left",
              style = MaterialTheme.typography.labelSmall,
              color = PrismColors.AmberSlide,
            )
          }
        }
        Text(
          text = "${formatByteSize(entry.sizeBytes)} • ${entry.originalPath}",
          style = MaterialTheme.typography.bodySmall,
          color = colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      IconButton(
        onClick = onRestore,
        modifier = Modifier.testTag("trash_restore_${entry.documentId}"),
      ) {
        Icon(
          imageVector = Icons.Rounded.RestoreFromTrash,
          contentDescription = "Restore ${entry.displayName}",
          tint = PrismColors.EmeraldSheet,
        )
      }
      IconButton(
        onClick = onDeleteForever,
        modifier = Modifier.testTag("trash_delete_${entry.documentId}"),
      ) {
        Icon(
          imageVector = Icons.Rounded.DeleteForever,
          contentDescription = "Delete ${entry.displayName} permanently",
          tint = PrismColors.CoralPdf,
        )
      }
    }
  }
}

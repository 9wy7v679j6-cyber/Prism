package com.prism.docs.feature.files

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.prism.docs.core.data.model.FileSortField
import com.prism.docs.core.ui.glass.GlassBottomSheet
import com.prism.docs.core.ui.glass.GlassElevation
import com.prism.docs.core.ui.glass.GlassSegmentedControl
import com.prism.docs.core.ui.glass.GlassSheetDetent
import com.prism.docs.core.ui.glass.GlassSurface
import com.prism.docs.core.ui.theme.LocalGlassEnvironment

/**
 * Glass options & filters sheet reachable from the top-right round sliders button on Library.
 * Keeps the main Library canvas ultra-clean while keeping name filter, sort, layout, favorites,
 * collections/tags, Glass settings, sync, and debug-only sample file generation one tap away.
 */
@Composable
internal fun LibraryOptionsGlassSheet(
  visible: Boolean,
  detent: GlassSheetDetent,
  onDetentChange: (GlassSheetDetent) -> Unit,
  onDismiss: () -> Unit,
  uiState: FilesUiState,
  isDebugBuild: Boolean,
  hasActiveSecondaryFilters: Boolean,
  onNamePrefixChange: (String) -> Unit,
  onSortFieldSelected: (FileSortField) -> Unit,
  onToggleSortDirection: () -> Unit,
  onSetGridMode: (Boolean) -> Unit,
  onToggleFavoritesOnly: () -> Unit,
  onToggleRecentsOnly: () -> Unit,
  onCollectionSelected: (Long?) -> Unit,
  onTagSelected: (Long?) -> Unit,
  onClearFilters: () -> Unit,
  onOpenGlassSettings: () -> Unit,
  onTriggerSync: () -> Unit,
  onAddSafFolder: () -> Unit,
  onClearThumbnailCache: () -> Unit,
  onCreateDebugSampleFiles: () -> Unit,
) {
  if (visible) {
    BackHandler(onBack = onDismiss)
  }

  val env = LocalGlassEnvironment.current
  val colorScheme = MaterialTheme.colorScheme

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
      Text(
        text = "Library Filters & Options",
        style = MaterialTheme.typography.titleLarge,
        color = colorScheme.onSurface,
      )
      TextButton(
        onClick = onDismiss,
        modifier = Modifier.testTag("close_library_options_button"),
      ) {
        Text("Done")
      }
    }

    Spacer(Modifier.height(10.dp))

    Column(
      modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      // Name filter (explicitly reachable from the sliders button)
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          text = "Filter by Name",
          style = MaterialTheme.typography.titleSmall,
          color = colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
          value = uiState.query.namePrefix.orEmpty(),
          onValueChange = onNamePrefixChange,
          placeholder = { Text("Filter files by name…") },
          leadingIcon = { Icon(imageVector = Icons.Rounded.Search, contentDescription = null) },
          trailingIcon = {
            if (!uiState.query.namePrefix.isNullOrBlank()) {
              IconButton(onClick = { onNamePrefixChange("") }) {
                Icon(imageVector = Icons.Rounded.Close, contentDescription = "Clear filter text")
              }
            }
          },
          singleLine = true,
          shape = RoundedCornerShape(20.dp),
          modifier = Modifier.fillMaxWidth().testTag("library_name_filter_input"),
        )
      }

      // View mode switcher
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          text = "Layout",
          style = MaterialTheme.typography.titleSmall,
          color = colorScheme.onSurfaceVariant,
        )
        GlassSegmentedControl(
          options = listOf("List", "Grid"),
          selectedIndex = if (uiState.isGridMode) 1 else 0,
          onSelect = { idx -> onSetGridMode(idx == 1) },
          modifier = Modifier.fillMaxWidth().testTag("toggle_layout_segmented"),
        )
      }

      // Sort options
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          text = "Sort By",
          style = MaterialTheme.typography.titleSmall,
          color = colorScheme.onSurfaceVariant,
        )
        SortControlRow(
          sortField = uiState.query.sortField,
          sortDirection = uiState.query.sortDirection,
          onSortFieldSelected = onSortFieldSelected,
          onToggleSortDirection = onToggleSortDirection,
          modifier = Modifier.fillMaxWidth(),
        )
      }

      // Quick filters (Favorites, Recents, Collections, Tags)
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Quick Filters",
            style = MaterialTheme.typography.titleSmall,
            color = colorScheme.onSurfaceVariant,
          )
          if (hasActiveSecondaryFilters || uiState.query.category != null) {
            TextButton(
              onClick = onClearFilters,
              modifier = Modifier.testTag("clear_all_filters_button"),
            ) {
              Text("Reset all")
            }
          }
        }
        LazyRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          contentPadding = PaddingValues(end = 12.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          item {
            FilterChip(
              selected = uiState.query.favoritesOnly,
              onClick = onToggleFavoritesOnly,
              shape = RoundedCornerShape(16.dp),
              label = { Text("Favorites (${uiState.favoritesCount})") },
              leadingIcon = {
                Icon(Icons.Rounded.Star, contentDescription = null, modifier = Modifier.size(16.dp))
              },
              modifier = Modifier.testTag("filter_favorites_chip"),
            )
          }
          item {
            FilterChip(
              selected = uiState.query.recentsOnly,
              onClick = onToggleRecentsOnly,
              shape = RoundedCornerShape(16.dp),
              label = { Text("Recents (${uiState.recentsCount})") },
              leadingIcon = {
                Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(16.dp))
              },
              modifier = Modifier.testTag("filter_recents_chip"),
            )
          }
          items(uiState.collections, key = { "opt_col_${it.id}" }) { col ->
            FilterChip(
              selected = uiState.query.collectionId == col.id,
              onClick = { onCollectionSelected(col.id) },
              shape = RoundedCornerShape(16.dp),
              label = { Text(col.name) },
            )
          }
          items(uiState.tags, key = { "opt_tag_${it.id}" }) { tag ->
            FilterChip(
              selected = uiState.query.tagId == tag.id,
              onClick = { onTagSelected(tag.id) },
              shape = RoundedCornerShape(16.dp),
              label = { Text("#${tag.name}") },
            )
          }
        }
      }

      // Library & Appearance Actions
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = "Settings & Tools",
          style = MaterialTheme.typography.titleSmall,
          color = colorScheme.onSurfaceVariant,
        )
        OptionsActionRow(
          icon = Icons.Rounded.Palette,
          title = "Appearance & Liquid Glass",
          subtitle = "Active: ${env.capabilityTier.label}",
          testTag = "top_glass_settings_button",
          onClick = {
            onDismiss()
            onOpenGlassSettings()
          },
        )
        OptionsActionRow(
          icon = Icons.Rounded.Sync,
          title = "Rescan device documents",
          subtitle = "Refresh Room index across storage & SAF",
          testTag = "top_sync_index_button",
          onClick = {
            onDismiss()
            onTriggerSync()
          },
        )
        OptionsActionRow(
          icon = Icons.Rounded.CreateNewFolder,
          title = "Link SAF folder",
          subtitle = "Grant persistent access to a folder via Android picker",
          testTag = "options_link_saf_button",
          onClick = {
            onDismiss()
            onAddSafFolder()
          },
        )
        OptionsActionRow(
          icon = Icons.Rounded.CleaningServices,
          title = "Clear thumbnail cache",
          subtitle = "Free cached memory and disk thumbnails",
          testTag = "options_clear_thumb_cache_button",
          onClick = {
            onDismiss()
            onClearThumbnailCache()
          },
        )
        if (isDebugBuild) {
          OptionsActionRow(
            icon = Icons.Rounded.Science,
            title = "Create sample files (Debug)",
            subtitle = "Write PDF, DOCX, XLSX, PPTX & JPG to Documents/PrismSamples",
            testTag = "debug_create_sample_files_button",
            onClick = {
              onDismiss()
              onCreateDebugSampleFiles()
            },
          )
        }
      }

      Spacer(Modifier.height(12.dp))
    }
  }
}

@Composable
private fun OptionsActionRow(
  icon: ImageVector,
  title: String,
  subtitle: String,
  testTag: String,
  onClick: () -> Unit,
) {
  val colorScheme = MaterialTheme.colorScheme
  GlassSurface(
    cornerRadius = 18.dp,
    elevation = GlassElevation.SUBTLE,
    enableRefraction = false,
    onClick = onClick,
    modifier = Modifier.fillMaxWidth().testTag(testTag),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = colorScheme.primary,
        modifier = Modifier.size(22.dp),
      )
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleSmall,
          color = colorScheme.onSurface,
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

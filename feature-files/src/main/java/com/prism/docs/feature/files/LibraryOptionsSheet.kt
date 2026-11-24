package com.prism.docs.feature.files

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material.icons.rounded.FileCopy
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.prism.docs.core.data.model.FileSortField
import com.prism.docs.core.ui.glass.GlassBottomSheet
import com.prism.docs.core.ui.glass.GlassElevation
import com.prism.docs.core.ui.glass.GlassSegmentedControl
import com.prism.docs.core.ui.glass.GlassSheetDetent
import com.prism.docs.core.ui.glass.GlassSurface
import com.prism.docs.core.ui.theme.LocalGlassEnvironment
import com.prism.docs.core.ui.theme.PrismDimens

/**
 * Glass options & filters sheet reachable from the top-right round sliders button on Library.
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
  onOpenDuplicateFinder: () -> Unit,
  onOpenStorageAnalyzer: () -> Unit,
  onOpenManageTagsCollections: () -> Unit,
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
  val selectedStateLabel = stringResource(R.string.state_selected)
  val notSelectedStateLabel = stringResource(R.string.state_not_selected)

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
        text = stringResource(R.string.options_title),
        style = MaterialTheme.typography.titleLarge,
        color = colorScheme.onSurface,
        maxLines = 2,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
      )
      TextButton(
        onClick = onDismiss,
        modifier =
          Modifier.defaultMinSize(minWidth = PrismDimens.TouchTargetMin, minHeight = PrismDimens.TouchTargetMin)
            .testTag("close_library_options_button"),
      ) {
        Text(stringResource(R.string.options_done))
      }
    }

    Spacer(Modifier.height(PrismDimens.SpaceMediumLarge))

    Column(
      modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(PrismDimens.SpaceSection),
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(PrismDimens.SpaceSmallMedium)) {
        Text(
          text = stringResource(R.string.options_filter_name_title),
          style = MaterialTheme.typography.titleSmall,
          color = colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
          value = uiState.query.namePrefix.orEmpty(),
          onValueChange = onNamePrefixChange,
          placeholder = { Text(stringResource(R.string.options_filter_name_placeholder)) },
          leadingIcon = { Icon(imageVector = Icons.Rounded.Search, contentDescription = null) },
          trailingIcon = {
            if (!uiState.query.namePrefix.isNullOrBlank()) {
              IconButton(
                onClick = { onNamePrefixChange("") },
                modifier = Modifier.defaultMinSize(PrismDimens.TouchTargetMin, PrismDimens.TouchTargetMin),
              ) {
                Icon(
                  imageVector = Icons.Rounded.Close,
                  contentDescription = stringResource(R.string.options_clear_filter_cd),
                )
              }
            }
          },
          singleLine = true,
          shape = RoundedCornerShape(20.dp),
          modifier = Modifier.fillMaxWidth().testTag("library_name_filter_input"),
        )
      }

      Column(verticalArrangement = Arrangement.spacedBy(PrismDimens.SpaceSmallMedium)) {
        Text(
          text = stringResource(R.string.options_layout_title),
          style = MaterialTheme.typography.titleSmall,
          color = colorScheme.onSurfaceVariant,
        )
        GlassSegmentedControl(
          options =
            listOf(
              stringResource(R.string.options_layout_list),
              stringResource(R.string.options_layout_grid),
            ),
          selectedIndex = if (uiState.isGridMode) 1 else 0,
          onSelect = { idx -> onSetGridMode(idx == 1) },
          modifier = Modifier.fillMaxWidth().testTag("toggle_layout_segmented"),
        )
      }

      Column(verticalArrangement = Arrangement.spacedBy(PrismDimens.SpaceSmallMedium)) {
        Text(
          text = stringResource(R.string.options_sort_by_title),
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

      Column(verticalArrangement = Arrangement.spacedBy(PrismDimens.SpaceSmallMedium)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = stringResource(R.string.options_quick_filters_title),
            style = MaterialTheme.typography.titleSmall,
            color = colorScheme.onSurfaceVariant,
          )
          if (hasActiveSecondaryFilters || uiState.query.category != null) {
            TextButton(
              onClick = onClearFilters,
              modifier =
                Modifier.defaultMinSize(minWidth = PrismDimens.TouchTargetMin, minHeight = PrismDimens.TouchTargetMin)
                  .testTag("clear_all_filters_button"),
            ) {
              Text(stringResource(R.string.options_reset_all))
            }
          }
        }
        LazyRow(
          horizontalArrangement = Arrangement.spacedBy(PrismDimens.SpaceMedium),
          contentPadding = PaddingValues(end = PrismDimens.SpaceStandard),
          modifier = Modifier.fillMaxWidth(),
        ) {
          item {
            FilterChip(
              selected = uiState.query.favoritesOnly,
              onClick = onToggleFavoritesOnly,
              shape = RoundedCornerShape(16.dp),
              label = { Text(stringResource(R.string.options_favorites_chip, uiState.favoritesCount)) },
              leadingIcon = {
                Icon(Icons.Rounded.Star, contentDescription = null, modifier = Modifier.size(16.dp))
              },
              modifier =
                Modifier.defaultMinSize(minHeight = PrismDimens.TouchTargetMin)
                  .semantics {
                    stateDescription =
                      if (uiState.query.favoritesOnly) selectedStateLabel else notSelectedStateLabel
                  }
                  .testTag("filter_favorites_chip"),
            )
          }
          item {
            FilterChip(
              selected = uiState.query.recentsOnly,
              onClick = onToggleRecentsOnly,
              shape = RoundedCornerShape(16.dp),
              label = { Text(stringResource(R.string.options_recents_chip, uiState.recentsCount)) },
              leadingIcon = {
                Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(16.dp))
              },
              modifier =
                Modifier.defaultMinSize(minHeight = PrismDimens.TouchTargetMin)
                  .semantics {
                    stateDescription =
                      if (uiState.query.recentsOnly) selectedStateLabel else notSelectedStateLabel
                  }
                  .testTag("filter_recents_chip"),
            )
          }
          items(uiState.collections, key = { "opt_col_${it.id}" }) { col ->
            val isColSelected = uiState.query.collectionId == col.id
            FilterChip(
              selected = isColSelected,
              onClick = { onCollectionSelected(col.id) },
              shape = RoundedCornerShape(16.dp),
              label = { Text(col.name) },
              modifier =
                Modifier.defaultMinSize(minHeight = PrismDimens.TouchTargetMin).semantics {
                  stateDescription = if (isColSelected) selectedStateLabel else notSelectedStateLabel
                },
            )
          }
          items(uiState.tags, key = { "opt_tag_${it.id}" }) { tag ->
            val isTagSelected = uiState.query.tagId == tag.id
            FilterChip(
              selected = isTagSelected,
              onClick = { onTagSelected(tag.id) },
              shape = RoundedCornerShape(16.dp),
              label = { Text(stringResource(R.string.filter_part_tag, tag.name)) },
              modifier =
                Modifier.defaultMinSize(minHeight = PrismDimens.TouchTargetMin).semantics {
                  stateDescription = if (isTagSelected) selectedStateLabel else notSelectedStateLabel
                },
            )
          }
        }
      }

      Column(verticalArrangement = Arrangement.spacedBy(PrismDimens.SpaceMedium)) {
        Text(
          text = stringResource(R.string.options_settings_tools_title),
          style = MaterialTheme.typography.titleSmall,
          color = colorScheme.onSurfaceVariant,
        )
        OptionsActionRow(
          icon = Icons.Rounded.FileCopy,
          title = stringResource(R.string.tool_duplicate_finder_title),
          subtitle = stringResource(R.string.tool_duplicate_finder_subtitle),
          testTag = "options_duplicate_finder_button",
          onClick = {
            onDismiss()
            onOpenDuplicateFinder()
          },
        )
        OptionsActionRow(
          icon = Icons.Rounded.DonutLarge,
          title = stringResource(R.string.tool_storage_analyzer_title),
          subtitle = stringResource(R.string.tool_storage_analyzer_subtitle),
          testTag = "options_storage_analyzer_button",
          onClick = {
            onDismiss()
            onOpenStorageAnalyzer()
          },
        )
        OptionsActionRow(
          icon = Icons.AutoMirrored.Rounded.Label,
          title = stringResource(R.string.tool_tags_collections_title),
          subtitle = stringResource(R.string.tool_tags_collections_subtitle),
          testTag = "options_tags_collections_button",
          onClick = {
            onDismiss()
            onOpenManageTagsCollections()
          },
        )
        OptionsActionRow(
          icon = Icons.Rounded.Palette,
          title = stringResource(R.string.options_appearance_title),
          subtitle = stringResource(R.string.options_appearance_subtitle, env.capabilityTier.label),
          testTag = "top_glass_settings_button",
          onClick = {
            onDismiss()
            onOpenGlassSettings()
          },
        )
        OptionsActionRow(
          icon = Icons.Rounded.Sync,
          title = stringResource(R.string.options_rescan_title),
          subtitle = stringResource(R.string.options_rescan_subtitle),
          testTag = "top_sync_index_button",
          onClick = {
            onDismiss()
            onTriggerSync()
          },
        )
        OptionsActionRow(
          icon = Icons.Rounded.CreateNewFolder,
          title = stringResource(R.string.options_link_saf_title),
          subtitle = stringResource(R.string.options_link_saf_subtitle),
          testTag = "options_link_saf_button",
          onClick = {
            onDismiss()
            onAddSafFolder()
          },
        )
        OptionsActionRow(
          icon = Icons.Rounded.CleaningServices,
          title = stringResource(R.string.options_clear_cache_title),
          subtitle = stringResource(R.string.options_clear_cache_subtitle),
          testTag = "options_clear_thumb_cache_button",
          onClick = {
            onDismiss()
            onClearThumbnailCache()
          },
        )
        if (isDebugBuild) {
          OptionsActionRow(
            icon = Icons.Rounded.Science,
            title = stringResource(R.string.options_debug_samples_title),
            subtitle = stringResource(R.string.options_debug_samples_subtitle),
            testTag = "debug_create_sample_files_button",
            onClick = {
              onDismiss()
              onCreateDebugSampleFiles()
            },
          )
        }
      }

      Spacer(Modifier.height(PrismDimens.SpaceStandard))
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
    cornerRadius = PrismDimens.FilterBarCornerRadius,
    elevation = GlassElevation.SUBTLE,
    enableRefraction = false,
    onClick = onClick,
    clickRole = Role.Button,
    modifier =
      Modifier.fillMaxWidth()
        .defaultMinSize(minHeight = PrismDimens.TouchTargetMin)
        .testTag(testTag),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(PrismDimens.SpaceStandard),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = colorScheme.primary,
        modifier = Modifier.size(PrismDimens.HeaderIconSize),
      )
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PrismDimens.SpaceXSmall)) {
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

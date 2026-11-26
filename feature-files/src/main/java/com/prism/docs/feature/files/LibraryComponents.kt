package com.prism.docs.feature.files

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.compose.LazyPagingItems
import com.prism.docs.core.data.db.IndexedDocumentEntity
import com.prism.docs.core.data.model.FileSortField
import com.prism.docs.core.ui.glass.ConnectedGlassCardSlice
import com.prism.docs.core.ui.glass.GlassElevation
import com.prism.docs.core.ui.glass.GlassSurface
import com.prism.docs.core.ui.glass.glassClickable
import com.prism.docs.core.ui.glass.glassShimmer
import com.prism.docs.core.ui.glass.rememberPrismHaptics
import com.prism.docs.core.ui.theme.LocalPrismVisualTokens
import com.prism.docs.core.ui.theme.PrismColors
import com.prism.docs.core.ui.theme.PrismDimens
import java.time.Instant
import java.time.ZoneId

enum class DateBucket(@StringRes val titleRes: Int) {
  TODAY(R.string.date_group_today),
  YESTERDAY(R.string.date_group_yesterday),
  THIS_WEEK(R.string.date_group_this_week),
  EARLIER(R.string.date_group_earlier),
  ALL(R.string.date_group_all_documents),
}

@Immutable
data class DateBucketBoundaries(
  val todayStartMs: Long,
  val yesterdayStartMs: Long,
  val thisWeekStartMs: Long,
) {
  companion object {
    fun current(nowEpochMs: Long = System.currentTimeMillis()): DateBucketBoundaries {
      val zone = ZoneId.systemDefault()
      val today = Instant.ofEpochMilli(nowEpochMs).atZone(zone).toLocalDate()
      return DateBucketBoundaries(
        todayStartMs = today.atStartOfDay(zone).toInstant().toEpochMilli(),
        yesterdayStartMs = today.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
        thisWeekStartMs = today.minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli(),
      )
    }
  }
}

@Immutable
data class LibraryDateSection(
  val bucket: DateBucket,
  val startIndex: Int,
  val count: Int,
)

fun resolveDateBucket(epochMs: Long, boundaries: DateBucketBoundaries): DateBucket =
  when {
    epochMs >= boundaries.todayStartMs -> DateBucket.TODAY
    epochMs >= boundaries.yesterdayStartMs -> DateBucket.YESTERDAY
    epochMs >= boundaries.thisWeekStartMs -> DateBucket.THIS_WEEK
    else -> DateBucket.EARLIER
  }

fun buildLibrarySections(
  pagedItems: LazyPagingItems<IndexedDocumentEntity>,
  sortField: FileSortField,
  boundaries: DateBucketBoundaries,
): List<LibraryDateSection> {
  val total = pagedItems.itemCount
  if (total <= 0) return emptyList()
  if (sortField != FileSortField.DATE_MODIFIED) {
    return listOf(LibraryDateSection(bucket = DateBucket.ALL, startIndex = 0, count = total))
  }

  val sections = ArrayList<LibraryDateSection>(4)
  var currentBucket: DateBucket? = null
  var currentStart = 0

  for (i in 0 until total) {
    val item = pagedItems.peek(i)
    val bucket =
      if (item != null) {
        resolveDateBucket(item.lastModifiedEpochMs, boundaries)
      } else {
        currentBucket ?: DateBucket.EARLIER
      }
    if (currentBucket == null) {
      currentBucket = bucket
      currentStart = i
    } else if (bucket != currentBucket) {
      sections.add(LibraryDateSection(currentBucket, currentStart, i - currentStart))
      currentBucket = bucket
      currentStart = i
    }
  }
  if (currentBucket != null) {
    sections.add(LibraryDateSection(currentBucket, currentStart, total - currentStart))
  }
  return sections
}

@Composable
internal fun StickyDateSectionHeader(
  bucket: DateBucket,
  modifier: Modifier = Modifier,
  useBackdropBlur: Boolean = false,
  headerTestTag: String = "date_section_header_${bucket.name}",
) {
  val colorScheme = MaterialTheme.colorScheme
  val tokens = LocalPrismVisualTokens.current
  val title = stringResource(bucket.titleRes)
  val sectionCd = stringResource(R.string.date_section_cd, title)

  Box(
    modifier =
      modifier
        .fillMaxWidth()
        .padding(top = PrismDimens.SpaceMedium, bottom = PrismDimens.SpaceSmallMedium)
        .semantics {
          heading()
          contentDescription = sectionCd
        }
        .testTag(headerTestTag),
  ) {
    GlassSurface(
      cornerRadius = PrismDimens.StickyHeaderCornerRadius,
      elevation = if (useBackdropBlur) GlassElevation.FLOATING else GlassElevation.SUBTLE,
      useBackdropBlur = useBackdropBlur,
      enableRefraction = useBackdropBlur,
      accentTint = colorScheme.primary.copy(alpha = if (tokens.isDark) 0.16f else 0.08f),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .padding(
              horizontal = PrismDimens.SpaceSection,
              vertical = PrismDimens.SpaceMediumLarge,
            ),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
          color = colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

/**
 * Compact collapsing top bar for the Library section:
 * - At rest (`scrollProgress <= 0.02f`): zero blur, zero box frame; subtitle on left, Select + Sliders on right.
 * - As content scrolls under (`scrollProgress > 0.02f`): activates Haze backdrop blur and fades in the
 *   frosted glass bar while cross-fading the centered "Library" title in.
 */
@Composable
internal fun LibraryCollapsingTopBar(
  title: String,
  subtitle: String,
  isIndexing: Boolean,
  hasActiveFilters: Boolean,
  scrollProgress: State<Float>,
  onEnterSelectionMode: () -> Unit,
  onSettingsClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme
  val tokens = LocalPrismVisualTokens.current
  val density = LocalDensity.current
  val isScrolledUnder by remember { derivedStateOf { scrollProgress.value > 0.02f } }
  val rightClusterWidthPx = remember { androidx.compose.runtime.mutableIntStateOf(0) }
  val rightClearanceDp =
    if (rightClusterWidthPx.intValue > 0) with(density) { rightClusterWidthPx.intValue.toDp() } + 10.dp
    else 144.dp
  val subtitleStartPad by
    androidx.compose.animation.core.animateDpAsState(
      targetValue = if (isScrolledUnder) 16.dp else 0.dp,
      label = "library_subtitle_start_pad",
    )

  Box(
    modifier =
      modifier
        .fillMaxWidth()
        .height(PrismDimens.HeaderBarHeight),
    contentAlignment = Alignment.Center,
  ) {
    if (isScrolledUnder) {
      GlassSurface(
        cornerRadius = PrismDimens.HeaderBarHeight / 2,
        shape = CircleShape,
        elevation = GlassElevation.FLOATING,
        useBackdropBlur = true,
        enableRefraction = true,
        modifier =
          Modifier.matchParentSize()
            .graphicsLayer {
              alpha = ((scrollProgress.value - 0.02f) / 0.42f).coerceIn(0f, 1f)
            }
            .testTag("library_collapsed_glass_bar"),
      ) {}
    }

    // Subtitle on the left when hero title is expanded below; fades out before center title fades in
    Row(
      modifier =
        Modifier.align(Alignment.CenterStart)
          .padding(start = subtitleStartPad, end = rightClearanceDp)
          .graphicsLayer { alpha = (1f - scrollProgress.value * 2.4f).coerceIn(0f, 1f) },
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(PrismDimens.SpaceSmallMedium),
    ) {
      if (isIndexing) {
        CircularProgressIndicator(
          modifier = Modifier.size(PrismDimens.HeaderIndexingSpinnerSize),
          strokeWidth = 1.8.dp,
        )
      }
      Text(
        text = subtitle,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
        color = colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.testTag("library_header_subtitle"),
      )
    }

    // Small centered "Library" title fading in once scrolled under without colliding with right cluster
    Box(
      modifier =
        Modifier.align(Alignment.CenterStart)
          .fillMaxWidth()
          .padding(start = 16.dp, end = rightClearanceDp)
          .graphicsLayer {
            val a = ((scrollProgress.value - 0.35f) / 0.50f).coerceIn(0f, 1f)
            alpha = a
            translationY = (1f - a) * 6.dp.toPx()
          },
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = title,
        style =
          MaterialTheme.typography.titleLarge.copy(
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
          ),
        color = colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = Modifier.testTag("library_collapsed_title"),
      )
    }

    // Visible "Select" glass pill + Sliders round glass button on the right (48dp touch targets)
    Row(
      modifier =
        Modifier.align(Alignment.CenterEnd)
          .onSizeChanged { rightClusterWidthPx.intValue = it.width }
          .padding(end = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(PrismDimens.SpaceMedium),
    ) {
      val selectCd = stringResource(R.string.library_select_button_cd)
      GlassSurface(
        cornerRadius = PrismDimens.CategoryChipCornerRadius,
        shape = CircleShape,
        elevation = if (isScrolledUnder) GlassElevation.SUBTLE else GlassElevation.FLOATING,
        useBackdropBlur = !isScrolledUnder,
        enableRefraction = !isScrolledUnder,
        onClick = onEnterSelectionMode,
        hapticOnClick = false,
        clickRole = Role.Button,
        modifier =
          Modifier.height(PrismDimens.TouchTargetMin)
            .defaultMinSize(minWidth = PrismDimens.TouchTargetMin, minHeight = PrismDimens.TouchTargetMin)
            .semantics { contentDescription = selectCd }
            .testTag("library_select_button"),
      ) {
        Box(
          modifier = Modifier.align(Alignment.Center).padding(horizontal = 16.dp),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = stringResource(R.string.library_select_button),
            style = MaterialTheme.typography.labelLarge,
            color = tokens.linkAccent,
            maxLines = 1,
          )
        }
      }

      Box {
        GlassSurface(
          cornerRadius = PrismDimens.CategoryChipCornerRadius,
          shape = CircleShape,
          elevation = if (isScrolledUnder) GlassElevation.SUBTLE else GlassElevation.FLOATING,
          useBackdropBlur = !isScrolledUnder,
          enableRefraction = !isScrolledUnder,
          onClick = onSettingsClick,
          clickRole = Role.Button,
          modifier =
            Modifier.size(PrismDimens.HeaderIconButtonSize)
              .defaultMinSize(
                minWidth = PrismDimens.TouchTargetMin,
                minHeight = PrismDimens.TouchTargetMin,
              )
              .testTag("library_sliders_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Tune,
            contentDescription = stringResource(R.string.library_sliders_cd),
            tint = colorScheme.onSurface,
            modifier = Modifier.align(Alignment.Center).size(PrismDimens.HeaderIconSize),
          )
        }
        androidx.compose.animation.AnimatedVisibility(
          visible = hasActiveFilters,
          enter = scaleIn(initialScale = 0.4f) + fadeIn(),
          exit = scaleOut(targetScale = 0.4f) + fadeOut(),
          modifier = Modifier.align(Alignment.TopEnd).padding(PrismDimens.HeaderFilterDotPadding),
        ) {
          Box(
            modifier =
              Modifier.size(PrismDimens.HeaderFilterDotSize)
                .clip(CircleShape)
                .background(colorScheme.primary)
          )
        }
      }
    }
  }
}

/**
 * Floating Liquid Glass contextual top bar for Library selection mode:
 * displays Close, "N selected", Select all, Tags & Collections, Share, Move, and Delete with >= 48dp touch targets.
 */
@Composable
internal fun SelectionGlassTopBar(
  selectedCount: Int,
  onClearSelection: () -> Unit,
  onSelectAll: () -> Unit,
  onTagsCollections: () -> Unit,
  onShare: () -> Unit,
  onMove: () -> Unit,
  onDelete: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme
  val tokens = LocalPrismVisualTokens.current
  val haptics = rememberPrismHaptics()
  val hasSelection = selectedCount > 0

  GlassSurface(
    cornerRadius = 26.dp,
    shape = RoundedCornerShape(26.dp),
    elevation = GlassElevation.FLOATING,
    useBackdropBlur = true,
    enableRefraction = true,
    accentTint = colorScheme.primary,
    modifier =
      modifier
        .fillMaxWidth()
        .defaultMinSize(minHeight = PrismDimens.HeaderBarHeight)
        .testTag("selection_glass_top_bar"),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(
          onClick = {
            haptics.tap()
            onClearSelection()
          },
          modifier =
            Modifier.size(PrismDimens.TouchTargetMin)
              .testTag("selection_clear_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = stringResource(R.string.selection_clear_cd),
            tint = colorScheme.onSurface,
          )
        }

        Text(
          text = pluralStringResource(R.plurals.library_selected_count, selectedCount, selectedCount),
          style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
          color = colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f).testTag("selection_count_text"),
        )

        val selectAllCd = stringResource(R.string.selection_top_bar_select_all_cd)
        Box(
          modifier =
            Modifier.height(PrismDimens.TouchTargetMin)
              .clip(CircleShape)
              .glassClickable(role = Role.Button, hapticOnClick = false, onClick = onSelectAll)
              .semantics { contentDescription = selectAllCd }
              .padding(horizontal = 12.dp)
              .testTag("selection_select_all_button"),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = stringResource(R.string.selection_select_all),
            style = MaterialTheme.typography.labelLarge,
            color = tokens.linkAccent,
            maxLines = 1,
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(
          onClick = {
            haptics.tap()
            onTagsCollections()
          },
          enabled = hasSelection,
          modifier =
            Modifier.size(PrismDimens.TouchTargetMin)
              .testTag("selection_tag_collection_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.Label,
            contentDescription = stringResource(R.string.selection_tag_collection_cd),
            tint = if (hasSelection) colorScheme.primary else colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
          )
        }

        IconButton(
          onClick = {
            haptics.tap()
            onShare()
          },
          enabled = hasSelection,
          modifier =
            Modifier.size(PrismDimens.TouchTargetMin)
              .testTag("selection_share_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Share,
            contentDescription = stringResource(R.string.selection_share_cd),
            tint = if (hasSelection) colorScheme.onSurface else colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
          )
        }

        IconButton(
          onClick = {
            haptics.tap()
            onMove()
          },
          enabled = hasSelection,
          modifier =
            Modifier.size(PrismDimens.TouchTargetMin)
              .testTag("selection_move_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.DriveFileMove,
            contentDescription = stringResource(R.string.selection_move_cd),
            tint = if (hasSelection) colorScheme.onSurface else colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
          )
        }

        IconButton(
          onClick = {
            haptics.reject()
            onDelete()
          },
          enabled = hasSelection,
          modifier =
            Modifier.size(PrismDimens.TouchTargetMin)
              .testTag("selection_delete_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.DeleteOutline,
            contentDescription = stringResource(R.string.selection_delete_cd),
            tint = if (hasSelection) colorScheme.error else colorScheme.error.copy(alpha = 0.45f),
          )
        }
      }
    }
  }
}

@Composable
internal fun PrimaryGlassButton(
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val tokens = LocalPrismVisualTokens.current
  val gradientBrush = remember(tokens.brandGradient) { Brush.linearGradient(colors = tokens.brandGradient) }
  val borderColor = Color.White.copy(alpha = if (tokens.isDark) 0.42f else 0.72f)

  Box(
    modifier =
      modifier
        .height(PrismDimens.TouchTargetMin)
        .defaultMinSize(minWidth = 128.dp, minHeight = PrismDimens.TouchTargetMin)
        .shadow(
          elevation = PrismDimens.ShadowMedium,
          shape = CircleShape,
          ambientColor = PrismColors.IndigoBlue.copy(alpha = 0.34f),
          spotColor = PrismColors.VioletAccent.copy(alpha = 0.42f),
        )
        .clip(CircleShape)
        .background(gradientBrush)
        .border(width = PrismDimens.GlassTopEdgeWidth, color = borderColor, shape = CircleShape)
        .glassClickable(role = Role.Button, onClick = onClick)
        .padding(horizontal = PrismDimens.SpaceXLarge),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
      color = PrismColors.OnAccent,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
private fun LibraryEmptyGlassIllustration(modifier: Modifier = Modifier) {
  val colorScheme = MaterialTheme.colorScheme
  val tokens = LocalPrismVisualTokens.current
  val badgeGradient = remember(tokens.brandGradient) { Brush.linearGradient(colors = tokens.brandGradient) }

  Box(
    modifier =
      modifier
        .size(width = 160.dp, height = 108.dp)
        .clearAndSetSemantics {},
    contentAlignment = Alignment.Center,
  ) {
    GlassSurface(
      cornerRadius = 18.dp,
      elevation = GlassElevation.SUBTLE,
      useBackdropBlur = false,
      modifier = Modifier.size(width = 84.dp, height = 74.dp).offset(x = (-30).dp, y = 8.dp).rotate(-8f),
    ) {}

    GlassSurface(
      cornerRadius = 18.dp,
      elevation = GlassElevation.SUBTLE,
      useBackdropBlur = false,
      modifier = Modifier.size(width = 84.dp, height = 74.dp).offset(x = 30.dp, y = 8.dp).rotate(7f),
    ) {}

    GlassSurface(
      cornerRadius = 22.dp,
      elevation = GlassElevation.FLOATING,
      useBackdropBlur = false,
      modifier = Modifier.size(width = 104.dp, height = 92.dp),
    ) {
      Column(
        modifier = Modifier.fillMaxSize().padding(PrismDimens.SpaceStandard),
        verticalArrangement = Arrangement.spacedBy(PrismDimens.SpaceSmallMedium),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(PrismDimens.SpaceMedium),
        ) {
          Box(
            modifier =
              Modifier.size(28.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(badgeGradient),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Rounded.Description,
              contentDescription = null,
              tint = PrismColors.OnAccent,
              modifier = Modifier.size(16.dp),
            )
          }
          Box(
            modifier =
              Modifier.fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .background(colorScheme.primary.copy(alpha = 0.28f))
          )
        }
        Box(
          modifier =
            Modifier.fillMaxWidth(0.88f)
              .height(6.dp)
              .clip(CircleShape)
              .background(tokens.fallbackLine)
        )
        Box(
          modifier =
            Modifier.fillMaxWidth(0.68f)
              .height(6.dp)
              .clip(CircleShape)
              .background(tokens.fallbackLine)
        )
      }
    }
  }
}

@Composable
internal fun LibraryEmptyStateCard(
  hasActiveFilters: Boolean,
  hasFullAccess: Boolean,
  isDebugBuild: Boolean,
  onClearFilters: () -> Unit,
  onOpenOptions: () -> Unit,
  onRequestAllFilesAccess: () -> Unit,
  onAddSafFolder: () -> Unit,
  onTriggerSync: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme
  val title =
    stringResource(
      if (hasActiveFilters) R.string.empty_filtered_title else R.string.empty_library_title
    )
  val body =
    when {
      hasActiveFilters -> stringResource(R.string.empty_filtered_body)
      hasFullAccess && isDebugBuild -> stringResource(R.string.empty_library_body_full_debug)
      hasFullAccess -> stringResource(R.string.empty_library_body_full_release)
      else -> stringResource(R.string.empty_library_body_limited)
    }

  val primaryLabel: String
  val onPrimaryClick: () -> Unit
  val secondaryLabel: String
  val onSecondaryClick: () -> Unit

  when {
    hasActiveFilters -> {
      primaryLabel = stringResource(R.string.empty_action_clear_filters)
      onPrimaryClick = onClearFilters
      secondaryLabel = stringResource(R.string.empty_action_open_options)
      onSecondaryClick = onOpenOptions
    }
    hasFullAccess -> {
      primaryLabel = stringResource(R.string.empty_action_sync_now)
      onPrimaryClick = onTriggerSync
      secondaryLabel = stringResource(R.string.empty_action_add_folder)
      onSecondaryClick = onAddSafFolder
    }
    else -> {
      primaryLabel = stringResource(R.string.empty_action_grant_access)
      onPrimaryClick = onRequestAllFilesAccess
      secondaryLabel = stringResource(R.string.empty_action_add_folder)
      onSecondaryClick = onAddSafFolder
    }
  }

  GlassSurface(
    cornerRadius = PrismDimens.EmptyCardCornerRadius,
    elevation = GlassElevation.SUBTLE,
    useBackdropBlur = false,
    modifier = modifier.fillMaxWidth().testTag("library_empty_card"),
  ) {
    Column(
      modifier = Modifier.padding(PrismDimens.SpaceXLarge).fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(PrismDimens.SpaceStandard),
    ) {
      LibraryEmptyGlassIllustration()
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = colorScheme.onSurface,
        textAlign = TextAlign.Center,
      )
      Text(
        text = body,
        style = MaterialTheme.typography.bodyMedium,
        color = colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
      Spacer(modifier = Modifier.height(PrismDimens.SpaceSmall))
      Row(
        horizontalArrangement = Arrangement.spacedBy(PrismDimens.SpaceStandard),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        val haptics = rememberPrismHaptics()
        PrimaryGlassButton(
          label = primaryLabel,
          onClick = onPrimaryClick,
          modifier = Modifier.testTag("library_empty_primary_button"),
        )
        TextButton(
          onClick = {
            haptics.tap()
            onSecondaryClick()
          },
          modifier =
            Modifier.defaultMinSize(minHeight = PrismDimens.TouchTargetMin)
              .testTag("library_empty_secondary_button"),
        ) {
          Text(text = secondaryLabel)
        }
      }
    }
  }
}

@Composable
internal fun LibraryRecentSkeletonRow(modifier: Modifier = Modifier) {
  val loadingCd = stringResource(R.string.library_loading_cd)
  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .semantics { contentDescription = loadingCd }
        .testTag("library_recent_skeleton"),
    verticalArrangement = Arrangement.spacedBy(PrismDimens.SpaceSmallMedium),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier =
          Modifier.size(width = 88.dp, height = 22.dp)
            .glassShimmer(cornerRadius = 11.dp)
      )
      Box(
        modifier =
          Modifier.size(width = 58.dp, height = 18.dp)
            .glassShimmer(cornerRadius = 9.dp)
      )
    }

    LazyRow(
      horizontalArrangement = Arrangement.spacedBy(PrismDimens.SpaceRow),
      contentPadding =
        PaddingValues(
          top = PrismDimens.SpaceSmallMedium,
          bottom = PrismDimens.SpaceStandard,
          end = PrismDimens.RecentFadeWidth,
        ),
      userScrollEnabled = false,
      modifier = Modifier.fillMaxWidth(),
    ) {
      items(4) {
        GlassSurface(
          cornerRadius = PrismDimens.RecentCardCornerRadius,
          elevation = GlassElevation.SUBTLE,
          useBackdropBlur = false,
          modifier = Modifier.width(PrismDimens.RecentCardWidth),
        ) {
          Column(
            modifier = Modifier.padding(PrismDimens.SpaceMediumLarge),
            verticalArrangement = Arrangement.spacedBy(PrismDimens.SpaceMedium),
          ) {
            Box(
              modifier =
                Modifier.fillMaxWidth()
                  .height(PrismDimens.RecentThumbnailHeight)
                  .glassShimmer(cornerRadius = PrismDimens.RecentThumbnailCornerRadius)
            )
            Box(
              modifier =
                Modifier.fillMaxWidth(0.84f)
                  .height(14.dp)
                  .glassShimmer(cornerRadius = 7.dp)
            )
            Box(
              modifier =
                Modifier.fillMaxWidth(0.56f)
                  .height(11.dp)
                  .glassShimmer(cornerRadius = 6.dp)
            )
          }
        }
      }
    }
  }
}

@Composable
internal fun LibraryListSkeletonRow(
  isFirst: Boolean,
  isLast: Boolean,
  modifier: Modifier = Modifier,
) {
  val tokens = LocalPrismVisualTokens.current
  ConnectedGlassCardSlice(
    isFirst = isFirst,
    isLast = isLast,
    cornerRadius = PrismDimens.ListCardCornerRadius,
    modifier = modifier.then(if (isFirst) Modifier.testTag("library_list_skeleton") else Modifier),
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .padding(
              start = PrismDimens.SpaceSection,
              top = PrismDimens.SpaceRow,
              bottom = PrismDimens.SpaceRow,
              end = PrismDimens.SpaceSection,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PrismDimens.SpaceRow),
      ) {
        Box(
          modifier =
            Modifier.size(PrismDimens.BadgeSquareSize)
              .glassShimmer(cornerRadius = PrismDimens.BadgeSquareCornerRadius)
        )
        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
          Box(
            modifier =
              Modifier.fillMaxWidth(0.68f)
                .height(15.dp)
                .glassShimmer(cornerRadius = 7.dp)
          )
          Box(
            modifier =
              Modifier.fillMaxWidth(0.42f)
                .height(11.dp)
                .glassShimmer(cornerRadius = 6.dp)
          )
        }
        Box(
          modifier =
            Modifier.size(24.dp)
              .glassShimmer(cornerRadius = 12.dp)
        )
      }
      if (!isLast) {
        Box(
          modifier =
            Modifier.fillMaxWidth()
              .padding(horizontal = PrismDimens.SpaceSection)
              .height(PrismDimens.HairlineWidth)
              .background(tokens.rowDivider)
        )
      }
    }
  }
}

@Composable
internal fun LibraryGridSkeletonCard(modifier: Modifier = Modifier) {
  GlassSurface(
    cornerRadius = PrismDimens.RecentCardCornerRadius,
    elevation = GlassElevation.SUBTLE,
    useBackdropBlur = false,
    modifier = modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(PrismDimens.SpaceStandard),
      verticalArrangement = Arrangement.spacedBy(PrismDimens.SpaceMediumLarge),
    ) {
      Box(
        modifier =
          Modifier.fillMaxWidth()
            .aspectRatio(1.08f)
            .glassShimmer(cornerRadius = PrismDimens.RecentThumbnailCornerRadius)
      )
      Box(
        modifier =
          Modifier.fillMaxWidth(0.78f)
            .height(15.dp)
            .glassShimmer(cornerRadius = 7.dp)
      )
      Box(
        modifier =
          Modifier.fillMaxWidth(0.52f)
            .height(11.dp)
            .glassShimmer(cornerRadius = 6.dp)
      )
    }
  }
}

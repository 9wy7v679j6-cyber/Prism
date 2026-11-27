package com.prism.docs.feature.search

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.rounded.SortByAlpha
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.prism.docs.core.data.db.IndexedDocumentEntity
import com.prism.docs.core.data.model.DocumentCategory
import com.prism.docs.core.data.model.FileSortField
import com.prism.docs.core.data.model.IncomingDocumentRequest
import com.prism.docs.core.ui.glass.GlassElevation
import com.prism.docs.core.ui.glass.GlassSearchField
import com.prism.docs.core.ui.glass.GlassSurface
import com.prism.docs.core.ui.glass.GlassToolbarAction
import com.prism.docs.core.ui.glass.GlassToolbarGroup
import com.prism.docs.core.ui.glass.ScrollEdgeFade
import com.prism.docs.core.ui.glass.glassClickable
import com.prism.docs.core.ui.glass.rememberHeaderScrollProgress
import com.prism.docs.core.ui.glass.rememberPrismHaptics
import com.prism.docs.core.ui.theme.LocalGlassEnvironment
import com.prism.docs.core.ui.theme.LocalPrismVisualTokens
import com.prism.docs.core.ui.theme.PrismColors
import com.prism.docs.core.ui.theme.PrismDimens
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

private data class QuickFilterPill(
  val tagSuffix: String,
  val label: String,
  val selected: Boolean,
  val icon: ImageVector?,
  val onClick: () -> Unit,
)

@Composable
fun SearchScreen(
  onNavigateBack: () -> Unit,
  onOpenDocument: (IncomingDocumentRequest) -> Unit = {},
  modifier: Modifier = Modifier,
  viewModel: SearchViewModel = hiltViewModel(),
) {
  val colorScheme = MaterialTheme.colorScheme
  val env = LocalGlassEnvironment.current
  val tokens = LocalPrismVisualTokens.current
  val density = LocalDensity.current
  val haptics = rememberPrismHaptics()
  val focusManager = LocalFocusManager.current
  val keyboardController = LocalSoftwareKeyboardController.current
  val searchFocusRequester = remember { FocusRequester() }

  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val pagedResults = viewModel.pagedResults.collectAsLazyPagingItems()
  val listState = rememberLazyListState()
  val headerScrollProgress = rememberHeaderScrollProgress(listState)

  LaunchedEffect(Unit) {
    delay(120L)
    runCatching { searchFocusRequester.requestFocus() }
    keyboardController?.show()
  }

  val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
  val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
  val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
  var topHeaderPx by remember { mutableIntStateOf(0) }
  var bottomDockPx by remember { mutableIntStateOf(0) }
  val topHeaderDp =
    if (topHeaderPx > 0) with(density) { topHeaderPx.toDp() } else statusBarTop + 72.dp
  val bottomDockDp =
    if (bottomDockPx > 0) with(density) { bottomDockPx.toDp() }
    else maxOf(navBarBottom, imeBottom) + 128.dp

  val allLabel = stringResource(R.string.search_filter_all)
  val docsLabel = stringResource(R.string.search_filter_docs)
  val favLabel = stringResource(R.string.search_filter_favorites)
  val recentsLabel = stringResource(R.string.search_filter_recents)
  val thisWeekLabel = stringResource(R.string.search_filter_this_week)
  val pdfLabel = stringResource(R.string.search_filter_pdf)
  val wordLabel = stringResource(R.string.search_filter_word)
  val excelLabel = stringResource(R.string.search_filter_excel)
  val slidesLabel = stringResource(R.string.search_filter_slides)
  val imagesLabel = stringResource(R.string.search_filter_images)
  val codeLabel = stringResource(R.string.search_filter_code)
  val booksLabel = stringResource(R.string.search_filter_books)

  val quickFilterPills =
    buildList {
      add(
        QuickFilterPill(
          tagSuffix = "all",
          label = allLabel,
          selected = !uiState.hasActiveFilters,
          icon = Icons.Rounded.Folder,
          onClick = { viewModel.selectQuickFilter(SearchQuickFilterId.ALL) },
        )
      )
      add(
        QuickFilterPill(
          tagSuffix = "docs",
          label = docsLabel,
          selected = uiState.documentsOnly && uiState.selectedCategory == null,
          icon = Icons.Rounded.Description,
          onClick = { viewModel.selectQuickFilter(SearchQuickFilterId.DOCS) },
        )
      )
      add(
        QuickFilterPill(
          tagSuffix = "favorites",
          label = favLabel,
          selected = uiState.favoritesOnly,
          icon = Icons.Rounded.Star,
          onClick = { viewModel.selectQuickFilter(SearchQuickFilterId.FAVORITES) },
        )
      )
      add(
        QuickFilterPill(
          tagSuffix = "recents",
          label = recentsLabel,
          selected = uiState.recentsOnly,
          icon = Icons.Rounded.History,
          onClick = { viewModel.selectQuickFilter(SearchQuickFilterId.RECENTS) },
        )
      )
      add(
        QuickFilterPill(
          tagSuffix = "this week",
          label = thisWeekLabel,
          selected = uiState.thisWeekOnly,
          icon = Icons.Rounded.Schedule,
          onClick = { viewModel.selectQuickFilter(SearchQuickFilterId.THIS_WEEK) },
        )
      )
      add(
        QuickFilterPill(
          tagSuffix = "pdf",
          label = pdfLabel,
          selected = uiState.selectedCategory == DocumentCategory.PDF,
          icon = Icons.Rounded.PictureAsPdf,
          onClick = { viewModel.selectQuickFilter(SearchQuickFilterId.PDF) },
        )
      )
      add(
        QuickFilterPill(
          tagSuffix = "word",
          label = wordLabel,
          selected = uiState.selectedCategory == DocumentCategory.DOCUMENTS,
          icon = Icons.Rounded.Description,
          onClick = { viewModel.selectQuickFilter(SearchQuickFilterId.DOCUMENTS) },
        )
      )
      add(
        QuickFilterPill(
          tagSuffix = "excel",
          label = excelLabel,
          selected = uiState.selectedCategory == DocumentCategory.SPREADSHEETS,
          icon = Icons.Rounded.TableChart,
          onClick = { viewModel.selectQuickFilter(SearchQuickFilterId.SPREADSHEETS) },
        )
      )
      add(
        QuickFilterPill(
          tagSuffix = "slides",
          label = slidesLabel,
          selected = uiState.selectedCategory == DocumentCategory.SLIDES,
          icon = Icons.Rounded.Slideshow,
          onClick = { viewModel.selectQuickFilter(SearchQuickFilterId.SLIDES) },
        )
      )
      add(
        QuickFilterPill(
          tagSuffix = "images",
          label = imagesLabel,
          selected = uiState.selectedCategory == DocumentCategory.IMAGES,
          icon = Icons.Rounded.Image,
          onClick = { viewModel.selectQuickFilter(SearchQuickFilterId.IMAGES) },
        )
      )
      add(
        QuickFilterPill(
          tagSuffix = "code",
          label = codeLabel,
          selected = uiState.selectedCategory == DocumentCategory.CODE_TEXT,
          icon = Icons.Rounded.Code,
          onClick = { viewModel.selectQuickFilter(SearchQuickFilterId.CODE_TEXT) },
        )
      )
      add(
        QuickFilterPill(
          tagSuffix = "books",
          label = booksLabel,
          selected = uiState.selectedCategory == DocumentCategory.EBOOKS,
          icon = Icons.AutoMirrored.Rounded.MenuBook,
          onClick = { viewModel.selectQuickFilter(SearchQuickFilterId.EBOOKS) },
        )
      )
      uiState.tags.forEach { tag ->
        add(
          QuickFilterPill(
            tagSuffix = "tag_${tag.id}",
            label = "#${tag.name}",
            selected = uiState.selectedTagId == tag.id,
            icon = null,
            onClick = { viewModel.selectTagFilter(tag.id) },
          )
        )
      }
    }

  val isInitialLoading =
    pagedResults.loadState.refresh is LoadState.Loading && pagedResults.itemCount == 0

  val backdropScrim =
    if (env.reduceTransparency) colorScheme.background
    else colorScheme.background.copy(alpha = if (env.isDark) 0.58f else 0.64f)

  Box(modifier = modifier.fillMaxSize().background(backdropScrim)) {
    LazyColumn(
      state = listState,
      contentPadding =
        PaddingValues(
          top = topHeaderDp + 8.dp,
          bottom = bottomDockDp + 12.dp,
          start = 16.dp,
          end = 16.dp,
        ),
      verticalArrangement = Arrangement.spacedBy(10.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier.fillMaxSize().testTag("search_results_list"),
    ) {
      if (isInitialLoading) {
        item(key = "search_loading") {
          GlassSurface(
            cornerRadius = 24.dp,
            elevation = GlassElevation.SUBTLE,
            useBackdropBlur = false,
            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(20.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
              Text(
                text = stringResource(R.string.search_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      } else if (pagedResults.itemCount == 0) {
        item(key = "search_empty") {
          val hasCriteria = uiState.queryText.isNotBlank() || uiState.hasActiveFilters
          GlassSurface(
            cornerRadius = 24.dp,
            elevation = GlassElevation.SUBTLE,
            useBackdropBlur = false,
            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(20.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Icon(
                  imageVector = Icons.Rounded.Search,
                  contentDescription = null,
                  tint = colorScheme.primary,
                )
                Text(
                  text =
                    if (hasCriteria) stringResource(R.string.search_empty_no_matches_title)
                    else stringResource(R.string.search_empty_prompt_title),
                  style = MaterialTheme.typography.titleMedium,
                  color = colorScheme.onSurface,
                )
              }
              Text(
                text =
                  if (hasCriteria) stringResource(R.string.search_empty_no_matches_body)
                  else stringResource(R.string.search_empty_prompt_body),
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant,
              )
              if (hasCriteria) {
                TextButton(
                  onClick = {
                    haptics.tap()
                    viewModel.clearAllFilters()
                  },
                  modifier =
                    Modifier.defaultMinSize(minHeight = PrismDimens.TouchTargetMin)
                      .testTag("search_clear_filters_button"),
                ) {
                  Text(text = stringResource(R.string.search_clear_filters))
                }
              }
            }
          }
        }
      } else {
        items(
          count = pagedResults.itemCount,
          key = { idx -> pagedResults.peek(idx)?.id ?: "search_placeholder_$idx" },
          contentType = { "search_doc_row" },
        ) { index ->
          val doc = pagedResults[index] ?: return@items
          SearchResultDocumentCard(
            document = doc,
            onClick = {
              haptics.tap()
              focusManager.clearFocus()
              keyboardController?.hide()
              viewModel.recordDocumentOpened(doc.id)
              onOpenDocument(
                IncomingDocumentRequest(
                  uriString = doc.uriString,
                  displayName = doc.displayName,
                  mimeType = doc.mimeType,
                  sizeBytes = doc.sizeBytes,
                  sourceAction = "SEARCH",
                )
              )
            },
            onToggleFavorite = {
              haptics.segmentTick()
              viewModel.toggleFavorite(doc.id, doc.isFavorite)
            },
            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
          )
        }
      }
    }

    ScrollEdgeFade(
      isTop = true,
      height = topHeaderDp + 10.dp,
      strongTint = true,
      scrollProgress = headerScrollProgress,
      modifier = Modifier.align(Alignment.TopCenter),
    )

    // Compact opaque Top Bar (Back + Title/Matches + Sort)
    Row(
      modifier =
        Modifier.align(Alignment.TopCenter)
          .widthIn(max = 720.dp)
          .fillMaxWidth()
          .onSizeChanged { topHeaderPx = it.height }
          .statusBarsPadding()
          .padding(horizontal = 16.dp, vertical = 10.dp),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      GlassToolbarGroup(
        actions =
          listOf(
            GlassToolbarAction(
              icon = Icons.AutoMirrored.Rounded.ArrowBack,
              contentDescription = stringResource(R.string.search_back_cd),
              testTag = "search_back_button",
              onClick = {
                focusManager.clearFocus()
                keyboardController?.hide()
                onNavigateBack()
              },
            )
          )
      )

      GlassSurface(
        cornerRadius = 24.dp,
        elevation = GlassElevation.SUBTLE,
        useBackdropBlur = false,
        modifier = Modifier.weight(1f).height(48.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = stringResource(R.string.search_title),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text =
              pluralStringResource(
                R.plurals.search_match_count,
                pagedResults.itemCount,
                pagedResults.itemCount,
              ),
            style = MaterialTheme.typography.labelMedium,
            color = tokens.linkAccent,
            maxLines = 1,
          )
        }
      }

      GlassToolbarGroup(
        actions =
          listOf(
            GlassToolbarAction(
              icon = Icons.Rounded.SortByAlpha,
              contentDescription =
                if (uiState.sortField == FileSortField.NAME) "Sorted by name"
                else "Sorted by date",
              testTag = "search_sort_toggle_button",
              active = uiState.sortField == FileSortField.NAME,
              onClick = { viewModel.toggleSortField() },
            )
          )
      )
    }

    // Bottom Search Dock: Opaque Quick Filtering Options + Liquid Glass Search Bar right above keyboard
    Column(
      modifier =
        Modifier.align(Alignment.BottomCenter)
          .widthIn(max = 720.dp)
          .fillMaxWidth()
          .onSizeChanged { bottomDockPx = it.height }
          .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
          .padding(horizontal = 16.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      val quickFiltersCd = stringResource(R.string.search_quick_filters_cd)
      LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 2.dp),
        modifier =
          Modifier.fillMaxWidth()
            .semantics { contentDescription = quickFiltersCd }
            .testTag("search_quick_filters_row"),
      ) {
        items(quickFilterPills, key = { it.tagSuffix }) { pill ->
          val containerColor by
            animateColorAsState(
              targetValue = if (pill.selected) colorScheme.primary else colorScheme.surface,
              label = "search_pill_bg_${pill.tagSuffix}",
            )
          val contentColor by
            animateColorAsState(
              targetValue = if (pill.selected) colorScheme.onPrimary else colorScheme.onSurface,
              label = "search_pill_fg_${pill.tagSuffix}",
            )
          val borderColor by
            animateColorAsState(
              targetValue = if (pill.selected) colorScheme.primary else colorScheme.outline,
              label = "search_pill_border_${pill.tagSuffix}",
            )
          Row(
            modifier =
              Modifier.defaultMinSize(minHeight = PrismDimens.TouchTargetMin)
                .clip(CircleShape)
                .background(containerColor)
                .border(width = 1.dp, color = borderColor, shape = CircleShape)
                .glassClickable(role = Role.Tab, hapticOnClick = false) {
                  haptics.segmentTick()
                  pill.onClick()
                }
                .semantics { selected = pill.selected }
                .testTag("search_filter_${pill.tagSuffix}")
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            if (pill.icon != null) {
              Icon(
                imageVector = pill.icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp),
              )
            }
            Text(
              text = pill.label,
              style =
                MaterialTheme.typography.labelLarge.copy(
                  fontWeight = if (pill.selected) FontWeight.Bold else FontWeight.SemiBold,
                ),
              color = contentColor,
              maxLines = 1,
            )
          }
        }
      }

      GlassSearchField(
        query = uiState.queryText,
        onQueryChange = viewModel::setQuery,
        placeholder = stringResource(R.string.search_input_placeholder),
        onSearchSubmit = {
          focusManager.clearFocus()
          keyboardController?.hide()
        },
        focusRequester = searchFocusRequester,
        modifier = Modifier.fillMaxWidth(),
      )
    }
  }
}

@Composable
private fun SearchResultDocumentCard(
  document: IndexedDocumentEntity,
  onClick: () -> Unit,
  onToggleFavorite: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme
  val tokens = LocalPrismVisualTokens.current
  val category =
    remember(document.category) {
      runCatching { DocumentCategory.valueOf(document.category) }.getOrDefault(DocumentCategory.OTHER)
    }
  val accentColor = remember(category) { categoryAccentColor(category) }
  val badgeLabel =
    remember(document.extension, category) {
      document.extension.uppercase(Locale.US).ifBlank { category.shortLabel }.take(4)
    }
  val metaLine =
    stringResource(
      R.string.search_row_meta_format,
      formatSearchBytes(document.sizeBytes),
      formatSearchDate(document.lastModifiedEpochMs),
    )

  GlassSurface(
    cornerRadius = 20.dp,
    elevation = GlassElevation.SUBTLE,
    useBackdropBlur = false,
    onClick = onClick,
    clickRole = Role.Button,
    modifier = modifier.testTag("search_result_${document.id}"),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Box(
        modifier =
          Modifier.size(44.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(accentColor.copy(alpha = if (tokens.isDark) 0.24f else 0.14f)),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = badgeLabel,
          style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
          color = accentColor,
          maxLines = 1,
        )
      }

      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        Text(
          text = document.displayName,
          style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
          color = colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = metaLine,
          style = MaterialTheme.typography.bodySmall,
          color = colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }

      IconButton(
        onClick = onToggleFavorite,
        modifier =
          Modifier.defaultMinSize(
              minWidth = PrismDimens.TouchTargetMin,
              minHeight = PrismDimens.TouchTargetMin,
            )
            .testTag("search_fav_${document.id}"),
      ) {
        Icon(
          imageVector = if (document.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarOutline,
          contentDescription =
            if (document.isFavorite) {
              stringResource(R.string.search_favorite_remove_cd, document.displayName)
            } else {
              stringResource(R.string.search_favorite_add_cd, document.displayName)
            },
          tint = if (document.isFavorite) tokens.favoriteAccent else colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

private fun categoryAccentColor(category: DocumentCategory): Color =
  when (category) {
    DocumentCategory.PDF -> PrismColors.CoralPdf
    DocumentCategory.DOCUMENTS -> PrismColors.IndigoBlue
    DocumentCategory.SPREADSHEETS -> PrismColors.EmeraldSheet
    DocumentCategory.SLIDES -> PrismColors.OrangeSlide
    DocumentCategory.IMAGES -> PrismColors.OrchidImage
    DocumentCategory.CODE_TEXT -> PrismColors.TealCode
    DocumentCategory.EBOOKS -> PrismColors.Amethyst
    DocumentCategory.ARCHIVES -> PrismColors.IndigoArchive
    DocumentCategory.VIDEOS,
    DocumentCategory.AUDIO,
    DocumentCategory.OTHER -> PrismColors.Sapphire
  }

private fun formatSearchBytes(bytes: Long): String {
  if (bytes < 0L) return "—"
  if (bytes < 1024L) return "$bytes B"
  val kb = bytes / 1024.0
  if (kb < 1024.0) return String.format(Locale.US, "%.0f KB", kb)
  val mb = kb / 1024.0
  if (mb < 1024.0) return String.format(Locale.US, "%.1f MB", mb)
  val gb = mb / 1024.0
  return String.format(Locale.US, "%.2f GB", gb)
}

private val SEARCH_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

private fun formatSearchDate(epochMs: Long): String {
  if (epochMs <= 0L) return "—"
  return runCatching {
      SEARCH_DATE_FORMATTER
        .withLocale(Locale.getDefault())
        .format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))
    }
    .getOrDefault("—")
}

package com.prism.docs.feature.viewer

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prism.docs.core.data.model.IncomingDocumentRequest
import com.prism.docs.core.ui.glass.GlassElevation
import com.prism.docs.core.ui.glass.GlassSheetDetent
import com.prism.docs.core.ui.glass.GlassSurface
import com.prism.docs.core.ui.glass.LocalGlassBackdrop
import com.prism.docs.core.ui.glass.LocalGlassMotion
import com.prism.docs.core.ui.glass.glassContentCaptureSource
import com.prism.docs.core.ui.glass.rememberScreenGlassBackdropState
import com.prism.docs.core.ui.theme.LocalHazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay

@Composable
fun ViewerShellScreen(
  document: IncomingDocumentRequest?,
  onNavigateBack: () -> Unit,
  onOpenExternalChooser: ((String, String) -> Unit)? = null,
  onOpenEditorRoute: () -> Unit = {},
  modifier: Modifier = Modifier,
  viewModel: ViewerViewModel = hiltViewModel(),
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val density = LocalDensity.current
  val motion = LocalGlassMotion.current
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  LaunchedEffect(document) {
    viewModel.openDocument(document)
  }

  DisposableEffect(lifecycleOwner, viewModel) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_STOP) {
        viewModel.persistReadingStateNow()
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
      viewModel.persistReadingStateNow()
    }
  }

  LaunchedEffect(uiState.statusMessage) {
    if (uiState.statusMessage != null) {
      delay(2400L)
      viewModel.clearStatusMessage()
    }
  }

  var isTopBarMinimized by remember { mutableStateOf(false) }
  var isChromeHidden by remember { mutableStateOf(false) }
  var showOptionsSheet by remember { mutableStateOf(false) }
  var optionsDetent by remember { mutableStateOf(GlassSheetDetent.HALF) }
  var showOutlineSheet by remember { mutableStateOf(false) }
  var outlineDetent by remember { mutableStateOf(GlassSheetDetent.HALF) }

  // Nested scroll detector: scrolling down minimizes the top bar & hides bottom chrome so PDF view is unobstructed;
  // scrolling up restores both. Threshold prevents micro-jitter.
  var accumulatedDy by remember { mutableFloatStateOf(0f) }
  val scrollCollapseConnection =
    remember {
      object : NestedScrollConnection {
        override fun onPostScroll(
          consumed: Offset,
          available: Offset,
          source: NestedScrollSource,
        ): Offset {
          if (source == NestedScrollSource.UserInput && kotlin.math.abs(consumed.y) > 0.5f) {
            if ((accumulatedDy > 0f && consumed.y < 0f) || (accumulatedDy < 0f && consumed.y > 0f)) {
              accumulatedDy = 0f
            }
            accumulatedDy += consumed.y
            if (accumulatedDy < -36f) {
              if (!isTopBarMinimized) isTopBarMinimized = true
              if (!isChromeHidden) isChromeHidden = true
              accumulatedDy = 0f
            } else if (accumulatedDy > 36f) {
              if (isTopBarMinimized) isTopBarMinimized = false
              if (isChromeHidden) isChromeHidden = false
              accumulatedDy = 0f
            }
          }
          return Offset.Zero
        }
      }
    }

  val toggleChromeOnTap: () -> Unit = {
    if (isTopBarMinimized || isChromeHidden) {
      isTopBarMinimized = false
      isChromeHidden = false
    } else {
      isTopBarMinimized = true
      isChromeHidden = true
    }
  }

  val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
  val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
  var topChromeHeightPx by remember { mutableIntStateOf(0) }
  var bottomChromeHeightPx by remember { mutableIntStateOf(0) }
  val topChromeDp =
    if (topChromeHeightPx > 0) with(density) { topChromeHeightPx.toDp() }
    else statusBarTop + if (uiState.isSearchVisible) 132.dp else 72.dp
  val bottomChromeDp =
    if (bottomChromeHeightPx > 0 && !isChromeHidden) with(density) { bottomChromeHeightPx.toDp() }
    else navBarBottom + 16.dp

  val screenBackdropState = rememberScreenGlassBackdropState()
  val viewerHazeState = rememberHazeState()

  val copiedMessage = stringResource(R.string.viewer_selection_copied)
  val ocrStubMessage = stringResource(R.string.viewer_scanned_ocr_stub_message)
  val shareLabel = stringResource(R.string.viewer_share_cd)

  val openExternalHandler: (() -> Unit)? =
    if (document != null && onOpenExternalChooser != null) {
      { onOpenExternalChooser(document.uriString, document.mimeType) }
    } else null

  val shareDocumentHandler: () -> Unit = {
    val intent = viewModel.buildShareDocumentIntent()
    if (intent != null) {
      runCatching { context.startActivity(Intent.createChooser(intent, shareLabel)) }
    } else {
      openExternalHandler?.invoke()
    }
  }

  CompositionLocalProvider(
    LocalGlassBackdrop provides screenBackdropState,
    LocalHazeState provides viewerHazeState,
  ) {
    Box(modifier = modifier.fillMaxSize()) {
      // Document viewport (opaque paper/surface for reading) captured for floating glass bars
      Box(
        modifier =
          Modifier.fillMaxSize()
            .glassContentCaptureSource(screenBackdropState)
            .hazeSource(state = viewerHazeState, zIndex = 0f),
      ) {
        when {
          uiState.isLoading -> {
            Box(
              modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
              contentAlignment = Alignment.Center,
            ) {
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                CircularProgressIndicator(modifier = Modifier.size(34.dp))
                Text(
                  text = stringResource(R.string.viewer_loading_document),
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }

          uiState.kind == ViewerDocumentKind.PDF && uiState.pageCount > 0 -> {
            PdfViewerCanvas(
              pdfController = viewModel.pdfController,
              pageCount = uiState.pageCount,
              currentPage = uiState.currentPage,
              initialScrollOffsetFraction = uiState.initialScrollOffsetFraction,
              initialZoom = uiState.settledZoom,
              scrollMode = uiState.scrollMode,
              paperMode = uiState.paperMode,
              searchMatches = uiState.searchMatches,
              currentMatchIndex = uiState.currentMatchIndex,
              selection = uiState.selection,
              currentPageHasTextLayer = uiState.currentPageHasTextLayer,
              topContentPadding = topChromeDp,
              bottomContentPadding = bottomChromeDp,
              nestedScrollConnection = scrollCollapseConnection,
              jumpEvents = viewModel.jumpEvents,
              onVisiblePageChanged = viewModel::onPageVisibleChanged,
              onZoomSettled = viewModel::onZoomSettled,
              onToggleChrome = toggleChromeOnTap,
              onLongPressSelectAt = viewModel::onLongPressSelectAt,
              onDragSelectionHandle = viewModel::onDragSelectionHandle,
              onClearSelection = viewModel::clearSelection,
              onCopySelection = { viewModel.copySelection(copiedMessage) },
              onShareSelection = {
                viewModel.buildShareSelectedTextIntent()?.let { intent ->
                  runCatching { context.startActivity(Intent.createChooser(intent, shareLabel)) }
                }
              },
              onSearchSelection = { viewModel.searchSelectedText() },
              onScannedOcrChipClick = { viewModel.showStatusMessage(ocrStubMessage) },
            )
          }

          uiState.kind == ViewerDocumentKind.IMAGE && document != null -> {
            ImageDocumentViewer(
              uriString = document.uriString,
              displayName = document.displayName,
              rotationDegrees = uiState.imageRotationDegrees,
              paperMode = uiState.paperMode,
              onToggleChrome = toggleChromeOnTap,
              onOpenExternal = openExternalHandler,
            )
          }

          uiState.kind in setOf(
            ViewerDocumentKind.TEXT,
            ViewerDocumentKind.MARKDOWN,
            ViewerDocumentKind.CODE,
            ViewerDocumentKind.CSV,
            ViewerDocumentKind.JSON,
          ) -> {
            TextCodeDocumentViewer(
              kind = uiState.kind,
              rawText = uiState.textContent,
              formattedJsonText = uiState.formattedJsonContent,
              isTruncated = uiState.isTextTruncated,
              loadedBytes = uiState.loadedTextBytes,
              paperMode = uiState.paperMode,
              wrapLines = uiState.textWrapLines,
              fontSizeSp = uiState.textFontSizeSp,
              prettyPrintJson = uiState.prettyPrintJson,
              textMatches = uiState.textMatches,
              currentMatchIndex = uiState.currentTextMatchIndex,
              topContentPadding = topChromeDp,
              bottomContentPadding = bottomChromeDp,
              nestedScrollConnection = scrollCollapseConnection,
              onToggleWrap = viewModel::toggleTextWrap,
              onTogglePrettyJson = viewModel::togglePrettyPrintJson,
              onFontSizeDelta = viewModel::adjustTextFontSize,
              onToggleChrome = toggleChromeOnTap,
            )
          }

          uiState.kind == ViewerDocumentKind.ERROR -> {
            UnsupportedDocumentCard(
              document = document,
              title = stringResource(R.string.viewer_error_title),
              body = uiState.errorMessage?.takeIf { it.isNotBlank() } ?: stringResource(R.string.viewer_error_body),
              isError = true,
              onOpenExternal = openExternalHandler,
              onOpenAnnotate = onOpenEditorRoute,
            )
          }

          else -> {
            val typeLabel =
              document?.displayName?.substringAfterLast('.', "")?.uppercase()?.takeIf { it.isNotBlank() }
                ?: document?.mimeType?.substringAfter('/')?.uppercase()
                ?: "this"
            UnsupportedDocumentCard(
              document = document,
              title = stringResource(R.string.viewer_unsupported_title),
              body = stringResource(R.string.viewer_unsupported_body, typeLabel),
              isError = false,
              onOpenExternal = openExternalHandler,
              onOpenAnnotate = onOpenEditorRoute,
            )
          }
        }
      }

      // Floating Liquid Glass Top Bar (Existing Liquid Glass style, minimizes on downward scroll) + Search Bar
      Column(
        modifier =
          Modifier.align(Alignment.TopCenter)
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .onSizeChanged { topChromeHeightPx = it.height }
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        val displayTitle = document?.displayName ?: stringResource(R.string.viewer_default_title)
        val subtitleText =
          when {
            uiState.kind == ViewerDocumentKind.PDF && uiState.pageCount > 0 ->
              stringResource(R.string.viewer_page_subtitle_format, uiState.currentPage + 1, uiState.pageCount)
            document != null ->
              document.mimeType.substringAfter('/').uppercase().ifBlank { "FILE" }
            else -> stringResource(R.string.viewer_default_title)
          }
        val compactPageText =
          if (uiState.kind == ViewerDocumentKind.PDF && uiState.pageCount > 0) {
            stringResource(R.string.viewer_page_compact_format, uiState.currentPage + 1, uiState.pageCount)
          } else {
            displayTitle.take(14)
          }

        ViewerCollapsingTopBar(
          title = displayTitle,
          subtitle = subtitleText,
          compactPageText = compactPageText,
          isMinimized = isTopBarMinimized && !uiState.isSearchVisible,
          isSearchActive = uiState.isSearchVisible,
          isBookmarked = uiState.currentPageBookmarked,
          showRotateAction = uiState.kind == ViewerDocumentKind.IMAGE,
          onNavigateBack = {
            viewModel.persistReadingStateNow()
            onNavigateBack()
          },
          onExpandFromMinimized = {
            isTopBarMinimized = false
            isChromeHidden = false
          },
          onOpenOutline = {
            if (uiState.kind == ViewerDocumentKind.PDF) showOutlineSheet = true
            else showOptionsSheet = true
          },
          onToggleSearch = {
            if (uiState.isSearchVisible) {
              viewModel.closeSearch()
            } else {
              isTopBarMinimized = false
              viewModel.openSearch()
            }
          },
          onShare = shareDocumentHandler,
          onRotateImage = viewModel::rotateImage90,
          onOpenOptions = { showOptionsSheet = true },
        )

        AnimatedVisibility(
          visible = uiState.isSearchVisible,
          enter =
            if (motion) slideInVertically(spring(dampingRatio = 0.85f, stiffness = 460f)) { -it / 2 } + fadeIn(tween(150))
            else EnterTransition.None,
          exit =
            if (motion) slideOutVertically(tween(130)) { -it / 2 } + fadeOut(tween(110))
            else ExitTransition.None,
        ) {
          val matchCount =
            if (uiState.kind == ViewerDocumentKind.PDF) uiState.searchMatches.size
            else uiState.textMatches.size
          val activeIdx =
            if (uiState.kind == ViewerDocumentKind.PDF) uiState.currentMatchIndex
            else uiState.currentTextMatchIndex
          ViewerSearchGlassBar(
            query = uiState.searchQuery,
            matchCount = matchCount,
            currentMatchIndex = activeIdx,
            isSearching = uiState.isSearching,
            indexedPages = uiState.searchIndexedPages,
            totalPages = uiState.pageCount,
            onQueryChange = viewModel::onSearchQueryChange,
            onPrevMatch = { viewModel.navigateSearchMatch(-1) },
            onNextMatch = { viewModel.navigateSearchMatch(1) },
            onClose = viewModel::closeSearch,
          )
        }
      }

      // Right-edge Liquid Glass scrubber with floating page bubble
      AnimatedVisibility(
        visible = uiState.kind == ViewerDocumentKind.PDF && uiState.pageCount > 1 && !isChromeHidden,
        enter =
          if (motion) slideInHorizontally { it } + fadeIn(tween(160))
          else EnterTransition.None,
        exit =
          if (motion) slideOutHorizontally { it } + fadeOut(tween(120))
          else ExitTransition.None,
        modifier =
          Modifier.align(Alignment.CenterEnd)
            .padding(top = topChromeDp + 16.dp, bottom = bottomChromeDp + 20.dp, end = 6.dp),
      ) {
        ViewerRightEdgeScrubber(
          pageCount = uiState.pageCount,
          currentPage = uiState.currentPage,
          onSelectPage = { page -> viewModel.jumpToPage(page) },
        )
      }

      // Floating bottom Liquid Glass page-thumbnail strip
      AnimatedVisibility(
        visible = uiState.kind == ViewerDocumentKind.PDF && uiState.pageCount > 0 && !isChromeHidden,
        enter =
          if (motion) slideInVertically { it } + fadeIn(tween(180))
          else EnterTransition.None,
        exit =
          if (motion) slideOutVertically { it } + fadeOut(tween(130))
          else ExitTransition.None,
        modifier =
          Modifier.align(Alignment.BottomCenter)
            .widthIn(max = 660.dp)
            .fillMaxWidth()
            .onSizeChanged { bottomChromeHeightPx = it.height }
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 12.dp),
      ) {
        ViewerThumbnailStripBar(
          pageCount = uiState.pageCount,
          currentPage = uiState.currentPage,
          pdfController = viewModel.pdfController,
          paperMode = uiState.paperMode,
          onSelectPage = { page -> viewModel.jumpToPage(page) },
        )
      }

      // Ephemeral Liquid Glass status toast (OCR stub message / copied confirmation)
      AnimatedVisibility(
        visible = uiState.statusMessage != null,
        enter = if (motion) fadeIn(tween(160)) else EnterTransition.None,
        exit = if (motion) fadeOut(tween(160)) else ExitTransition.None,
        modifier =
          Modifier.align(Alignment.BottomCenter)
            .padding(horizontal = 24.dp, vertical = bottomChromeDp + 22.dp),
      ) {
        GlassSurface(
          cornerRadius = 20.dp,
          shape = CircleShape,
          elevation = GlassElevation.FLOATING,
          useBackdropBlur = true,
          enableRefraction = true,
          allowLiquidGlass = true,
          modifier = Modifier.testTag("viewer_status_toast"),
        ) {
          Text(
            text = uiState.statusMessage.orEmpty(),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
          )
        }
      }

      ViewerReaderOptionsSheet(
        visible = showOptionsSheet,
        detent = optionsDetent,
        onDetentChange = { optionsDetent = it },
        onDismiss = { showOptionsSheet = false },
        kind = uiState.kind,
        currentPage = uiState.currentPage,
        isBookmarked = uiState.currentPageBookmarked,
        scrollMode = uiState.scrollMode,
        paperMode = uiState.paperMode,
        hasExternalOpener = openExternalHandler != null,
        onPaperModeChange = viewModel::setPaperMode,
        onScrollModeChange = viewModel::setScrollMode,
        onToggleBookmark = viewModel::toggleCurrentPageBookmark,
        onOpenOutline = { showOutlineSheet = true },
        onOpenAnnotate = onOpenEditorRoute,
        onOpenExternal = { openExternalHandler?.invoke() },
      )

      ViewerOutlineBookmarksSheet(
        visible = showOutlineSheet,
        detent = outlineDetent,
        onDetentChange = { outlineDetent = it },
        onDismiss = { showOutlineSheet = false },
        outlineNodes = uiState.outlineNodes,
        bookmarks = uiState.bookmarks,
        currentPage = uiState.currentPage,
        currentPageBookmarked = uiState.currentPageBookmarked,
        onToggleCurrentBookmark = viewModel::toggleCurrentPageBookmark,
        onRemoveBookmark = viewModel::removeBookmark,
        onJumpToPage = { page -> viewModel.jumpToPage(page) },
      )
    }
  }
}

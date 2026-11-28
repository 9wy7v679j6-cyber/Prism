package com.prism.docs.feature.viewer

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prism.docs.core.data.model.IncomingDocumentRequest
import com.prism.docs.core.ui.glass.GlassSheetDetent
import com.prism.docs.core.ui.glass.LocalGlassMotion
import com.prism.docs.core.ui.theme.LocalGlassEnvironment
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
  val baseEnv = LocalGlassEnvironment.current
  val scope = rememberCoroutineScope()
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

  // Continuous scroll-driven collapse progress (0f = expanded solid HUD, 1f = compact docked HUD)
  val collapseAnim = remember { Animatable(0f) }
  var isTopBarMinimized by remember { mutableStateOf(false) }
  var isChromeHidden by remember { mutableStateOf(false) }
  var isActivelyScrolling by remember { mutableStateOf(false) }
  var isScrubbing by remember { mutableStateOf(false) }
  var currentZoom by remember { mutableFloatStateOf(1f) }
  var zoomResetNonce by remember { mutableIntStateOf(0) }

  // Continuous page position read strictly in graphicsLayer / drawBehind (zero recomposition during scroll)
  val continuousPageState = remember(document?.uriString) { mutableFloatStateOf(0f) }
  LaunchedEffect(uiState.currentPage) {
    if (!isActivelyScrolling && !isScrubbing) {
      continuousPageState.floatValue = uiState.currentPage.toFloat()
    }
  }

  var showOptionsSheet by remember { mutableStateOf(false) }
  var optionsDetent by remember { mutableStateOf(GlassSheetDetent.HALF) }
  var showOutlineSheet by remember { mutableStateOf(false) }
  var outlineDetent by remember { mutableStateOf(GlassSheetDetent.HALF) }

  var isActivelyDragging by remember { mutableStateOf(false) }

  // Smooth spring handoff whenever target isTopBarMinimized changes outside active drag
  LaunchedEffect(isTopBarMinimized, uiState.isSearchVisible) {
    if (isActivelyDragging) return@LaunchedEffect
    val target = if (isTopBarMinimized && !uiState.isSearchVisible) 1f else 0f
    if (!motion) {
      collapseAnim.snapTo(target)
    } else {
      collapseAnim.animateTo(
        targetValue = target,
        animationSpec = spring(dampingRatio = 0.80f, stiffness = 420f),
      )
    }
  }

  // Continuous NestedScrollConnection:
  // Scrolling down scrubs collapseProgress live 0f -> 1f (morphing top bar & tucking bottom strip);
  // scrolling up scrubs back to 0f and smoothly unfurls all bars interactively.
  val collapseSpanPx = with(density) { 88.dp.toPx().coerceAtLeast(1f) }
  val scrollCollapseConnection =
    remember(collapseSpanPx, motion) {
      object : NestedScrollConnection {
        override fun onPostScroll(
          consumed: Offset,
          available: Offset,
          source: NestedScrollSource,
        ): Offset {
          if (source == NestedScrollSource.UserInput && abs(consumed.y) > 0.5f) {
            isActivelyDragging = true
            val deltaProgress = (-consumed.y) / collapseSpanPx
            val next = (collapseAnim.value + deltaProgress).coerceIn(0f, 1f)
            scope.launch { collapseAnim.snapTo(next) }
            if (deltaProgress > 0f && next > 0.45f) {
              if (!isTopBarMinimized) isTopBarMinimized = true
              if (!isChromeHidden) isChromeHidden = true
            } else if (deltaProgress < 0f && next < 0.55f) {
              if (isTopBarMinimized) isTopBarMinimized = false
              if (isChromeHidden) isChromeHidden = false
            }
          }
          return Offset.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
          isActivelyDragging = false
          val settledMin =
            when {
              consumed.y < -350f -> true
              consumed.y > 350f -> false
              else -> collapseAnim.value >= 0.5f
            }
          isTopBarMinimized = settledMin
          isChromeHidden = settledMin
          val target = if (settledMin && !uiState.isSearchVisible) 1f else 0f
          if (motion) {
            collapseAnim.animateTo(target, spring(dampingRatio = 0.80f, stiffness = 420f))
          } else {
            collapseAnim.snapTo(target)
          }
          return Velocity.Zero
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

  // Keep PDF scroll container padding stable so collapsing bars never causes layout jump
  val stableTopPaddingDp = statusBarTop + if (uiState.isSearchVisible) 134.dp else 72.dp
  val measuredBottomStripDp =
    if (bottomChromeHeightPx > 0) with(density) { bottomChromeHeightPx.toDp() }
    else navBarBottom + 96.dp
  val stableBottomPaddingDp =
    if (uiState.kind == ViewerDocumentKind.PDF) measuredBottomStripDp else navBarBottom + 16.dp

  val topOverlaySpaceDp =
    if (topChromeHeightPx > 0) with(density) { topChromeHeightPx.toDp() }
    else stableTopPaddingDp
  val bottomOverlayOffset by
    animateDpAsState(
      targetValue =
        if (uiState.kind == ViewerDocumentKind.PDF && (!isChromeHidden || isScrubbing) && uiState.selection == null) {
          measuredBottomStripDp
        } else {
          navBarBottom + 16.dp
        },
      animationSpec = if (motion) spring(dampingRatio = 0.82f, stiffness = 420f) else snap(),
      label = "viewer_bottom_overlay_offset",
    )

  // Synchronized bottom thumbnail strip reveal (rises smoothly when scrubbing, tucks when selecting)
  val scrubberStripPull by
    animateFloatAsState(
      targetValue = if (isScrubbing) 1f else 0f,
      animationSpec = if (motion) spring(dampingRatio = 0.82f, stiffness = 420f) else snap(),
      label = "viewer_scrubber_strip_pull",
    )
  val selectionStripHide by
    animateFloatAsState(
      targetValue = if (uiState.selection != null) 1f else 0f,
      animationSpec = if (motion) spring(dampingRatio = 0.82f, stiffness = 440f) else snap(),
      label = "viewer_selection_strip_hide",
    )

  val currentChapter =
    remember(uiState.outlineNodes, uiState.currentPage) {
      uiState.outlineNodes.lastOrNull { it.pageIndex <= uiState.currentPage }?.title
    }
  val bookmarkedPages =
    remember(uiState.bookmarks) {
      uiState.bookmarks.mapTo(HashSet()) { it.pageIndex }
    }

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

  // Enforce 100% opaque solid surfaces across all shared :core-ui sheets/controls inside the viewer
  val solidViewerEnv = remember(baseEnv) { baseEnv.copy(reduceTransparency = true) }

  CompositionLocalProvider(LocalGlassEnvironment provides solidViewerEnv) {
    Box(modifier = modifier.fillMaxSize()) {
      // Document viewport (opaque paper/surface for reading)
      Box(modifier = Modifier.fillMaxSize()) {
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
              zoomResetNonce = zoomResetNonce,
              isScrubbing = isScrubbing,
              scrollMode = uiState.scrollMode,
              paperMode = uiState.paperMode,
              bookmarkedPages = bookmarkedPages,
              searchMatches = uiState.searchMatches,
              currentMatchIndex = uiState.currentMatchIndex,
              selection = uiState.selection,
              currentPageHasTextLayer = uiState.currentPageHasTextLayer,
              topContentPadding = stableTopPaddingDp,
              bottomContentPadding = stableBottomPaddingDp,
              bottomOverlayOffset = bottomOverlayOffset,
              continuousPageState = continuousPageState,
              nestedScrollConnection = scrollCollapseConnection,
              jumpEvents = viewModel.jumpEvents,
              onVisiblePageChanged = viewModel::onPageVisibleChanged,
              onLiveZoomChanged = { z -> currentZoom = z },
              onZoomSettled = viewModel::onZoomSettled,
              onActiveScrollChanged = { scrolling -> isActivelyScrolling = scrolling },
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
              topContentPadding = stableTopPaddingDp,
              bottomContentPadding = stableBottomPaddingDp,
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

      // Floating Solid Top Bar (scroll-choreographed morphing) + Solid Search Bar
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
          pageCount = uiState.pageCount,
          currentPage = uiState.currentPage,
          collapseProgress = if (uiState.isSearchVisible) 0f else collapseAnim.value,
          continuousPageState = continuousPageState,
          currentChapter = currentChapter,
          currentZoom = currentZoom,
          isActivelyScrolling = isActivelyScrolling || isScrubbing,
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
              isChromeHidden = false
              viewModel.openSearch()
            }
          },
          onShare = shareDocumentHandler,
          onRotateImage = viewModel::rotateImage90,
          onOpenOptions = { showOptionsSheet = true },
          onResetZoom = { zoomResetNonce++ },
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
          ViewerSearchSolidBar(
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

      // Right-edge solid scrubber stays visible while chrome is open, actively scrolling, or scrubbing
      AnimatedVisibility(
        visible = uiState.kind == ViewerDocumentKind.PDF && uiState.pageCount > 1 &&
          (!isChromeHidden || isActivelyScrolling || isScrubbing),
        enter =
          if (motion) slideInHorizontally(spring(dampingRatio = 0.80f, stiffness = 440f)) { it } + fadeIn(tween(140))
          else EnterTransition.None,
        exit =
          if (motion) slideOutHorizontally(tween(180)) { it / 2 } + fadeOut(tween(160))
          else ExitTransition.None,
        modifier =
          Modifier.align(Alignment.CenterEnd)
            .padding(top = topOverlaySpaceDp + 12.dp, bottom = bottomOverlayOffset + 16.dp, end = 6.dp),
      ) {
        ViewerRightEdgeScrubber(
          pageCount = uiState.pageCount,
          currentPage = uiState.currentPage,
          continuousPageState = continuousPageState,
          pdfController = viewModel.pdfController,
          paperMode = uiState.paperMode,
          currentChapter = currentChapter,
          isActivelyScrolling = isActivelyScrolling,
          isBookmarked = uiState.currentPageBookmarked,
          onSelectPage = { page -> viewModel.jumpToPage(page) },
          onScrubbingChanged = { scrubbing -> isScrubbing = scrubbing },
        )
      }

      // Floating solid bottom page-thumbnail strip — glides smoothly with scroll collapse & springs up when scrubbing
      if (uiState.kind == ViewerDocumentKind.PDF && uiState.pageCount > 0) {
        val baseReveal = (1f - collapseAnim.value).coerceIn(0f, 1f)
        val stripReveal = (maxOf(baseReveal, scrubberStripPull) * (1f - selectionStripHide)).coerceIn(0f, 1f)
        if (stripReveal > 0.02f) {
          Box(
            modifier =
              Modifier.align(Alignment.BottomCenter)
                .widthIn(max = 660.dp)
                .fillMaxWidth()
                .onSizeChanged { bottomChromeHeightPx = it.height }
                .graphicsLayer {
                  val hideOffset = (bottomChromeHeightPx.toFloat().takeIf { it > 0f } ?: 260f) + 28.dp.toPx()
                  translationY = (1f - stripReveal) * hideOffset
                  alpha = (stripReveal * 1.15f).coerceIn(0f, 1f)
                  val s = 0.94f + 0.06f * stripReveal
                  scaleX = s
                  scaleY = s
                }
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 12.dp),
          ) {
            ViewerThumbnailStripBar(
              pageCount = uiState.pageCount,
              currentPage = uiState.currentPage,
              bookmarkedPages = bookmarkedPages,
              pdfController = viewModel.pdfController,
              paperMode = uiState.paperMode,
              isScrubbing = isScrubbing,
              onSelectPage = { page -> viewModel.jumpToPage(page) },
            )
          }
        }
      }

      // Solid high-contrast status toast (OCR stub message / copied confirmation)
      AnimatedVisibility(
        visible = uiState.statusMessage != null,
        enter =
          if (motion) slideInVertically(spring(dampingRatio = 0.82f, stiffness = 440f)) { it / 2 } + fadeIn(tween(150))
          else EnterTransition.None,
        exit =
          if (motion) slideOutVertically(tween(130)) { it / 2 } + fadeOut(tween(130))
          else ExitTransition.None,
        modifier =
          Modifier.align(Alignment.BottomCenter)
            .padding(horizontal = 24.dp, vertical = bottomOverlayOffset + 14.dp),
      ) {
        ViewerSolidSurface(
          cornerRadius = 20.dp,
          shape = CircleShape,
          emphasized = true,
          shadowElevation = 8.dp,
          modifier = Modifier.testTag("viewer_status_toast"),
        ) {
          Text(
            text = uiState.statusMessage.orEmpty(),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
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

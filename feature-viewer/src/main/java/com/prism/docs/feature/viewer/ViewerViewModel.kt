package com.prism.docs.feature.viewer

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.prism.docs.core.data.db.DocumentBookmarkEntity
import com.prism.docs.core.data.model.IncomingDocumentRequest
import com.prism.docs.core.data.viewer.ViewerStateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ViewerUiState(
  val document: IncomingDocumentRequest? = null,
  val kind: ViewerDocumentKind = ViewerDocumentKind.UNSUPPORTED,
  val isLoading: Boolean = true,
  val errorMessage: String? = null,
  val pageCount: Int = 0,
  val currentPage: Int = 0,
  val initialScrollOffsetFraction: Float = 0f,
  val settledZoom: Float = 1f,
  val scrollMode: PdfScrollMode = PdfScrollMode.CONTINUOUS,
  val paperMode: ReaderPaperMode = ReaderPaperMode.LIGHT,
  val outlineNodes: List<PdfOutlineNode> = emptyList(),
  val bookmarks: List<DocumentBookmarkEntity> = emptyList(),
  val isSearchVisible: Boolean = false,
  val searchQuery: String = "",
  val isSearching: Boolean = false,
  val searchIndexedPages: Int = 0,
  val searchMatches: List<PdfSearchMatch> = emptyList(),
  val currentMatchIndex: Int = -1,
  val textMatches: List<IntRange> = emptyList(),
  val currentTextMatchIndex: Int = -1,
  val currentPageHasTextLayer: Boolean? = null,
  val selection: TextSelectionRange? = null,
  val textContent: String = "",
  val formattedJsonContent: String? = null,
  val isTextTruncated: Boolean = false,
  val loadedTextBytes: Int = 0,
  val textWrapLines: Boolean = true,
  val textFontSizeSp: Float = 14f,
  val prettyPrintJson: Boolean = true,
  val imageRotationDegrees: Int = 0,
  val statusMessage: String? = null,
) {
  val currentPageBookmarked: Boolean
    get() = bookmarks.any { it.pageIndex == currentPage }
}

sealed interface ViewerJumpEvent {
  data class JumpToPage(
    val pageIndex: Int,
    val yFraction: Float? = null,
    val xFraction: Float? = null,
  ) : ViewerJumpEvent
}

@OptIn(FlowPreview::class)
@HiltViewModel
class ViewerViewModel
@Inject
constructor(
  application: Application,
  private val viewerStateRepository: ViewerStateRepository,
) : AndroidViewModel(application) {

  private val appContext: Context = application.applicationContext
  val pdfController = PdfDocumentController(appContext)
  val textExtractor = PdfTextAndOutlineExtractor(appContext)

  private val _uiState = MutableStateFlow(ViewerUiState())
  val uiState: StateFlow<ViewerUiState> = _uiState.asStateFlow()

  private val _jumpEvents = MutableSharedFlow<ViewerJumpEvent>(extraBufferCapacity = 8)
  val jumpEvents: SharedFlow<ViewerJumpEvent> = _jumpEvents.asSharedFlow()

  private val searchQueryInput = MutableStateFlow("")
  private val persistPositionSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 16)

  private var openJob: Job? = null
  private var bookmarkObserveJob: Job? = null
  private var searchJob: Job? = null
  private var pageTextInspectJob: Job? = null
  private var currentUriKey: String = ""
  private var latestOffsetFraction: Float = 0f

  init {
    viewModelScope.launch {
      searchQueryInput
        .debounce(250L)
        .distinctUntilChanged()
        .collectLatest { debounced ->
          executeSearch(debounced, jumpToFirst = debounced.isNotBlank())
        }
    }

    viewModelScope.launch {
      persistPositionSignal
        .debounce(400L)
        .collectLatest {
          persistReadingStateNow()
        }
    }
  }

  fun openDocument(request: IncomingDocumentRequest?) {
    if (request == null) {
      currentUriKey = ""
      _uiState.value =
        ViewerUiState(
          document = null,
          kind = ViewerDocumentKind.UNSUPPORTED,
          isLoading = false,
        )
      return
    }

    if (currentUriKey == request.uriString && !_uiState.value.isLoading && _uiState.value.kind != ViewerDocumentKind.ERROR) {
      val queryFromSearch = request.initialSearchQuery?.trim().orEmpty()
      if (queryFromSearch.isNotEmpty() && queryFromSearch != _uiState.value.searchQuery) {
        openSearchWithQuery(queryFromSearch)
      }
      return
    }

    openJob?.cancel()
    searchJob?.cancel()
    bookmarkObserveJob?.cancel()
    pageTextInspectJob?.cancel()

    currentUriKey = request.uriString
    val kind = resolveViewerKind(request.displayName, request.mimeType)
    val initialQuery = request.initialSearchQuery?.trim().orEmpty()

    _uiState.value =
      ViewerUiState(
        document = request,
        kind = kind,
        isLoading = kind != ViewerDocumentKind.UNSUPPORTED,
        isSearchVisible = initialQuery.isNotEmpty(),
        searchQuery = initialQuery,
      )

    bookmarkObserveJob =
      viewModelScope.launch {
        viewerStateRepository.observeBookmarks(request.uriString).collectLatest { list ->
          _uiState.update { it.copy(bookmarks = list) }
        }
      }

    openJob =
      viewModelScope.launch {
        val savedState = runCatching { viewerStateRepository.getReadingState(request.uriString) }.getOrNull()
        val restoredScrollMode =
          savedState?.scrollMode?.let { name ->
            runCatching { PdfScrollMode.valueOf(name) }.getOrNull()
          } ?: PdfScrollMode.CONTINUOUS
        val restoredPaperMode =
          savedState?.paperMode?.let { name ->
            runCatching { ReaderPaperMode.valueOf(name) }.getOrNull()
          } ?: ReaderPaperMode.LIGHT

        when (kind) {
          ViewerDocumentKind.PDF -> {
            val openResult = pdfController.open(request.uriString)
            if (openResult.isFailure) {
              _uiState.update {
                it.copy(
                  kind = ViewerDocumentKind.ERROR,
                  isLoading = false,
                  errorMessage = openResult.exceptionOrNull()?.message,
                )
              }
              return@launch
            }
            val totalPages = openResult.getOrDefault(0)
            val startPage =
              when {
                request.initialPage in 0 until totalPages -> request.initialPage
                savedState != null -> savedState.lastPage.coerceIn(0, (totalPages - 1).coerceAtLeast(0))
                else -> 0
              }
            val startZoom = savedState?.zoomScale?.coerceIn(1f, 5f) ?: 1f
            val startOffset = savedState?.scrollOffsetFraction?.coerceIn(0f, 1f) ?: 0f
            latestOffsetFraction = startOffset

            _uiState.update {
              it.copy(
                kind = ViewerDocumentKind.PDF,
                isLoading = false,
                pageCount = totalPages,
                currentPage = startPage,
                initialScrollOffsetFraction = startOffset,
                settledZoom = startZoom,
                scrollMode = restoredScrollMode,
                paperMode = restoredPaperMode,
              )
            }

            if (startPage > 0) {
              _jumpEvents.tryEmit(ViewerJumpEvent.JumpToPage(startPage, yFraction = startOffset))
            }

            // Open PdfBox document on IO for lazy search, outline, and text selection
            launch(Dispatchers.IO) {
              val extractorOpen = textExtractor.open(request.uriString, request.sizeBytes)
              if (extractorOpen.isSuccess) {
                val outline = textExtractor.extractOutline()
                _uiState.update { it.copy(outlineNodes = outline) }
                inspectCurrentPageTextLayer(startPage)
                if (initialQuery.isNotEmpty()) {
                  searchQueryInput.value = initialQuery
                  executeSearch(initialQuery, jumpToFirst = true)
                }
              } else {
                _uiState.update { it.copy(currentPageHasTextLayer = null) }
              }
            }
          }

          ViewerDocumentKind.IMAGE -> {
            _uiState.update {
              it.copy(
                kind = ViewerDocumentKind.IMAGE,
                isLoading = false,
                paperMode = restoredPaperMode,
                settledZoom = savedState?.zoomScale?.coerceIn(1f, 5f) ?: 1f,
              )
            }
          }

          ViewerDocumentKind.TEXT,
          ViewerDocumentKind.MARKDOWN,
          ViewerDocumentKind.CODE,
          ViewerDocumentKind.CSV,
          ViewerDocumentKind.JSON -> {
            val loaded = NonPdfDocumentLoader.loadTextDocument(appContext, request.uriString, kind)
            if (loaded.isFailure) {
              _uiState.update {
                it.copy(
                  kind = ViewerDocumentKind.ERROR,
                  isLoading = false,
                  errorMessage = loaded.exceptionOrNull()?.message,
                )
              }
              return@launch
            }
            val data = loaded.getOrThrow()
            _uiState.update {
              it.copy(
                kind = kind,
                isLoading = false,
                paperMode = restoredPaperMode,
                textContent = data.rawText,
                formattedJsonContent = data.formattedJsonText,
                isTextTruncated = data.isTruncated,
                loadedTextBytes = data.loadedBytes,
              )
            }
            if (initialQuery.isNotEmpty()) {
              searchQueryInput.value = initialQuery
              executeSearch(initialQuery, jumpToFirst = true)
            }
          }

          ViewerDocumentKind.UNSUPPORTED,
          ViewerDocumentKind.ERROR -> {
            _uiState.update { it.copy(isLoading = false) }
          }
        }
      }
  }

  fun onPageVisibleChanged(pageIndex: Int, scrollOffsetFraction: Float = 0f) {
    val total = _uiState.value.pageCount
    if (total <= 0) return
    val clamped = pageIndex.coerceIn(0, total - 1)
    latestOffsetFraction = scrollOffsetFraction.coerceIn(0f, 1f)
    if (_uiState.value.currentPage != clamped) {
      _uiState.update { it.copy(currentPage = clamped, selection = null) }
      inspectCurrentPageTextLayer(clamped)
    }
    persistPositionSignal.tryEmit(Unit)
  }

  fun jumpToPage(pageIndex: Int, yFraction: Float? = null, xFraction: Float? = null) {
    val total = _uiState.value.pageCount
    if (total <= 0) return
    val clamped = pageIndex.coerceIn(0, total - 1)
    latestOffsetFraction = yFraction?.coerceIn(0f, 1f) ?: 0f
    _uiState.update { it.copy(currentPage = clamped, selection = null) }
    inspectCurrentPageTextLayer(clamped)
    _jumpEvents.tryEmit(ViewerJumpEvent.JumpToPage(clamped, yFraction = yFraction, xFraction = xFraction))
    persistPositionSignal.tryEmit(Unit)
  }

  fun onZoomSettled(newZoom: Float) {
    val clamped = newZoom.coerceIn(1f, 5f)
    if (_uiState.value.settledZoom != clamped) {
      _uiState.update { it.copy(settledZoom = clamped) }
      persistPositionSignal.tryEmit(Unit)
    }
  }

  fun setScrollMode(mode: PdfScrollMode) {
    if (_uiState.value.scrollMode != mode) {
      val page = _uiState.value.currentPage
      _uiState.update { it.copy(scrollMode = mode) }
      _jumpEvents.tryEmit(ViewerJumpEvent.JumpToPage(page))
      persistPositionSignal.tryEmit(Unit)
    }
  }

  fun setPaperMode(mode: ReaderPaperMode) {
    if (_uiState.value.paperMode != mode) {
      _uiState.update { it.copy(paperMode = mode) }
      persistPositionSignal.tryEmit(Unit)
    }
  }

  fun toggleCurrentPageBookmark() {
    val uri = currentUriKey
    if (uri.isEmpty() || _uiState.value.kind != ViewerDocumentKind.PDF) return
    val page = _uiState.value.currentPage
    viewModelScope.launch {
      viewerStateRepository.toggleBookmark(
        uriString = uri,
        pageIndex = page,
        defaultLabel = "Page ${page + 1}",
      )
    }
  }

  fun removeBookmark(id: Long) {
    viewModelScope.launch {
      viewerStateRepository.removeBookmarkById(id)
    }
  }

  fun openSearch() {
    _uiState.update { it.copy(isSearchVisible = true) }
  }

  fun openSearchWithQuery(query: String) {
    val trimmed = query.trim()
    _uiState.update { it.copy(isSearchVisible = true, searchQuery = trimmed) }
    searchQueryInput.value = trimmed
    if (trimmed.isNotEmpty()) {
      executeSearch(trimmed, jumpToFirst = true)
    }
  }

  fun closeSearch() {
    searchJob?.cancel()
    searchQueryInput.value = ""
    _uiState.update {
      it.copy(
        isSearchVisible = false,
        searchQuery = "",
        isSearching = false,
        searchMatches = emptyList(),
        currentMatchIndex = -1,
        textMatches = emptyList(),
        currentTextMatchIndex = -1,
      )
    }
  }

  fun onSearchQueryChange(query: String) {
    _uiState.update { it.copy(searchQuery = query) }
    searchQueryInput.value = query
  }

  fun navigateSearchMatch(delta: Int) {
    val state = _uiState.value
    if (state.kind == ViewerDocumentKind.PDF) {
      val matches = state.searchMatches
      if (matches.isEmpty()) return
      val nextIdx =
        if (state.currentMatchIndex < 0) 0
        else (state.currentMatchIndex + delta).mod(matches.size)
      _uiState.update { it.copy(currentMatchIndex = nextIdx) }
      val hit = matches[nextIdx]
      val anchorRect = hit.rects.firstOrNull()
      jumpToPage(
        pageIndex = hit.pageIndex,
        yFraction = anchorRect?.let { ((it.top + it.bottom) * 0.5f - 0.15f).coerceIn(0f, 1f) },
        xFraction = anchorRect?.let { (it.left + it.right) * 0.5f },
      )
    } else {
      val matches = state.textMatches
      if (matches.isEmpty()) return
      val nextIdx =
        if (state.currentTextMatchIndex < 0) 0
        else (state.currentTextMatchIndex + delta).mod(matches.size)
      _uiState.update { it.copy(currentTextMatchIndex = nextIdx) }
    }
  }

  private fun executeSearch(query: String, jumpToFirst: Boolean) {
    searchJob?.cancel()
    val state = _uiState.value
    val trimmed = query.trim()
    if (trimmed.isEmpty()) {
      _uiState.update {
        it.copy(
          isSearching = false,
          searchIndexedPages = 0,
          searchMatches = emptyList(),
          currentMatchIndex = -1,
          textMatches = emptyList(),
          currentTextMatchIndex = -1,
        )
      }
      return
    }

    if (state.kind == ViewerDocumentKind.PDF) {
      val totalPages = state.pageCount
      if (totalPages <= 0) return
      searchJob =
        viewModelScope.launch(Dispatchers.Default) {
          _uiState.update {
            it.copy(
              isSearching = true,
              searchIndexedPages = 0,
              searchMatches = emptyList(),
              currentMatchIndex = -1,
            )
          }
          val allMatches = ArrayList<PdfSearchMatch>()
          var jumped = false
          for (pageIdx in 0 until totalPages) {
            try {
              val pageData = textExtractor.getOrExtractPageText(pageIdx)
              if (pageData != null && pageData.hasTextLayer) {
                val pageHits = PdfSearchMatcher.findMatchesInPage(pageData, trimmed)
                if (pageHits.isNotEmpty()) {
                  allMatches.addAll(pageHits)
                  val shouldJumpNow = jumpToFirst && !jumped
                  if (shouldJumpNow) jumped = true
                  val currentIdx = if (_uiState.value.currentMatchIndex < 0) 0 else _uiState.value.currentMatchIndex
                  _uiState.update {
                    it.copy(
                      searchMatches = allMatches.toList(),
                      currentMatchIndex = currentIdx,
                    )
                  }
                  if (shouldJumpNow) {
                    val firstHit = pageHits.first()
                    val anchor = firstHit.rects.firstOrNull()
                    jumpToPage(
                      pageIndex = firstHit.pageIndex,
                      yFraction = anchor?.let { ((it.top + it.bottom) * 0.5f - 0.15f).coerceIn(0f, 1f) },
                      xFraction = anchor?.let { (it.left + it.right) * 0.5f },
                    )
                  }
                }
              }
              _uiState.update { it.copy(searchIndexedPages = pageIdx + 1) }
            } catch (ce: CancellationException) {
              throw ce
            } catch (_: Throwable) {
              // Skip unreadable page without breaking search
            }
          }
          _uiState.update {
            it.copy(
              isSearching = false,
              searchIndexedPages = totalPages,
              searchMatches = allMatches,
              currentMatchIndex = if (allMatches.isEmpty()) -1 else it.currentMatchIndex.coerceIn(0, allMatches.lastIndex),
            )
          }
        }
    } else if (
      state.kind in setOf(
        ViewerDocumentKind.TEXT,
        ViewerDocumentKind.MARKDOWN,
        ViewerDocumentKind.CODE,
        ViewerDocumentKind.CSV,
        ViewerDocumentKind.JSON,
      )
    ) {
      searchJob =
        viewModelScope.launch(Dispatchers.Default) {
          val activeText =
            if (state.kind == ViewerDocumentKind.JSON && state.prettyPrintJson && state.formattedJsonContent != null) {
              state.formattedJsonContent
            } else {
              state.textContent
            }
          val hits = PdfSearchMatcher.findPlainTextMatches(activeText, trimmed)
          _uiState.update {
            it.copy(
              isSearching = false,
              textMatches = hits,
              currentTextMatchIndex = if (hits.isEmpty()) -1 else 0,
            )
          }
        }
    }
  }

  private fun inspectCurrentPageTextLayer(pageIndex: Int) {
    pageTextInspectJob?.cancel()
    pageTextInspectJob =
      viewModelScope.launch(Dispatchers.Default) {
        val data = textExtractor.getOrExtractPageText(pageIndex)
        if (_uiState.value.currentPage == pageIndex) {
          _uiState.update { it.copy(currentPageHasTextLayer = data?.hasTextLayer) }
        }
      }
  }

  fun onLongPressSelectAt(pageIndex: Int, xNorm: Float, yNorm: Float) {
    viewModelScope.launch(Dispatchers.Default) {
      val pageData = textExtractor.getOrExtractPageText(pageIndex) ?: return@launch
      if (!pageData.hasTextLayer) return@launch
      val hitChar = PdfSearchMatcher.findNearestCharIndex(pageData, xNorm, yNorm) ?: return@launch
      val wordRange = PdfSearchMatcher.expandToWordRange(pageData.rawText, hitChar)
      val selection = PdfSearchMatcher.buildSelectionRange(pageData, wordRange.first, wordRange.last)
      _uiState.update { it.copy(selection = selection) }
    }
  }

  fun onDragSelectionHandle(isStartHandle: Boolean, pageIndex: Int, xNorm: Float, yNorm: Float) {
    val existing = _uiState.value.selection ?: return
    if (existing.pageIndex != pageIndex) return
    val pageData = textExtractor.peekCachedPageText(pageIndex) ?: return
    val hitChar = PdfSearchMatcher.findNearestCharIndex(pageData, xNorm, yNorm, maxDistanceNorm = 0.25f) ?: return
    val updated =
      if (isStartHandle) {
        PdfSearchMatcher.buildSelectionRange(pageData, hitChar, existing.endIndex)
      } else {
        PdfSearchMatcher.buildSelectionRange(pageData, existing.startIndex, hitChar)
      } ?: return
    _uiState.update { it.copy(selection = updated) }
  }

  fun clearSelection() {
    if (_uiState.value.selection != null) {
      _uiState.update { it.copy(selection = null) }
    }
  }

  fun copySelection(copiedMessage: String) {
    val selected = _uiState.value.selection?.selectedText ?: return
    val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("Prism Selection", selected))
    showStatusMessage(copiedMessage)
  }

  fun buildShareSelectedTextIntent(): Intent? {
    val selected = _uiState.value.selection?.selectedText ?: return null
    return Intent(Intent.ACTION_SEND).apply {
      type = "text/plain"
      putExtra(Intent.EXTRA_TEXT, selected)
    }
  }

  fun searchSelectedText() {
    val selected = _uiState.value.selection?.selectedText?.take(80) ?: return
    clearSelection()
    openSearchWithQuery(selected)
  }

  fun rotateImage90() {
    _uiState.update { it.copy(imageRotationDegrees = (it.imageRotationDegrees + 90) % 360) }
  }

  fun toggleTextWrap() {
    _uiState.update { it.copy(textWrapLines = !it.textWrapLines) }
  }

  fun togglePrettyPrintJson() {
    val prev = _uiState.value
    val nextPretty = !prev.prettyPrintJson
    _uiState.update { it.copy(prettyPrintJson = nextPretty) }
    if (prev.isSearchVisible && prev.searchQuery.isNotBlank()) {
      executeSearch(prev.searchQuery, jumpToFirst = false)
    }
  }

  fun adjustTextFontSize(deltaSp: Float) {
    _uiState.update {
      it.copy(textFontSizeSp = (it.textFontSizeSp + deltaSp).coerceIn(11f, 24f))
    }
  }

  fun showStatusMessage(message: String) {
    _uiState.update { it.copy(statusMessage = message) }
  }

  fun clearStatusMessage() {
    _uiState.update { it.copy(statusMessage = null) }
  }

  fun buildShareDocumentIntent(): Intent? {
    val doc = _uiState.value.document ?: return null
    return runCatching {
      val parsed = Uri.parse(doc.uriString)
      val shareUri =
        when (parsed.scheme?.lowercase()) {
          null, "", "file" -> {
            val file = File(parsed.path ?: doc.uriString)
            if (!file.exists()) return null
            FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)
          }
          else -> parsed
        }
      Intent(Intent.ACTION_SEND).apply {
        type = doc.mimeType.ifBlank { "*/*" }
        putExtra(Intent.EXTRA_STREAM, shareUri)
        putExtra(Intent.EXTRA_SUBJECT, doc.displayName)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
    }.getOrNull()
  }

  fun persistReadingStateNow() {
    val doc = _uiState.value.document ?: return
    val state = _uiState.value
    viewModelScope.launch(Dispatchers.IO) {
      runCatching {
        viewerStateRepository.saveReadingState(
          uriString = doc.uriString,
          lastPage = state.currentPage,
          zoomScale = state.settledZoom,
          scrollOffsetFraction = latestOffsetFraction,
          scrollMode = state.scrollMode.name,
          paperMode = state.paperMode.name,
        )
      }
    }
  }

  override fun onCleared() {
    super.onCleared()
    openJob?.cancel()
    searchJob?.cancel()
    bookmarkObserveJob?.cancel()
    pageTextInspectJob?.cancel()
    pdfController.close()
    textExtractor.close()
  }
}

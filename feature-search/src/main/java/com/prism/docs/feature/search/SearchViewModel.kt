package com.prism.docs.feature.search

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.prism.docs.core.data.db.IndexedDocumentEntity
import com.prism.docs.core.data.db.TagEntity
import com.prism.docs.core.data.model.DocumentCategory
import com.prism.docs.core.data.model.FileListQuery
import com.prism.docs.core.data.model.FileSortField
import com.prism.docs.core.data.model.SortDirection
import com.prism.docs.core.data.paging.DocumentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SearchQuickFilterId {
  ALL,
  DOCS,
  FAVORITES,
  RECENTS,
  THIS_WEEK,
  PDF,
  DOCUMENTS,
  SPREADSHEETS,
  SLIDES,
  IMAGES,
  CODE_TEXT,
  EBOOKS,
}

@Immutable
data class SearchUiState(
  val queryText: String = "",
  val documentsOnly: Boolean = false,
  val selectedCategory: DocumentCategory? = null,
  val favoritesOnly: Boolean = false,
  val recentsOnly: Boolean = false,
  val thisWeekOnly: Boolean = false,
  val selectedTagId: Long? = null,
  val tags: List<TagEntity> = emptyList(),
  val sortField: FileSortField = FileSortField.DATE_MODIFIED,
) {
  val hasActiveFilters: Boolean
    get() =
      documentsOnly ||
        selectedCategory != null ||
        favoritesOnly ||
        recentsOnly ||
        thisWeekOnly ||
        selectedTagId != null
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class SearchViewModel
@Inject
constructor(
  private val documentRepository: DocumentRepository,
) : ViewModel() {

  private val filterStateFlow = MutableStateFlow(SearchUiState())

  val uiState: StateFlow<SearchUiState> =
    combine(filterStateFlow, documentRepository.observeTags()) { state, tags ->
        state.copy(tags = tags)
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = SearchUiState(),
      )

  val pagedResults: Flow<PagingData<IndexedDocumentEntity>> =
    combine(
        filterStateFlow
          .map { it.copy(queryText = "") }
          .distinctUntilChanged(),
        filterStateFlow
          .map { it.queryText }
          .distinctUntilChanged()
          .debounce { text -> if (text.isBlank()) 0L else 150L },
      ) { stateWithoutText, debouncedText ->
        val modifiedAfter =
          if (stateWithoutText.thisWeekOnly) {
            System.currentTimeMillis() - SEVEN_DAYS_MS
          } else {
            null
          }
        FileListQuery(
          category = stateWithoutText.selectedCategory,
          documentsOnly = stateWithoutText.documentsOnly,
          tagId = stateWithoutText.selectedTagId,
          favoritesOnly = stateWithoutText.favoritesOnly,
          recentsOnly = stateWithoutText.recentsOnly,
          namePrefix = debouncedText.trim().ifBlank { null },
          modifiedAfterEpochMs = modifiedAfter,
          sortField = stateWithoutText.sortField,
          sortDirection =
            if (stateWithoutText.sortField == FileSortField.NAME) SortDirection.ASC
            else SortDirection.DESC,
        )
      }
      .distinctUntilChanged()
      .flatMapLatest { query -> documentRepository.pagedDocuments(query) }
      .cachedIn(viewModelScope)

  fun setQuery(query: String) {
    filterStateFlow.update { it.copy(queryText = query) }
  }

  fun selectQuickFilter(filterId: SearchQuickFilterId) {
    filterStateFlow.update { current ->
      when (filterId) {
        SearchQuickFilterId.ALL ->
          current.copy(
            documentsOnly = false,
            selectedCategory = null,
            favoritesOnly = false,
            recentsOnly = false,
            thisWeekOnly = false,
            selectedTagId = null,
          )
        SearchQuickFilterId.DOCS ->
          current.copy(
            documentsOnly = !(current.documentsOnly && current.selectedCategory == null),
            selectedCategory = null,
          )
        SearchQuickFilterId.FAVORITES ->
          current.copy(favoritesOnly = !current.favoritesOnly, recentsOnly = false)
        SearchQuickFilterId.RECENTS ->
          current.copy(recentsOnly = !current.recentsOnly, favoritesOnly = false)
        SearchQuickFilterId.THIS_WEEK ->
          current.copy(thisWeekOnly = !current.thisWeekOnly)
        SearchQuickFilterId.PDF -> toggleCategory(current, DocumentCategory.PDF)
        SearchQuickFilterId.DOCUMENTS -> toggleCategory(current, DocumentCategory.DOCUMENTS)
        SearchQuickFilterId.SPREADSHEETS -> toggleCategory(current, DocumentCategory.SPREADSHEETS)
        SearchQuickFilterId.SLIDES -> toggleCategory(current, DocumentCategory.SLIDES)
        SearchQuickFilterId.IMAGES -> toggleCategory(current, DocumentCategory.IMAGES)
        SearchQuickFilterId.CODE_TEXT -> toggleCategory(current, DocumentCategory.CODE_TEXT)
        SearchQuickFilterId.EBOOKS -> toggleCategory(current, DocumentCategory.EBOOKS)
      }
    }
  }

  private fun toggleCategory(
    current: SearchUiState,
    category: DocumentCategory,
  ): SearchUiState {
    val next = if (current.selectedCategory == category) null else category
    return current.copy(
      selectedCategory = next,
      documentsOnly = false,
    )
  }

  fun selectTagFilter(tagId: Long) {
    filterStateFlow.update { current ->
      current.copy(selectedTagId = if (current.selectedTagId == tagId) null else tagId)
    }
  }

  fun toggleSortField() {
    filterStateFlow.update { current ->
      val next =
        if (current.sortField == FileSortField.DATE_MODIFIED) FileSortField.NAME
        else FileSortField.DATE_MODIFIED
      current.copy(sortField = next)
    }
  }

  fun clearAllFilters() {
    filterStateFlow.update {
      SearchUiState(sortField = it.sortField)
    }
  }

  fun toggleFavorite(documentId: Long, currentlyFavorite: Boolean) {
    viewModelScope.launch {
      documentRepository.setFavorite(documentId, !currentlyFavorite)
    }
  }

  fun recordDocumentOpened(documentId: Long) {
    viewModelScope.launch {
      documentRepository.recordDocumentOpened(documentId)
    }
  }

  companion object {
    private const val SEVEN_DAYS_MS = 7L * 24L * 60L * 60L * 1000L
  }
}

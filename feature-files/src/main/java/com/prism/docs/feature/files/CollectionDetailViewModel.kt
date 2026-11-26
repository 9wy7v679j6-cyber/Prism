package com.prism.docs.feature.files

import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.Immutable
import com.prism.docs.core.data.db.CollectionEntity
import com.prism.docs.core.data.db.IndexedDocumentEntity
import com.prism.docs.core.data.paging.DocumentRepository
import com.prism.docs.core.data.storage.FileOperationsManager
import com.prism.docs.core.data.thumbnail.DocumentThumbnailPipeline
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class CollectionDetailUiState(
  val collection: CollectionEntity? = null,
  val documents: List<IndexedDocumentEntity> = emptyList(),
  val isLoaded: Boolean = false,
  val renameTarget: IndexedDocumentEntity? = null,
  val statusMessage: String? = null,
) {
  val totalBytes: Long
    get() = documents.sumOf { it.sizeBytes.coerceAtLeast(0L) }
}

@HiltViewModel
class CollectionDetailViewModel
@Inject
constructor(
  savedStateHandle: SavedStateHandle,
  private val documentRepository: DocumentRepository,
  private val fileOperationsManager: FileOperationsManager,
  val thumbnailPipeline: DocumentThumbnailPipeline,
) : ViewModel() {

  private val collectionId: Long = savedStateHandle.get<Long>("collectionId") ?: 0L
  private val renameTargetFlow = MutableStateFlow<IndexedDocumentEntity?>(null)
  private val statusMessageFlow = MutableStateFlow<String?>(null)

  val uiState: StateFlow<CollectionDetailUiState> =
    combine(
      documentRepository.observeCollection(collectionId),
      documentRepository.observeDocumentsInCollection(collectionId),
      renameTargetFlow,
      statusMessageFlow,
    ) { collection, documents, renameTarget, statusMessage ->
      CollectionDetailUiState(
        collection = collection,
        documents = documents,
        isLoaded = true,
        renameTarget = renameTarget,
        statusMessage = statusMessage,
      )
    }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000L),
      initialValue = CollectionDetailUiState(),
    )

  fun renameCollection(newName: String) {
    viewModelScope.launch {
      val ok = documentRepository.renameCollection(collectionId, newName)
      statusMessageFlow.value =
        if (ok) "Renamed collection to \"${newName.trim()}\"" else "Enter a valid collection name"
    }
  }

  fun deleteCollection(onDeleted: () -> Unit) {
    viewModelScope.launch {
      documentRepository.deleteCollection(collectionId)
      onDeleted()
    }
  }

  fun removeFromCollection(documentId: Long) {
    viewModelScope.launch {
      documentRepository.removeDocumentFromCollection(documentId, collectionId)
      statusMessageFlow.value = "Removed from collection"
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

  fun openRenameDialog(document: IndexedDocumentEntity) {
    renameTargetFlow.value = document
  }

  fun dismissRenameDialog() {
    renameTargetFlow.value = null
  }

  fun confirmRename(newName: String) {
    val target = renameTargetFlow.value ?: return
    viewModelScope.launch {
      val result = fileOperationsManager.renameDocument(target.id, newName)
      renameTargetFlow.value = null
      statusMessageFlow.value = result.message
    }
  }

  fun buildShareIntent(documentId: Long, onReady: (Intent?) -> Unit) {
    viewModelScope.launch {
      val docs = documentRepository.getDocumentsByIds(listOf(documentId))
      onReady(fileOperationsManager.buildShareIntent(docs))
    }
  }

  fun moveToRecycleBin(documentId: Long) {
    viewModelScope.launch {
      val result = fileOperationsManager.moveToRecycleBin(listOf(documentId))
      statusMessageFlow.value = result.message
    }
  }

  fun clearStatusMessage() {
    statusMessageFlow.value = null
  }
}

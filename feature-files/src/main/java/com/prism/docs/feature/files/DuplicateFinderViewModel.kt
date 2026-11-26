package com.prism.docs.feature.files

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.Immutable
import com.prism.docs.core.data.duplicates.DuplicateFileGroup
import com.prism.docs.core.data.duplicates.DuplicateScanProgress
import com.prism.docs.core.data.duplicates.DuplicateScanScheduler
import com.prism.docs.core.data.duplicates.DuplicateScanStage
import com.prism.docs.core.data.thumbnail.DocumentThumbnailPipeline
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class DuplicateFinderUiState(
  val progress: DuplicateScanProgress = DuplicateScanProgress(),
  val groups: List<DuplicateFileGroup> = emptyList(),
  val isTrashing: Boolean = false,
  val statusMessage: String? = null,
) {
  val totalReclaimableBytes: Long
    get() = groups.sumOf { it.reclaimableBytes }

  val totalExtraCopies: Int
    get() = groups.sumOf { it.duplicatesToTrash.size }
}

@HiltViewModel
class DuplicateFinderViewModel
@Inject
constructor(
  private val scheduler: DuplicateScanScheduler,
  val thumbnailPipeline: DocumentThumbnailPipeline,
) : ViewModel() {

  private val isTrashingFlow = MutableStateFlow(false)
  private val statusMessageFlow = MutableStateFlow<String?>(null)
  private var autoStarted = false

  val uiState: StateFlow<DuplicateFinderUiState> =
    combine(
      scheduler.observeScanProgress(),
      scheduler.observeDuplicateGroups(),
      isTrashingFlow,
      statusMessageFlow,
    ) { progress, groups, isTrashing, message ->
      DuplicateFinderUiState(
        progress = progress,
        groups = groups,
        isTrashing = isTrashing,
        statusMessage = message,
      )
    }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000L),
      initialValue = DuplicateFinderUiState(),
    )

  fun triggerInitialScanIfNeeded() {
    if (autoStarted) return
    autoStarted = true
    viewModelScope.launch {
      val (progress, groups) =
        combine(scheduler.observeScanProgress(), scheduler.observeDuplicateGroups()) { p, g -> p to g }
          .first()
      if (!progress.isRunning && progress.stage == DuplicateScanStage.IDLE && groups.isEmpty()) {
        scheduler.startScan()
      }
    }
  }

  fun startRescan() {
    statusMessageFlow.value = null
    scheduler.startScan()
  }

  fun cancelScan() {
    scheduler.cancelScan()
  }

  fun keepNewestMoveAllToBin() {
    val groups = uiState.value.groups
    if (groups.isEmpty() || isTrashingFlow.value) return
    viewModelScope.launch {
      isTrashingFlow.value = true
      try {
        val result = scheduler.moveNonNewestToRecycleBin(groups)
        statusMessageFlow.value = result.message
      } finally {
        isTrashingFlow.value = false
      }
    }
  }

  fun keepNewestInGroup(group: DuplicateFileGroup) {
    if (group.duplicatesToTrash.isEmpty() || isTrashingFlow.value) return
    viewModelScope.launch {
      isTrashingFlow.value = true
      try {
        val result = scheduler.moveNonNewestToRecycleBin(listOf(group))
        statusMessageFlow.value = result.message
      } finally {
        isTrashingFlow.value = false
      }
    }
  }

  fun clearStatusMessage() {
    statusMessageFlow.value = null
  }
}

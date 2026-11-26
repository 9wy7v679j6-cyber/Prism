package com.prism.docs

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prism.docs.core.data.indexer.DocumentIndexScheduler
import com.prism.docs.core.data.model.GlassIntensity
import com.prism.docs.core.data.model.IncomingDocumentRequest
import com.prism.docs.core.data.model.IndexWorkStatus
import com.prism.docs.core.data.model.PrismPreferences
import com.prism.docs.core.data.model.StorageAccessLevel
import com.prism.docs.core.data.model.StorageAccessState
import com.prism.docs.core.data.model.ThemeMode
import com.prism.docs.core.data.preferences.PrismPreferencesRepository
import com.prism.docs.core.data.storage.StoragePermissionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class PrismAppUiState(
  val isLoaded: Boolean = false,
  val preferences: PrismPreferences = PrismPreferences(),
  val storageAccessState: StorageAccessState =
    StorageAccessState(
      level = StorageAccessLevel.LIMITED_NONE,
      sdkInt = Build.VERSION.SDK_INT,
      hasAllFilesPermission = false,
      safFolders = emptyList(),
    ),
  val indexStatus: IndexWorkStatus = IndexWorkStatus(),
  val pendingIncomingDocument: IncomingDocumentRequest? = null,
)

@HiltViewModel
class PrismViewModel
@Inject
constructor(
  private val preferencesRepository: PrismPreferencesRepository,
  val storagePermissionManager: StoragePermissionManager,
  private val documentIndexScheduler: DocumentIndexScheduler,
) : ViewModel() {

  private val permissionRefreshSignal = MutableStateFlow(0L)
  private val pendingIncoming = MutableStateFlow<IncomingDocumentRequest?>(null)
  private val liveGlassClarity = MutableStateFlow<Float?>(null)
  private var lastKnownAccessLevel: StorageAccessLevel? = null

  private val effectivePrefsFlow =
    combine(preferencesRepository.preferencesFlow, liveGlassClarity) { prefs, overrideClarity ->
      if (overrideClarity != null) prefs.copy(glassClarity = overrideClarity) else prefs
    }

  private val storageAccessStateFlow =
    combine(
        preferencesRepository.preferencesFlow
          .map { it.safFolderGrants }
          .distinctUntilChanged(),
        permissionRefreshSignal,
      ) { safGrants, _ ->
        storagePermissionManager.computeAccessState(safGrants)
      }
      .flowOn(Dispatchers.IO)
      .distinctUntilChanged()
      .onEach { accessState ->
        val previous = lastKnownAccessLevel
        lastKnownAccessLevel = accessState.level
        if (
          previous != null &&
            previous != accessState.level &&
            accessState.level != StorageAccessLevel.LIMITED_NONE
        ) {
          documentIndexScheduler.triggerImmediateSync()
        }
      }

  val uiState: StateFlow<PrismAppUiState> =
    combine(
        effectivePrefsFlow,
        storageAccessStateFlow,
        documentIndexScheduler.observeIndexStatus(),
        pendingIncoming,
      ) { effectivePrefs, accessState, indexStatus, incoming ->
        PrismAppUiState(
          isLoaded = true,
          preferences = effectivePrefs,
          storageAccessState = accessState,
          indexStatus = indexStatus,
          pendingIncomingDocument = incoming,
        )
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PrismAppUiState(isLoaded = false),
      )

  fun refreshStorageAccess() {
    permissionRefreshSignal.update { it + 1L }
  }

  fun triggerImmediateIndexSync() {
    documentIndexScheduler.triggerImmediateSync()
  }

  fun onSafTreePicked(uri: Uri) {
    viewModelScope.launch {
      val grant = storagePermissionManager.persistSafTreePermission(uri)
      if (grant != null) {
        preferencesRepository.addSafFolderGrant(grant)
        refreshStorageAccess()
        documentIndexScheduler.triggerImmediateSync()
      }
    }
  }

  fun removeSafFolder(uriString: String) {
    viewModelScope.launch {
      storagePermissionManager.releaseSafTreePermission(uriString)
      preferencesRepository.removeSafFolderGrant(uriString)
      refreshStorageAccess()
      documentIndexScheduler.triggerImmediateSync()
    }
  }

  fun completeOnboarding() {
    viewModelScope.launch {
      preferencesRepository.setOnboardingCompleted(true)
      documentIndexScheduler.triggerImmediateSync()
    }
  }

  fun setThemeMode(mode: ThemeMode) {
    viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
  }

  fun setUseDynamicColor(enabled: Boolean) {
    viewModelScope.launch { preferencesRepository.setUseDynamicColor(enabled) }
  }

  fun setGlassIntensity(intensity: GlassIntensity) {
    viewModelScope.launch { preferencesRepository.setGlassIntensity(intensity) }
  }

  fun previewGlassClarity(clarity: Float) {
    liveGlassClarity.value = clarity.coerceIn(0f, 1f)
  }

  fun commitGlassClarity(clarity: Float) {
    val clamped = clarity.coerceIn(0f, 1f)
    liveGlassClarity.value = clamped
    viewModelScope.launch {
      preferencesRepository.setGlassClarity(clamped)
      withTimeoutOrNull(500L) {
        preferencesRepository.preferencesFlow.first { abs(it.glassClarity - clamped) < 0.001f }
      }
      if (liveGlassClarity.value == clamped) {
        liveGlassClarity.value = null
      }
    }
  }

  fun setReduceTransparency(enabled: Boolean) {
    viewModelScope.launch { preferencesRepository.setReduceTransparency(enabled) }
  }

  fun setReduceMotion(enabled: Boolean) {
    viewModelScope.launch { preferencesRepository.setReduceMotion(enabled) }
  }

  fun dismissFullAccessBanner() {
    viewModelScope.launch { preferencesRepository.setFullAccessBannerDismissed(true) }
  }

  fun handleIncomingIntent(intent: Intent?) {
    if (intent == null) return
    val action = intent.action ?: return
    val explicitMimeType = intent.type
    val uri: Uri? =
      when (action) {
        Intent.ACTION_VIEW -> intent.data
        Intent.ACTION_SEND -> extractStreamUri(intent)
        Intent.ACTION_SEND_MULTIPLE -> extractStreamUris(intent).firstOrNull()
        else -> null
      }

    if (uri != null) {
      viewModelScope.launch(Dispatchers.IO) {
        val resolved =
          storagePermissionManager.resolveIncomingDocument(
            uri = uri,
            explicitMimeType = explicitMimeType,
            sourceAction = action.substringAfterLast('.'),
          )
        pendingIncoming.value = resolved
      }
    }
  }

  fun consumeIncomingDocument() {
    pendingIncoming.value = null
  }

  @Suppress("DEPRECATION")
  private fun extractStreamUri(intent: Intent): Uri? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
    } else {
      intent.getParcelableExtra(Intent.EXTRA_STREAM)
    }
  }

  @Suppress("DEPRECATION")
  private fun extractStreamUris(intent: Intent): List<Uri> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
    } else {
      intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
    }
  }
}

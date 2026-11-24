package com.prism.docs.navigation

import kotlinx.serialization.Serializable

@Serializable data object OnboardingRoute

@Serializable data object LibraryRoute

@Serializable data object ScanRoute

@Serializable data object PdfToolsRoute

@Serializable data object SearchRoute

@Serializable
data class ViewerRoute(
  val uriString: String? = null,
  val displayName: String? = null,
  val mimeType: String? = null,
  val sizeBytes: Long = -1L,
  val sourceAction: String = "PREVIEW",
)

@Serializable data object EditorRoute

@Serializable data object DuplicateFinderRoute

@Serializable data object StorageAnalyzerRoute

@Serializable data class CollectionDetailRoute(val collectionId: Long)


package com.prism.docs.feature.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prism.docs.core.data.model.IncomingDocumentRequest
import com.prism.docs.core.ui.glass.GlassElevation
import com.prism.docs.core.ui.glass.GlassPageThumbnailStrip
import com.prism.docs.core.ui.glass.GlassSurface
import com.prism.docs.core.ui.glass.GlassToolbarAction
import com.prism.docs.core.ui.glass.GlassToolbarGroup
import com.prism.docs.core.ui.theme.LocalHazeState
import dev.chrisbanes.haze.hazeSource

@Composable
fun ViewerShellScreen(
  document: IncomingDocumentRequest?,
  onNavigateBack: () -> Unit,
  onOpenExternalChooser: ((String, String) -> Unit)? = null,
  onOpenEditorRoute: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val hazeState = LocalHazeState.current
  val colorScheme = MaterialTheme.colorScheme
  var currentPage by remember { mutableIntStateOf(0) }
  var nightInvert by remember { mutableStateOf(false) }

  Box(modifier = modifier.fillMaxSize()) {
    // Document canvas area (marked as hazeSource so floating chrome blurs live page content)
    Box(
      modifier =
        Modifier.fillMaxSize()
          .then(if (hazeState != null) Modifier.hazeSource(hazeState) else Modifier)
          .padding(horizontal = 20.dp, vertical = 104.dp),
      contentAlignment = Alignment.Center,
    ) {
      GlassSurface(
        cornerRadius = 28.dp,
        elevation = GlassElevation.SUBTLE,
        modifier = Modifier.fillMaxWidth().widthIn(max = 620.dp).testTag("viewer_document_canvas"),
      ) {
        Column(
          modifier = Modifier.padding(24.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Box(
              modifier =
                Modifier.size(48.dp)
                  .clip(RoundedCornerShape(16.dp))
                  .background(colorScheme.primary.copy(alpha = 0.14f)),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Rounded.Description,
                contentDescription = null,
                tint = colorScheme.primary,
                modifier = Modifier.size(26.dp),
              )
            }
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = document?.displayName ?: "Unified Viewer Shell (Phase 0)",
                style = MaterialTheme.typography.titleLarge,
                color = colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text =
                  if (document != null) {
                    "${document.mimeType} • ${formatBytes(document.sizeBytes)}"
                  } else {
                    "Page ${currentPage + 1} of 8 • Preview Mode"
                  },
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.primary,
              )
            }
          }

          if (document != null) {
            Box(
              modifier =
                Modifier.fillMaxWidth()
                  .clip(RoundedCornerShape(14.dp))
                  .background(colorScheme.surfaceVariant.copy(alpha = 0.55f))
                  .padding(12.dp)
            ) {
              Text(
                text = "URI: ${document.uriString}\nAction: ${document.sourceAction}",
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant,
              )
            }
          }

          Text(
            text =
              "Prism's single unified viewer shell resolves VIEW / SEND / SEND_MULTIPLE intents for content:// and file:// URIs safely. Phase 2 connects the tiled PDF renderer, Media3 audio/video player, syntax-highlighted text/code viewer, archive explorer, and streaming OOXML DOCX/XLSX/PPTX engines.",
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurfaceVariant,
          )

          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (document != null && onOpenExternalChooser != null) {
              Button(
                onClick = { onOpenExternalChooser(document.uriString, document.mimeType) },
                modifier = Modifier.testTag("viewer_open_external_button"),
              ) {
                Icon(
                  imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text("Open in another app")
              }
            }
            OutlinedButton(
              onClick = onOpenEditorRoute,
              modifier = Modifier.testTag("viewer_editor_route_button"),
            ) {
              Icon(
                imageVector = Icons.Rounded.EditNote,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(Modifier.size(8.dp))
              Text("Annotate")
            }
          }
        }
      }
    }

    // Floating top Liquid Glass viewer chrome
    Row(
      modifier =
        Modifier.align(Alignment.TopCenter)
          .fillMaxWidth()
          .statusBarsPadding()
          .padding(horizontal = 16.dp, vertical = 12.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      GlassToolbarGroup(
        actions =
          listOf(
            GlassToolbarAction(
              icon = Icons.AutoMirrored.Rounded.ArrowBack,
              contentDescription = "Navigate back",
              testTag = "viewer_back_button",
              onClick = onNavigateBack,
            )
          )
      )

      GlassToolbarGroup(
        actions =
          listOf(
            GlassToolbarAction(
              icon = Icons.Rounded.DarkMode,
              contentDescription = "Toggle reader night mode",
              testTag = "viewer_night_mode_button",
              active = nightInvert,
              onClick = { nightInvert = !nightInvert },
            ),
            GlassToolbarAction(
              icon = Icons.Rounded.Info,
              contentDescription = "Document details",
              testTag = "viewer_info_button",
              onClick = {},
            ),
          )
      )
    }

    // Floating bottom Liquid Glass page-thumbnail scrubber strip
    Box(
      modifier =
        Modifier.align(Alignment.BottomCenter)
          .fillMaxWidth()
          .widthIn(max = 620.dp)
          .navigationBarsPadding()
          .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
      GlassPageThumbnailStrip(
        pageCount = 8,
        currentPage = currentPage,
        onSelectPage = { currentPage = it },
      )
    }
  }
}

private fun formatBytes(sizeBytes: Long): String {
  if (sizeBytes < 0L) return "Unknown size"
  if (sizeBytes < 1024L) return "$sizeBytes B"
  val kb = sizeBytes / 1024.0
  if (kb < 1024.0) return String.format("%.1f KB", kb)
  val mb = kb / 1024.0
  return String.format("%.2f MB", mb)
}

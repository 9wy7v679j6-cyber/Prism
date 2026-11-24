package com.prism.docs.feature.pdftools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMerge
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.prism.docs.core.ui.glass.GlassElevation
import com.prism.docs.core.ui.glass.GlassSegmentedControl
import com.prism.docs.core.ui.glass.GlassSurface
import com.prism.docs.core.ui.glass.ObserveScrollCollapse
import com.prism.docs.core.ui.glass.ScrollEdgeFade
import com.prism.docs.core.ui.glass.rememberHeaderScrollProgress
import com.prism.docs.core.ui.theme.LocalGlassEnvironment
import com.prism.docs.core.ui.theme.LocalHazeState
import com.prism.docs.core.ui.theme.LocalPrismVisualTokens
import com.prism.docs.core.ui.theme.PrismColors
import dev.chrisbanes.haze.hazeSource

private data class PdfToolSpec(
  val groupIndex: Int,
  val title: String,
  val summary: String,
  val icon: ImageVector,
  val accent: Color,
)

@Composable
fun PdfToolsHubScreen(
  onTabBarCollapsedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  val listState = rememberLazyListState()
  val headerScrollProgress = rememberHeaderScrollProgress(listState)
  val hazeState = LocalHazeState.current
  val env = LocalGlassEnvironment.current
  val tokens = LocalPrismVisualTokens.current
  val colorScheme = MaterialTheme.colorScheme
  val density = LocalDensity.current
  var selectedGroup by remember { mutableIntStateOf(0) }

  val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
  val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
  var topHeaderPx by remember { mutableIntStateOf(0) }
  val topHeaderDp =
    if (topHeaderPx > 0) with(density) { topHeaderPx.toDp() } else statusBarTop + 88.dp

  ObserveScrollCollapse(listState = listState, onCollapsedChanged = onTabBarCollapsedChange)

  val allTools =
    remember(env.isDark, colorScheme, tokens) {
      listOf(
        PdfToolSpec(
          0,
          "Merge & Split PDF",
          "Combine files or split by page ranges, fixed intervals, or bookmarks — non-destructive by default.",
          Icons.AutoMirrored.Rounded.CallMerge,
          tokens.destructiveAccent,
        ),
        PdfToolSpec(
          0,
          "Reorder, Rotate & Extract",
          "Drag-and-drop page thumbnails, delete pages, crop margins, or insert blank pages.",
          Icons.AutoMirrored.Rounded.RotateRight,
          colorScheme.primary,
        ),
        PdfToolSpec(
          1,
          "Compress, Flatten & Repair",
          "Low / Medium / High image re-encoding with before-and-after byte savings via PdfBox-Android.",
          Icons.Rounded.Compress,
          tokens.successAccent,
        ),
        PdfToolSpec(
          1,
          "Convert Offline",
          "Images <-> PDF, HTML/TXT to PDF, and clearly labeled best-effort Office/reflow conversions.",
          Icons.Rounded.Image,
          if (env.isDark) Color(0xFFD68FFF) else PrismColors.OrchidImage,
        ),
        PdfToolSpec(
          2,
          "Sign, Watermark & Redact",
          "Draw/type/image signatures, headers/footers, page numbers, stamps, AcroForms, and true content redaction.",
          Icons.Rounded.Draw,
          colorScheme.secondary,
        ),
        PdfToolSpec(
          2,
          "Protect, Unlock & Compare",
          "AES-256 encryption, permission flags, visual two-PDF diff, metadata editor, and WorkManager batch queue.",
          Icons.Rounded.Lock,
          tokens.warningAccent,
        ),
      )
    }

  val visibleTools =
    remember(selectedGroup, allTools) {
      if (selectedGroup == 0) allTools else allTools.filter { it.groupIndex == selectedGroup - 1 }
    }

  Box(modifier = modifier.fillMaxSize()) {
    LazyColumn(
      state = listState,
      contentPadding =
        PaddingValues(
          top = topHeaderDp + 8.dp,
          bottom = navBarBottom + 116.dp,
          start = 20.dp,
          end = 20.dp,
        ),
      verticalArrangement = Arrangement.spacedBy(14.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier =
        Modifier.fillMaxSize()
          .then(if (hazeState != null) Modifier.hazeSource(hazeState, zIndex = 1f) else Modifier)
          .testTag("pdf_tools_list"),
    ) {
      item {
        GlassSegmentedControl(
          options = listOf("All", "Organize", "Optimize", "Edit & Lock"),
          selectedIndex = selectedGroup,
          onSelect = { selectedGroup = it },
          modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
        )
      }

      items(visibleTools, key = { it.title }) { tool ->
        GlassSurface(
          cornerRadius = 24.dp,
          elevation = GlassElevation.SUBTLE,
          accentTint = tool.accent,
          modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top,
          ) {
            Box(
              modifier =
                Modifier.size(44.dp)
                  .clip(RoundedCornerShape(14.dp))
                  .background(tool.accent.copy(alpha = if (env.isDark) 0.22f else 0.16f)),
              contentAlignment = Alignment.Center,
            ) {
              Icon(imageVector = tool.icon, contentDescription = null, tint = tool.accent)
            }
            Column(
              modifier = Modifier.weight(1f),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = tool.title,
                  style = MaterialTheme.typography.titleMedium,
                  color = colorScheme.onSurface,
                  modifier = Modifier.weight(1f),
                )
                Text(
                  text = "PHASE 5",
                  style = MaterialTheme.typography.labelSmall,
                  color = tool.accent,
                  maxLines = 1,
                )
              }
              Text(
                text = tool.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }

    ScrollEdgeFade(
      isTop = true,
      height = topHeaderDp + 14.dp,
      strongTint = true,
      scrollProgress = headerScrollProgress,
      modifier = Modifier.align(Alignment.TopCenter),
    )

    Column(
      modifier =
        Modifier.align(Alignment.TopStart)
          .fillMaxWidth()
          .onSizeChanged { topHeaderPx = it.height }
          .statusBarsPadding()
          .padding(horizontal = 20.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
        text = "Offline PDF toolkit",
        style = MaterialTheme.typography.titleSmall,
        color = colorScheme.onSurfaceVariant,
      )
      Text(
        text = "Tools",
        style = MaterialTheme.typography.displaySmall,
        color = colorScheme.onSurface,
      )
    }

    ScrollEdgeFade(
      isTop = false,
      height = navBarBottom + 96.dp,
      modifier = Modifier.align(Alignment.BottomCenter),
    )
  }
}

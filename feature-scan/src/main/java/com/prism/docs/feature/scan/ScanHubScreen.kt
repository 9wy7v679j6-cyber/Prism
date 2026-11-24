package com.prism.docs.feature.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.prism.docs.core.ui.glass.GlassElevation
import com.prism.docs.core.ui.glass.GlassSurface
import com.prism.docs.core.ui.glass.ObserveScrollCollapse
import com.prism.docs.core.ui.glass.ScrollEdgeFade
import com.prism.docs.core.ui.glass.rememberHeaderScrollProgress
import com.prism.docs.core.ui.theme.LocalHazeState
import dev.chrisbanes.haze.hazeSource

@Composable
fun ScanHubScreen(
  onTabBarCollapsedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  val listState = rememberLazyListState()
  val headerScrollProgress = rememberHeaderScrollProgress(listState)
  val hazeState = LocalHazeState.current
  val colorScheme = MaterialTheme.colorScheme

  ObserveScrollCollapse(listState = listState, onCollapsedChanged = onTabBarCollapsedChange)

  Box(modifier = modifier.fillMaxSize()) {
    LazyColumn(
      state = listState,
      contentPadding = PaddingValues(top = 96.dp, bottom = 128.dp, start = 20.dp, end = 20.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier =
        Modifier.fillMaxSize()
          .statusBarsPadding()
          .then(if (hazeState != null) Modifier.hazeSource(hazeState, zIndex = 1f) else Modifier)
          .testTag("scan_hub_list"),
    ) {
      item {
        ScanFeatureCard(
          icon = Icons.Rounded.DocumentScanner,
          title = "Smart Document Scanner",
          subtitle =
            "ML Kit Document Scanner with automatic edge detection, perspective correction, shadow & finger cleanup, and multi-page PDF capture.",
          badge = "PHASE 4",
        )
      }
      item {
        ScanFeatureCard(
          icon = Icons.Rounded.TextFields,
          title = "Searchable PDF & On-Device OCR",
          subtitle =
            "Embed an invisible text layer over scanned images with ML Kit Text Recognition v2 (Latin & Devanagari) and Tesseract4Android fallback for Malayalam & Tamil.",
          badge = "PHASE 4",
        )
      }
      item {
        ScanFeatureCard(
          icon = Icons.Rounded.AutoFixHigh,
          title = "Auto-Name & Instant Index",
          subtitle =
            "Suggest filenames from detected document headers and dates, then index recognized text into Room FTS immediately.",
          badge = "PHASE 4",
        )
      }
      item {
        ScanFeatureCard(
          icon = Icons.Rounded.Translate,
          title = "Offline Language Packs",
          subtitle =
            "All recognition stays on-device; optional traineddata packs download only when explicitly triggered by you.",
          badge = "PRIVACY-FIRST",
        )
      }
    }

    ScrollEdgeFade(
      isTop = true,
      height = 168.dp,
      strongTint = true,
      scrollProgress = headerScrollProgress,
      modifier = Modifier.align(Alignment.TopCenter),
    )

    Column(
      modifier =
        Modifier.align(Alignment.TopStart)
          .fillMaxWidth()
          .statusBarsPadding()
          .padding(horizontal = 20.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
        text = "Offline capture & OCR",
        style = MaterialTheme.typography.titleSmall,
        color = colorScheme.onSurfaceVariant,
      )
      Text(
        text = "Scan",
        style = MaterialTheme.typography.displaySmall,
        color = colorScheme.onSurface,
      )
    }

    ScrollEdgeFade(isTop = false, height = 104.dp, modifier = Modifier.align(Alignment.BottomCenter))
  }
}

@Composable
private fun ScanFeatureCard(
  icon: ImageVector,
  title: String,
  subtitle: String,
  badge: String,
) {
  val colorScheme = MaterialTheme.colorScheme
  GlassSurface(
    cornerRadius = 24.dp,
    elevation = GlassElevation.SUBTLE,
    modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp),
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
            .background(colorScheme.primary.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
      ) {
        Icon(imageVector = icon, contentDescription = null, tint = colorScheme.primary)
      }
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = colorScheme.onSurface,
            modifier = Modifier.weight(1f),
          )
          Text(
            text = badge,
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.primary,
          )
        }
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodyMedium,
          color = colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

package com.prism.docs.feature.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.prism.docs.core.ui.glass.GlassElevation
import com.prism.docs.core.ui.glass.GlassSurface
import com.prism.docs.core.ui.glass.GlassToolbarAction
import com.prism.docs.core.ui.glass.GlassToolbarGroup

@Composable
fun EditorContractScreen(
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme
  val density = LocalDensity.current

  val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
  val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
  var topChromeHeightPx by remember { mutableIntStateOf(0) }
  val topChromeDp =
    if (topChromeHeightPx > 0) with(density) { topChromeHeightPx.toDp() }
    else statusBarTop + 72.dp

  Box(modifier = modifier.fillMaxSize()) {
    Box(
      modifier =
        Modifier.fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(
            top = topChromeDp + 12.dp,
            bottom = navBarBottom + 24.dp,
            start = 20.dp,
            end = 20.dp,
          ),
      contentAlignment = Alignment.Center,
    ) {
      GlassSurface(
        cornerRadius = 26.dp,
        elevation = GlassElevation.SUBTLE,
        modifier = Modifier.widthIn(max = 580.dp).fillMaxWidth().testTag("editor_shell_card"),
      ) {
        Column(
          modifier = Modifier.padding(22.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text(
            text = "Annotation & Safe Editor (Phase 6)",
            style = MaterialTheme.typography.titleLarge,
            color = colorScheme.onSurface,
          )
          Text(
            text =
              "Phase 6 adds PDF annotations (stylus/palm-rejected ink, highlights, sticky notes, signatures), DOCX/XLSX/PPTX light editing preserving unknown XML, full text/code editing, and atomic temp-file validation before saving.",
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    GlassToolbarGroup(
      actions =
        listOf(
          GlassToolbarAction(
            icon = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = "Back from editor",
            testTag = "editor_back_button",
            onClick = onNavigateBack,
          )
        ),
      modifier =
        Modifier.align(Alignment.TopStart)
          .onSizeChanged { topChromeHeightPx = it.height }
          .statusBarsPadding()
          .padding(horizontal = 16.dp, vertical = 12.dp),
    )
  }
}

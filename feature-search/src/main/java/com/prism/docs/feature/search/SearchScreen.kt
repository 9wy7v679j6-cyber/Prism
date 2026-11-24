package com.prism.docs.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.prism.docs.core.ui.glass.GlassElevation
import com.prism.docs.core.ui.glass.GlassSearchField
import com.prism.docs.core.ui.glass.GlassSurface
import com.prism.docs.core.ui.glass.GlassToolbarAction
import com.prism.docs.core.ui.glass.GlassToolbarGroup
import com.prism.docs.core.ui.glass.ScrollEdgeFade
import com.prism.docs.core.ui.glass.rememberHeaderScrollProgress
import com.prism.docs.core.ui.theme.LocalHazeState
import dev.chrisbanes.haze.hazeSource

@Composable
fun SearchScreen(
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val hazeState = LocalHazeState.current
  val colorScheme = MaterialTheme.colorScheme
  val listState = rememberLazyListState()
  val headerScrollProgress = rememberHeaderScrollProgress(listState)
  var query by remember { mutableStateOf("") }
  var selectedFilter by remember { mutableStateOf("All") }
  val filters = remember { listOf("All", "PDF", "Documents", "Spreadsheets", "OCR Text", "This Week") }

  Box(modifier = modifier.fillMaxSize()) {
    LazyColumn(
      state = listState,
      contentPadding = PaddingValues(top = 136.dp, bottom = 120.dp, start = 16.dp, end = 16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier =
        Modifier.fillMaxSize()
          .statusBarsPadding()
          .then(if (hazeState != null) Modifier.hazeSource(hazeState, zIndex = 1f) else Modifier)
          .testTag("search_results_list"),
    ) {
      item {
        GlassSurface(
          cornerRadius = 24.dp,
          elevation = GlassElevation.SUBTLE,
          modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp),
        ) {
          Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text =
                if (query.isBlank()) "On-Device Full-Text Search"
                else "Searching \"$query\" in $selectedFilter",
              style = MaterialTheme.typography.titleMedium,
              color = colorScheme.onSurface,
            )
            Text(
              text =
                "Phase 3 attaches the Room FTS4/FTS5 full-text index and battery-aware WorkManager extraction pipeline across PDF, DOCX, XLSX, PPTX, Markdown, code, and OCR'd scans.",
              style = MaterialTheme.typography.bodyMedium,
              color = colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }

    ScrollEdgeFade(
      isTop = true,
      height = 172.dp,
      strongTint = true,
      scrollProgress = headerScrollProgress,
      modifier = Modifier.align(Alignment.TopCenter),
    )

    // Floating top search bar + filter rail
    Column(
      modifier =
        Modifier.align(Alignment.TopCenter)
          .fillMaxWidth()
          .widthIn(max = 720.dp)
          .statusBarsPadding()
          .padding(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        GlassToolbarGroup(
          actions =
            listOf(
              GlassToolbarAction(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back from search",
                testTag = "search_back_button",
                onClick = onNavigateBack,
              )
            )
        )
        GlassSearchField(
          query = query,
          onQueryChange = { query = it },
          placeholder = "Search filenames, PDF text, OCR...",
          modifier = Modifier.weight(1f),
        )
      }

      LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
      ) {
        items(filters, key = { it }) { filter ->
          val active = filter == selectedFilter
          GlassSurface(
            cornerRadius = 18.dp,
            elevation = GlassElevation.SUBTLE,
            useBackdropBlur = false,
            accentTint = if (active) colorScheme.primary else null,
            onClick = { selectedFilter = filter },
            clickRole = Role.Tab,
            modifier = Modifier.testTag("search_filter_${filter.lowercase()}"),
          ) {
            Text(
              text = filter,
              style = MaterialTheme.typography.labelLarge,
              color = if (active) colorScheme.primary else colorScheme.onSurface,
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
          }
        }
      }
    }
  }
}

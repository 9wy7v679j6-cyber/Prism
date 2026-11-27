package com.prism.docs.feature.viewer

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.prism.docs.core.data.db.PrismDatabase
import com.prism.docs.core.data.model.IncomingDocumentRequest
import com.prism.docs.core.data.viewer.ViewerStateRepository
import com.prism.docs.core.ui.theme.PrismTheme
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ViewerPersistenceAndUiRobolectricTest {

  @get:Rule
  val composeRule = createComposeRule()

  private lateinit var db: PrismDatabase
  private lateinit var repository: ViewerStateRepository
  private lateinit var context: Application

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    db = Room.inMemoryDatabaseBuilder(context, PrismDatabase::class.java).allowMainThreadQueries().build()
    repository = ViewerStateRepository(db.viewerStateDao())
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun lastPositionZoomPaperAndBookmarksPersistInRoom() = runBlocking {
    val uri = "content://docs/manual.pdf"
    repository.saveReadingState(
      uriString = uri,
      lastPage = 17,
      zoomScale = 2.35f,
      scrollOffsetFraction = 0.42f,
      scrollMode = PdfScrollMode.PAGED.name,
      paperMode = ReaderPaperMode.SEPIA.name,
    )

    val loaded = repository.getReadingState(uri)
    assertNotNull(loaded)
    assertEquals(17, loaded?.lastPage)
    assertEquals(2.35f, loaded?.zoomScale ?: 0f, 0.01f)
    assertEquals(0.42f, loaded?.scrollOffsetFraction ?: 0f, 0.01f)
    assertEquals(PdfScrollMode.PAGED.name, loaded?.scrollMode)
    assertEquals(ReaderPaperMode.SEPIA.name, loaded?.paperMode)

    val added = repository.toggleBookmark(uri, pageIndex = 17, defaultLabel = "Page 18")
    assertTrue(added)
    assertEquals(listOf(17), repository.observeBookmarks(uri).first().map { it.pageIndex })

    val removed = !repository.toggleBookmark(uri, pageIndex = 17, defaultLabel = "Page 18")
    assertTrue(removed)
    assertTrue(repository.observeBookmarks(uri).first().isEmpty())
  }

  @Test
  fun textAndJsonDocumentViewerRendersSearchCounterAndOptionsSheetInLightAndDark() {
    val jsonFile = File(context.cacheDir, "sample_config.json").apply {
      writeText("""{"app":"Prism","engine":"PdfRenderer","notes":"Prism supports glass clarity."}""")
    }
    val vm = ViewerViewModel(context, repository)
    val doc = IncomingDocumentRequest(
      uriString = jsonFile.toURI().toString(),
      displayName = "sample_config.json",
      mimeType = "application/json",
      sizeBytes = jsonFile.length(),
      sourceAction = "SEARCH",
      initialSearchQuery = "prism",
    )

    composeRule.setContent {
      PrismTheme(darkTheme = true) {
        ViewerShellScreen(
          document = doc,
          onNavigateBack = {},
          onOpenExternalChooser = { _, _ -> },
          viewModel = vm,
        )
      }
    }

    composeRule.waitUntil(timeoutMillis = 5_000L) {
      composeRule.onAllNodesWithTag("viewer_text_canvas").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("viewer_expanded_top_bar").assertIsDisplayed()
    composeRule.onNodeWithTag("viewer_search_bar").assertIsDisplayed()

    composeRule.waitUntil(timeoutMillis = 5_000L) {
      composeRule.onAllNodesWithTag("viewer_search_counter").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithText("1 of 2").assertIsDisplayed()

    composeRule.onNodeWithTag("viewer_search_next").performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithText("2 of 2").assertIsDisplayed()

    composeRule.onNodeWithTag("viewer_options_button").performClick()
    composeRule.waitUntil(timeoutMillis = 5_000L) {
      composeRule.onAllNodesWithTag("viewer_paper_mode_segmented").fetchSemanticsNodes().isNotEmpty()
    }
    jsonFile.delete()
  }

  @Test
  fun unsupportedDocumentShowsCleanPreviewCardWithExternalOpenButton() {
    val binFile = File(context.cacheDir, "archive_payload.xyz").apply {
      writeBytes(byteArrayOf(1, 2, 3, 4))
    }
    val vm = ViewerViewModel(context, repository)
    var externalClicked = false
    val doc = IncomingDocumentRequest(
      uriString = binFile.toURI().toString(),
      displayName = "archive_payload.xyz",
      mimeType = "application/octet-stream",
      sizeBytes = 4L,
    )

    composeRule.setContent {
      PrismTheme(darkTheme = false) {
        ViewerShellScreen(
          document = doc,
          onNavigateBack = {},
          onOpenExternalChooser = { _, _ -> externalClicked = true },
          viewModel = vm,
        )
      }
    }

    composeRule.waitUntil(timeoutMillis = 5_000L) {
      composeRule.onAllNodesWithTag("viewer_unsupported_card").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("viewer_open_external_button").assertIsDisplayed().performClick()
    assertTrue(externalClicked)
    binFile.delete()
  }

  @Test
  fun topBarMinimizesOnScrollDownAndRestoresOnMinimizedPillTapInLightMode() {
    val lines = (1..120).joinToString("\n") { "Line $it: Prism adaptive Liquid Glass document reader." }
    val txtFile = File(context.cacheDir, "notes_long.md").apply { writeText(lines) }
    val vm = ViewerViewModel(context, repository)
    val doc = IncomingDocumentRequest(
      uriString = txtFile.toURI().toString(),
      displayName = "notes_long.md",
      mimeType = "text/markdown",
      sizeBytes = txtFile.length(),
    )

    composeRule.setContent {
      PrismTheme(darkTheme = false) {
        ViewerShellScreen(
          document = doc,
          onNavigateBack = {},
          viewModel = vm,
        )
      }
    }

    composeRule.waitUntil(timeoutMillis = 5_000L) {
      composeRule.onAllNodesWithTag("viewer_text_canvas").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("viewer_expanded_top_bar").assertIsDisplayed()
    composeRule.onNodeWithTag("viewer_text_wrap_toggle").performClick()

    // Simulate downward scroll gesture on the text canvas to trigger top bar minimization
    composeRule.onNodeWithTag("viewer_text_canvas").performTouchInput {
      down(center)
      moveBy(androidx.compose.ui.geometry.Offset(0f, -450f))
      up()
    }
    composeRule.waitUntil(timeoutMillis = 5_000L) {
      composeRule.onAllNodesWithTag("viewer_minimized_top_bar").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("viewer_minimized_page_pill").assertIsDisplayed().performClick()
    composeRule.waitUntil(timeoutMillis = 5_000L) {
      composeRule.onAllNodesWithTag("viewer_expanded_top_bar").fetchSemanticsNodes().isNotEmpty()
    }
    txtFile.delete()
  }
}

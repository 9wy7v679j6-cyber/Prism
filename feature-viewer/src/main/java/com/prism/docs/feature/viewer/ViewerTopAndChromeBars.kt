package com.prism.docs.feature.viewer

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Rotate90DegreesCw
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prism.docs.core.data.db.DocumentBookmarkEntity
import com.prism.docs.core.ui.glass.ConnectedGlassCardSlice
import com.prism.docs.core.ui.glass.GlassBottomSheet
import com.prism.docs.core.ui.glass.GlassSegmentedControl
import com.prism.docs.core.ui.glass.GlassSheetDetent
import com.prism.docs.core.ui.glass.LocalGlassMotion
import com.prism.docs.core.ui.glass.glassClickable
import com.prism.docs.core.ui.glass.rememberPrismHaptics
import com.prism.docs.core.ui.theme.PrismDimens
import kotlin.math.roundToInt

/**
 * Solid high-contrast floating viewer top bar with scroll-driven morphing choreography:
 * - Renders 100% opaque Material 3 surfaces (zero translucent blur bleed over PDF text).
 * - Smoothly tracks scroll progress (`collapseProgress` 0f..1f) without ghost overlap so downward
 *   scrolling folds the expanded bar into compact solid corner pills while `continuousPageState`
 *   drives the live reading progress underline in the draw phase (zero recomposition).
 */
@Composable
internal fun ViewerCollapsingTopBar(
  title: String,
  subtitle: String,
  compactPageText: String,
  pageCount: Int,
  currentPage: Int,
  collapseProgress: Float,
  continuousPageState: State<Float>,
  currentChapter: String?,
  currentZoom: Float,
  isActivelyScrolling: Boolean,
  isSearchActive: Boolean,
  isBookmarked: Boolean,
  showRotateAction: Boolean,
  onNavigateBack: () -> Unit,
  onExpandFromMinimized: () -> Unit,
  onOpenOutline: () -> Unit,
  onToggleSearch: () -> Unit,
  onShare: () -> Unit,
  onRotateImage: () -> Unit,
  onOpenOptions: () -> Unit,
  onResetZoom: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme
  val motion = LocalGlassMotion.current
  val haptics = rememberPrismHaptics()

  val clampedCollapse = collapseProgress.coerceIn(0f, 1f)
  val minimizedPillPulse by
    animateFloatAsState(
      targetValue = if (isActivelyScrolling) 1.05f else 1f,
      animationSpec = if (motion) spring(dampingRatio = 0.62f, stiffness = 460f) else snap(),
      label = "viewer_min_pill_pulse",
    )

  val progressTrackColor = colorScheme.primary.copy(alpha = 0.16f)
  val progressFillColor = colorScheme.primary
  val percentInt =
    if (pageCount > 1) ((currentPage.toFloat() / (pageCount - 1).toFloat()) * 100f).roundToInt().coerceIn(0, 100)
    else if (pageCount == 1) 100 else 0

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Box(modifier = Modifier.fillMaxWidth()) {
      // Full Solid Top Bar (folds smoothly upward & fades out cleanly by 0.46f)
      if (clampedCollapse < 0.48f) {
        val expAlpha = (1f - clampedCollapse / 0.46f).coerceIn(0f, 1f)
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .graphicsLayer {
                alpha = expAlpha
                translationY = -clampedCollapse * 28.dp.toPx()
                val s = 1f - clampedCollapse * 0.12f
                scaleX = s
                scaleY = s
              }
              .testTag("viewer_expanded_top_bar"),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          // Left solid Back orb
          ViewerSolidSurface(
            cornerRadius = 24.dp,
            shape = CircleShape,
            shadowElevation = 8.dp,
            onClick = onNavigateBack,
            clickRole = Role.Button,
            modifier =
              Modifier.size(PrismDimens.TouchTargetMin)
                .defaultMinSize(PrismDimens.TouchTargetMin, PrismDimens.TouchTargetMin)
                .testTag("viewer_back_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
              contentDescription = stringResource(R.string.viewer_back_cd),
              tint = colorScheme.onSurface,
              modifier = Modifier.align(Alignment.Center).size(21.dp),
            )
          }

          // Centered solid Title + animated Page/Chapter subtitle + continuous reading progress track
          ViewerSolidSurface(
            cornerRadius = 24.dp,
            shape = CircleShape,
            shadowElevation = 8.dp,
            onClick = onOpenOutline,
            clickRole = Role.Button,
            modifier =
              Modifier.weight(1f)
                .height(PrismDimens.TouchTargetMin)
                .testTag("viewer_title_pill"),
          ) {
            Box(
              modifier =
                Modifier.fillMaxSize().drawBehind {
                  if (pageCount > 0) {
                    val liveFraction =
                      if (pageCount > 1) {
                        (continuousPageState.value / (pageCount - 1).toFloat()).coerceIn(0f, 1f)
                      } else 1f
                    val barHeight = 3.dp.toPx()
                    val y = size.height - barHeight
                    drawRect(
                      color = progressTrackColor,
                      topLeft = Offset(0f, y),
                      size = Size(size.width, barHeight),
                    )
                    drawRect(
                      color = progressFillColor,
                      topLeft = Offset(0f, y),
                      size = Size(size.width * liveFraction, barHeight),
                    )
                  }
                },
            ) {
              Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
              ) {
                Text(
                  text = title,
                  style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                  color = colorScheme.onSurface,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  textAlign = TextAlign.Center,
                )
                val fullSubtitle =
                  if (!currentChapter.isNullOrBlank()) "$subtitle • $currentChapter" else subtitle
                AnimatedContent(
                  targetState = fullSubtitle,
                  transitionSpec = {
                    if (motion) {
                      (slideInVertically(tween(150)) { it / 2 } + fadeIn(tween(130)))
                        .togetherWith(slideOutVertically(tween(120)) { -it / 2 } + fadeOut(tween(100)))
                    } else {
                      EnterTransition.None togetherWith ExitTransition.None
                    }
                  },
                  label = "viewer_subtitle_roll",
                ) { displaySub ->
                  Text(
                    text = displaySub,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("viewer_page_subtitle"),
                  )
                }
              }
            }
          }

          // Right solid grouped action pill
          val actions =
            buildList {
              add(
                ViewerToolbarAction(
                  icon = Icons.Rounded.Search,
                  contentDescription = stringResource(R.string.viewer_search_cd),
                  testTag = "viewer_search_button",
                  active = isSearchActive,
                  onClick = onToggleSearch,
                )
              )
              if (showRotateAction) {
                add(
                  ViewerToolbarAction(
                    icon = Icons.Rounded.Rotate90DegreesCw,
                    contentDescription = stringResource(R.string.viewer_image_rotate_cd),
                    testTag = "viewer_rotate_button",
                    onClick = onRotateImage,
                  )
                )
              }
              add(
                ViewerToolbarAction(
                  icon = Icons.Rounded.Share,
                  contentDescription = stringResource(R.string.viewer_share_cd),
                  testTag = "viewer_share_button",
                  onClick = onShare,
                )
              )
              add(
                ViewerToolbarAction(
                  icon = Icons.Rounded.Tune,
                  contentDescription = stringResource(R.string.viewer_options_cd),
                  testTag = "viewer_options_button",
                  active = isBookmarked,
                  onClick = onOpenOptions,
                )
              )
            }
          ViewerSolidToolbarGroup(actions = actions)
        }
      }

      // Minimized high-contrast solid corner pills when scrolling down (clean handoff at 0.42f..1f)
      if (clampedCollapse > 0.42f) {
        val minProgress = ((clampedCollapse - 0.42f) / 0.58f).coerceIn(0f, 1f)
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .graphicsLayer {
                alpha = minProgress
                translationY = (1f - minProgress) * -14.dp.toPx()
                val s = 0.92f + 0.08f * minProgress
                scaleX = s
                scaleY = s
              }
              .testTag("viewer_minimized_top_bar"),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          ViewerSolidSurface(
            cornerRadius = 24.dp,
            shape = CircleShape,
            shadowElevation = 8.dp,
            onClick = {
              haptics.tap()
              onNavigateBack()
            },
            hapticOnClick = false,
            clickRole = Role.Button,
            modifier =
              Modifier.defaultMinSize(minWidth = PrismDimens.TouchTargetMin, minHeight = PrismDimens.TouchTargetMin)
                .size(PrismDimens.TouchTargetMin)
                .testTag("viewer_minimized_back_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
              contentDescription = stringResource(R.string.viewer_back_cd),
              tint = colorScheme.onSurface,
              modifier = Modifier.align(Alignment.Center).size(20.dp),
            )
          }

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            AnimatedVisibility(
              visible = currentZoom > 1.05f,
              enter =
                if (motion) scaleIn(initialScale = 0.85f) + fadeIn(tween(140))
                else EnterTransition.None,
              exit =
                if (motion) scaleOut(targetScale = 0.85f) + fadeOut(tween(110))
                else ExitTransition.None,
            ) {
              ViewerZoomResetPill(
                zoom = currentZoom,
                onResetZoom = onResetZoom,
              )
            }

            ViewerSolidSurface(
              cornerRadius = 24.dp,
              shape = CircleShape,
              shadowElevation = 8.dp,
              onClick = {
                haptics.tap()
                onExpandFromMinimized()
              },
              hapticOnClick = false,
              clickRole = Role.Button,
              modifier =
                Modifier.defaultMinSize(minHeight = PrismDimens.TouchTargetMin)
                  .height(PrismDimens.TouchTargetMin)
                  .graphicsLayer {
                    scaleX = minimizedPillPulse
                    scaleY = minimizedPillPulse
                  }
                  .testTag("viewer_minimized_page_pill"),
            ) {
              Box(
                modifier =
                  Modifier.fillMaxHeight().drawBehind {
                    if (pageCount > 0) {
                      val liveFraction =
                        if (pageCount > 1) {
                          (continuousPageState.value / (pageCount - 1).toFloat()).coerceIn(0f, 1f)
                        } else 1f
                      val barHeight = 3.dp.toPx()
                      val y = size.height - barHeight
                      drawRect(
                        color = progressTrackColor,
                        topLeft = Offset(0f, y),
                        size = Size(size.width, barHeight),
                      )
                      drawRect(
                        color = progressFillColor,
                        topLeft = Offset(0f, y),
                        size = Size(size.width * liveFraction, barHeight),
                      )
                    }
                  },
              ) {
                Row(
                  modifier = Modifier.align(Alignment.Center).padding(horizontal = 14.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                  if (isBookmarked) {
                    Icon(
                      imageVector = Icons.Rounded.Bookmark,
                      contentDescription = null,
                      tint = colorScheme.primary,
                      modifier = Modifier.size(15.dp),
                    )
                  }
                  AnimatedContent(
                    targetState = compactPageText,
                    transitionSpec = {
                      if (motion) {
                        (slideInVertically(tween(130)) { it / 2 } + fadeIn(tween(110)))
                          .togetherWith(slideOutVertically(tween(100)) { -it / 2 } + fadeOut(tween(90)))
                      } else {
                        EnterTransition.None togetherWith ExitTransition.None
                      }
                    },
                    label = "viewer_compact_page_roll",
                  ) { pageLabel ->
                    Text(
                      text = pageLabel,
                      style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                      color = colorScheme.onSurface,
                      maxLines = 1,
                    )
                  }
                  if (pageCount > 1) {
                    Box(
                      modifier =
                        Modifier.clip(CircleShape)
                          .background(colorScheme.primaryContainer)
                          .padding(horizontal = 7.dp, vertical = 2.dp),
                    ) {
                      Text(
                        text = stringResource(R.string.viewer_page_percent_format, percentInt),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = colorScheme.onPrimaryContainer,
                        maxLines = 1,
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

    // Zoom reset pill when top bar is expanded and document is zoomed in
    if (clampedCollapse <= 0.42f && currentZoom > 1.05f && !isSearchActive) {
      Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        ViewerZoomResetPill(
          zoom = currentZoom,
          onResetZoom = onResetZoom,
        )
      }
    }
  }
}

@Composable
internal fun ViewerZoomResetPill(
  zoom: Float,
  onResetZoom: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme
  val haptics = rememberPrismHaptics()
  val resetCd = stringResource(R.string.viewer_reset_zoom_cd)

  ViewerSolidSurface(
    cornerRadius = 22.dp,
    shape = CircleShape,
    emphasized = true,
    shadowElevation = 8.dp,
    onClick = {
      haptics.segmentTick()
      onResetZoom()
    },
    hapticOnClick = false,
    clickRole = Role.Button,
    modifier =
      modifier
        .defaultMinSize(minHeight = PrismDimens.TouchTargetMin)
        .height(44.dp)
        .semantics { contentDescription = resetCd }
        .testTag("viewer_zoom_reset_pill"),
  ) {
    Row(
      modifier = Modifier.align(Alignment.Center).padding(horizontal = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
      Icon(
        imageVector = Icons.Rounded.ZoomOutMap,
        contentDescription = null,
        tint = colorScheme.onPrimaryContainer,
        modifier = Modifier.size(16.dp),
      )
      Text(
        text = stringResource(R.string.viewer_zoom_reset_format, zoom),
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        color = colorScheme.onPrimaryContainer,
        maxLines = 1,
      )
    }
  }
}

@Composable
internal fun ViewerSearchSolidBar(
  query: String,
  matchCount: Int,
  currentMatchIndex: Int,
  isSearching: Boolean,
  indexedPages: Int,
  totalPages: Int,
  onQueryChange: (String) -> Unit,
  onPrevMatch: () -> Unit,
  onNextMatch: () -> Unit,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme
  val motion = LocalGlassMotion.current
  val haptics = rememberPrismHaptics()
  val focusRequester = remember { FocusRequester() }

  LaunchedEffect(Unit) {
    runCatching { focusRequester.requestFocus() }
  }

  val counterText =
    if (query.isBlank()) {
      ""
    } else if (matchCount == 0) {
      stringResource(R.string.viewer_search_no_matches)
    } else {
      stringResource(
        R.string.viewer_search_match_counter,
        (currentMatchIndex + 1).coerceIn(1, matchCount),
        matchCount,
      )
    }

  ViewerSolidSurface(
    cornerRadius = 24.dp,
    shadowElevation = 10.dp,
    modifier = modifier.fillMaxWidth().testTag("viewer_search_bar"),
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp)
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Icon(
          imageVector = Icons.Rounded.Search,
          contentDescription = null,
          tint = colorScheme.primary,
          modifier = Modifier.size(20.dp),
        )
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
          if (query.isEmpty()) {
            Text(
              text = stringResource(R.string.viewer_search_placeholder),
              style = MaterialTheme.typography.bodyMedium,
              color = colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = colorScheme.onSurface),
            cursorBrush = SolidColor(colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onNextMatch() }),
            modifier =
              Modifier.fillMaxWidth()
                .focusRequester(focusRequester)
                .testTag("viewer_search_input"),
          )
        }
        if (counterText.isNotEmpty()) {
          AnimatedContent(
            targetState = counterText,
            transitionSpec = {
              if (motion) {
                (slideInVertically(tween(120)) { it / 2 } + fadeIn(tween(100)))
                  .togetherWith(slideOutVertically(tween(100)) { -it / 2 } + fadeOut(tween(80)))
              } else {
                EnterTransition.None togetherWith ExitTransition.None
              }
            },
            label = "viewer_search_counter_roll",
          ) { animatedCounter ->
            Box(
              modifier =
                Modifier.clip(CircleShape)
                  .background(
                    if (matchCount > 0) colorScheme.primaryContainer
                    else colorScheme.surfaceVariant,
                  )
                  .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
              Text(
                text = animatedCounter,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color =
                  if (matchCount > 0) colorScheme.onPrimaryContainer
                  else colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.testTag("viewer_search_counter"),
              )
            }
          }
        }
        IconButton(
          onClick = {
            haptics.tap()
            onPrevMatch()
          },
          enabled = matchCount > 0,
          modifier =
            Modifier.size(PrismDimens.TouchTargetMin)
              .defaultMinSize(PrismDimens.TouchTargetMin, PrismDimens.TouchTargetMin)
              .testTag("viewer_search_prev"),
        ) {
          Icon(
            imageVector = Icons.Rounded.KeyboardArrowUp,
            contentDescription = stringResource(R.string.viewer_search_prev_cd),
            tint = if (matchCount > 0) colorScheme.onSurface else colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
          )
        }
        IconButton(
          onClick = {
            haptics.tap()
            onNextMatch()
          },
          enabled = matchCount > 0,
          modifier =
            Modifier.size(PrismDimens.TouchTargetMin)
              .defaultMinSize(PrismDimens.TouchTargetMin, PrismDimens.TouchTargetMin)
              .testTag("viewer_search_next"),
        ) {
          Icon(
            imageVector = Icons.Rounded.KeyboardArrowDown,
            contentDescription = stringResource(R.string.viewer_search_next_cd),
            tint = if (matchCount > 0) colorScheme.onSurface else colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
          )
        }
        IconButton(
          onClick = {
            haptics.tap()
            onClose()
          },
          modifier =
            Modifier.size(PrismDimens.TouchTargetMin)
              .defaultMinSize(PrismDimens.TouchTargetMin, PrismDimens.TouchTargetMin)
              .testTag("viewer_search_close"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = stringResource(R.string.viewer_search_close_cd),
            tint = colorScheme.onSurface,
          )
        }
      }

      if (isSearching && totalPages > 1) {
        val progress = (indexedPages.toFloat() / totalPages.toFloat()).coerceIn(0f, 1f)
        LinearProgressIndicator(
          progress = { progress },
          modifier = Modifier.fillMaxWidth().height(2.5.dp).padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(4.dp))
      }
    }
  }
}

/**
 * Right-edge solid scrubber track that reads [continuousPageState] directly inside `graphicsLayer`
 * and `drawBehind` for zero-recomposition 120fps tracking during PDF scroll, displays a live page
 * pill while scrolling, and pops open a rich solid preview card (with miniature thumbnail, page/%,
 * and chapter name) while dragging with haptic ticks per page.
 */
@Composable
internal fun ViewerRightEdgeScrubber(
  pageCount: Int,
  currentPage: Int,
  continuousPageState: State<Float>,
  pdfController: PdfDocumentController,
  paperMode: ReaderPaperMode,
  currentChapter: String?,
  isActivelyScrolling: Boolean,
  isBookmarked: Boolean,
  onSelectPage: (Int) -> Unit,
  onScrubbingChanged: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  if (pageCount <= 1) return
  val colorScheme = MaterialTheme.colorScheme
  val motion = LocalGlassMotion.current
  val haptics = rememberPrismHaptics()
  val density = LocalDensity.current

  var isDragging by remember { mutableStateOf(false) }
  var dragFraction by remember { mutableFloatStateOf(0f) }

  val handleWidth by
    animateDpAsState(
      targetValue = when {
        isDragging -> 32.dp
        isActivelyScrolling -> 28.dp
        else -> 24.dp
      },
      animationSpec = if (motion) spring(dampingRatio = 0.70f, stiffness = 440f) else snap(),
      label = "scrubber_handle_width",
    )

  val handleScale by
    animateFloatAsState(
      targetValue = if (isDragging) 1.08f else 1f,
      animationSpec = if (motion) spring(dampingRatio = 0.65f, stiffness = 440f) else snap(),
      label = "scrubber_handle_scale",
    )

  val railColor by
    animateColorAsState(
      targetValue =
        if (isDragging || isActivelyScrolling) colorScheme.primary.copy(alpha = 0.28f)
        else colorScheme.outlineVariant,
      label = "scrubber_rail_color",
    )

  val latestPageCount by rememberUpdatedState(pageCount)
  val latestOnSelectPage by rememberUpdatedState(onSelectPage)
  val latestOnScrubbingChanged by rememberUpdatedState(onScrubbingChanged)
  val scrubberCd = stringResource(R.string.viewer_scrubber_cd)
  val percentInt = ((currentPage.toFloat() / (pageCount - 1).coerceAtLeast(1)) * 100f).roundToInt().coerceIn(0, 100)

  BoxWithConstraints(
    modifier = modifier.fillMaxHeight().widthIn(min = 56.dp),
    contentAlignment = Alignment.TopEnd,
  ) {
    val trackHeightPx = with(density) { (maxHeight - 44.dp).coerceAtLeast(48.dp).toPx() }
    val thumbSizePx = with(density) { 44.dp.toPx() }
    val usableSpan = (trackHeightPx - thumbSizePx).coerceAtLeast(1f)
    val maxBubbleY = (usableSpan - with(density) { 44.dp.toPx() }).coerceAtLeast(0f)

    fun currentFraction(): Float {
      return if (isDragging) {
        dragFraction
      } else {
        (continuousPageState.value / (pageCount - 1).coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
      }
    }

    // Compact live floating page pill while actively scrolling (before user grabs scrubber)
    AnimatedVisibility(
      visible = isActivelyScrolling && !isDragging,
      enter =
        if (motion) fadeIn(tween(110)) + scaleIn(initialScale = 0.85f)
        else EnterTransition.None,
      exit =
        if (motion) fadeOut(tween(160)) + scaleOut(targetScale = 0.85f)
        else ExitTransition.None,
      modifier =
        Modifier.align(Alignment.TopEnd)
          .graphicsLayer {
            translationX = -with(density) { 46.dp.toPx() }
            translationY = (currentFraction() * usableSpan + thumbSizePx * 0.12f).coerceIn(0f, usableSpan)
          },
    ) {
      ViewerSolidSurface(
        cornerRadius = 16.dp,
        shape = CircleShape,
        emphasized = true,
        shadowElevation = 6.dp,
      ) {
        Text(
          text = "${currentPage + 1}",
          style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
          color = colorScheme.onPrimaryContainer,
          modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
        )
      }
    }

    // Rich solid floating preview card with miniature page thumbnail while dragging scrubber
    AnimatedVisibility(
      visible = isDragging,
      enter =
        if (motion) fadeIn(tween(90)) + scaleIn(initialScale = 0.88f)
        else EnterTransition.None,
      exit =
        if (motion) fadeOut(tween(120)) + scaleOut(targetScale = 0.88f)
        else ExitTransition.None,
      modifier =
        Modifier.align(Alignment.TopEnd)
          .graphicsLayer {
            translationX = -with(density) { 54.dp.toPx() }
            translationY = (currentFraction() * usableSpan).coerceIn(0f, maxBubbleY)
          },
    ) {
      val previewThumb by
        produceState<Bitmap?>(
          initialValue = pdfController.peekCachedThumbnail(currentPage),
          currentPage,
          pdfController,
        ) {
          value = pdfController.peekCachedThumbnail(currentPage) ?: pdfController.renderThumbnail(currentPage)
        }

      ViewerSolidSurface(
        cornerRadius = 20.dp,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 12.dp,
        modifier = Modifier.testTag("viewer_scrubber_bubble"),
      ) {
        Row(
          modifier = Modifier.padding(10.dp),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          // Miniature crisp live page preview
          Box(
            modifier =
              Modifier.size(width = 40.dp, height = 54.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(paperSurfaceColor(paperMode))
                .border(1.dp, colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
          ) {
            val bmp = previewThumb
            if (bmp != null) {
              Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = paperModeColorFilter(paperMode),
                modifier = Modifier.fillMaxSize(),
              )
            }
          }

          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Text(
                text = stringResource(R.string.viewer_page_subtitle_format, currentPage + 1, pageCount),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = colorScheme.onSurface,
                maxLines = 1,
              )
              Box(
                modifier =
                  Modifier.clip(CircleShape)
                    .background(colorScheme.primaryContainer)
                    .padding(horizontal = 6.dp, vertical = 1.dp),
              ) {
                Text(
                  text = stringResource(R.string.viewer_page_percent_format, percentInt),
                  style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                  color = colorScheme.onPrimaryContainer,
                )
              }
              if (isBookmarked) {
                Icon(
                  imageVector = Icons.Rounded.Bookmark,
                  contentDescription = null,
                  tint = colorScheme.primary,
                  modifier = Modifier.size(14.dp),
                )
              }
            }
            if (!currentChapter.isNullOrBlank()) {
              Text(
                text = currentChapter,
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 160.dp),
              )
            }
            Box(
              modifier =
                Modifier.width(110.dp)
                  .height(4.dp)
                  .clip(CircleShape)
                  .background(colorScheme.surfaceVariant),
            ) {
              Box(
                modifier =
                  Modifier.fillMaxWidth(currentFraction().coerceIn(0.04f, 1f))
                    .fillMaxHeight()
                    .background(colorScheme.primary),
              )
            }
          }
        }
      }
    }

    // Vertical touch-safe scrubber rail + solid handle
    Box(
      modifier =
        Modifier.width(48.dp)
          .fillMaxHeight()
          .semantics { contentDescription = scrubberCd }
          .pointerInput(pageCount) {
            var lastTickPage = -1
            detectVerticalDragGestures(
              onDragStart = { offset ->
                isDragging = true
                latestOnScrubbingChanged(true)
                val f = ((offset.y - thumbSizePx * 0.5f) / usableSpan).coerceIn(0f, 1f)
                dragFraction = f
                val target = (f * (latestPageCount - 1)).roundToInt().coerceIn(0, latestPageCount - 1)
                lastTickPage = target
                haptics.dragStart()
                latestOnSelectPage(target)
              },
              onDragEnd = {
                isDragging = false
                latestOnScrubbingChanged(false)
              },
              onDragCancel = {
                isDragging = false
                latestOnScrubbingChanged(false)
              },
              onVerticalDrag = { change, _ ->
                change.consume()
                val f = ((change.position.y - thumbSizePx * 0.5f) / usableSpan).coerceIn(0f, 1f)
                dragFraction = f
                val target = (f * (latestPageCount - 1)).roundToInt().coerceIn(0, latestPageCount - 1)
                if (target != lastTickPage) {
                  lastTickPage = target
                  haptics.segmentTick()
                  latestOnSelectPage(target)
                }
              },
            )
          }
          .testTag("viewer_page_scrubber"),
      contentAlignment = Alignment.TopEnd,
    ) {
      // Track rail with active continuous progress fill (drawn in draw phase)
      Box(
        modifier =
          Modifier.align(Alignment.CenterEnd)
            .padding(end = 10.dp)
            .width(5.dp)
            .fillMaxHeight()
            .clip(CircleShape)
            .background(railColor)
            .drawBehind {
              val f = currentFraction()
              drawRoundRect(
                color = colorScheme.primary,
                topLeft = Offset.Zero,
                size = Size(size.width, (size.height * f).coerceAtLeast(6f)),
                cornerRadius = CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx()),
              )
            },
      )

      // Solid elevated drag handle pill (positioned in graphicsLayer for zero recomposition)
      ViewerSolidSurface(
        cornerRadius = 16.dp,
        shape = CircleShape,
        emphasized = isDragging || isActivelyScrolling,
        shadowElevation = 6.dp,
        modifier =
          Modifier.align(Alignment.TopEnd)
            .padding(end = 2.dp)
            .graphicsLayer {
              translationY = currentFraction() * usableSpan
              scaleX = handleScale
              scaleY = handleScale
            }
            .size(width = handleWidth, height = 44.dp),
      ) {
        Column(
          modifier = Modifier.align(Alignment.Center),
          verticalArrangement = Arrangement.spacedBy(3.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          repeat(3) {
            Box(
              modifier =
                Modifier.width(10.dp)
                  .height(2.dp)
                  .clip(CircleShape)
                  .background(
                    if (isDragging || isActivelyScrolling) colorScheme.onPrimaryContainer
                    else colorScheme.onSurfaceVariant,
                  ),
            )
          }
        }
      }
    }
  }
}

/**
 * Floating solid bottom thumbnail strip rendering real lazy PDF page bitmaps via
 * [PdfDocumentController]. Auto-centers the active page smoothly whenever scrolled,
 * scrubbed, or jumped via search/outline, and highlights bookmarked pages.
 */
@Composable
internal fun ViewerThumbnailStripBar(
  pageCount: Int,
  currentPage: Int,
  bookmarkedPages: Set<Int>,
  pdfController: PdfDocumentController,
  paperMode: ReaderPaperMode,
  isScrubbing: Boolean,
  onSelectPage: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  if (pageCount <= 0) return
  val colorScheme = MaterialTheme.colorScheme
  val motion = LocalGlassMotion.current
  val haptics = rememberPrismHaptics()
  val rowState = rememberLazyListState()
  val density = LocalDensity.current

  LaunchedEffect(currentPage, pageCount, isScrubbing) {
    if (currentPage in 0 until pageCount) {
      val info = rowState.layoutInfo
      val centerPx = (info.viewportSize.width / 2 - with(density) { 24.dp.toPx() }).roundToInt().coerceAtLeast(0)
      if (!motion || isScrubbing) {
        rowState.scrollToItem(currentPage, scrollOffset = -centerPx)
      } else {
        rowState.animateScrollToItem(currentPage, scrollOffset = -centerPx)
      }
    }
  }

  ViewerSolidSurface(
    cornerRadius = 24.dp,
    shadowElevation = 10.dp,
    modifier = modifier.fillMaxWidth().testTag("glass_page_thumbnail_strip"),
  ) {
    LazyRow(
      state = rowState,
      contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      items(count = pageCount, key = { it }) { pageIdx ->
        val pageNumber = pageIdx + 1
        val isActive = pageIdx == currentPage
        val isBookmarked = pageIdx in bookmarkedPages
        val borderColor by
          animateColorAsState(
            targetValue = if (isActive) colorScheme.primary else colorScheme.outlineVariant,
            label = "viewer_thumb_border_$pageIdx",
          )
        val thumbScale by
          animateFloatAsState(
            targetValue = if (isActive) 1.09f else 0.96f,
            animationSpec = if (motion) spring(dampingRatio = 0.68f, stiffness = 460f) else snap(),
            label = "viewer_thumb_scale_$pageIdx",
          )
        val thumbAlpha by
          animateFloatAsState(
            targetValue = if (isActive) 1f else 0.88f,
            animationSpec = if (motion) tween(160) else snap(),
            label = "viewer_thumb_alpha_$pageIdx",
          )

        val thumbBitmap by
          produceState<Bitmap?>(initialValue = pdfController.peekCachedThumbnail(pageIdx), pageIdx, pdfController) {
            value = pdfController.peekCachedThumbnail(pageIdx) ?: pdfController.renderThumbnail(pageIdx)
          }

        Box(
          modifier =
            Modifier.size(width = 46.dp, height = 62.dp)
              .defaultMinSize(PrismDimens.TouchTargetMin, PrismDimens.TouchTargetMin)
              .graphicsLayer {
                scaleX = thumbScale
                scaleY = thumbScale
                alpha = thumbAlpha
              }
              .clip(RoundedCornerShape(10.dp))
              .background(paperSurfaceColor(paperMode))
              .border(
                width = if (isActive) 2.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(10.dp),
              )
              .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = colorScheme.primary),
                role = Role.Button,
                onClick = {
                  haptics.segmentTick()
                  onSelectPage(pageIdx)
                },
              )
              .semantics {
                selected = isActive
                contentDescription = "Page $pageNumber of $pageCount"
              }
              .testTag("thumbnail_page_$pageNumber"),
          contentAlignment = Alignment.Center,
        ) {
          val bmp = thumbBitmap
          if (bmp != null) {
            Image(
              bitmap = bmp.asImageBitmap(),
              contentDescription = null,
              contentScale = ContentScale.Crop,
              colorFilter = paperModeColorFilter(paperMode),
              modifier = Modifier.fillMaxSize(),
            )
          }
          if (isBookmarked) {
            Box(
              modifier =
                Modifier.align(Alignment.TopEnd)
                  .padding(3.dp)
                  .size(14.dp)
                  .clip(CircleShape)
                  .background(colorScheme.primary),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Rounded.Bookmark,
                contentDescription = null,
                tint = colorScheme.onPrimary,
                modifier = Modifier.size(9.dp),
              )
            }
          }
          Box(
            modifier =
              Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                  if (isActive) colorScheme.primary
                  else colorScheme.surfaceVariant,
                )
                .padding(vertical = 2.dp),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = "$pageNumber",
              style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
              color = if (isActive) colorScheme.onPrimary else colorScheme.onSurfaceVariant,
              maxLines = 1,
            )
          }
        }
      }
    }
  }
}

@Composable
internal fun ViewerReaderOptionsSheet(
  visible: Boolean,
  detent: GlassSheetDetent,
  onDetentChange: (GlassSheetDetent) -> Unit,
  onDismiss: () -> Unit,
  kind: ViewerDocumentKind,
  currentPage: Int,
  isBookmarked: Boolean,
  scrollMode: PdfScrollMode,
  paperMode: ReaderPaperMode,
  hasExternalOpener: Boolean,
  onPaperModeChange: (ReaderPaperMode) -> Unit,
  onScrollModeChange: (PdfScrollMode) -> Unit,
  onToggleBookmark: () -> Unit,
  onOpenOutline: () -> Unit,
  onOpenAnnotate: () -> Unit,
  onOpenExternal: () -> Unit,
) {
  if (visible) {
    BackHandler(onBack = onDismiss)
  }
  val colorScheme = MaterialTheme.colorScheme
  val haptics = rememberPrismHaptics()

  GlassBottomSheet(
    visible = visible,
    detent = detent,
    onDetentChange = onDetentChange,
    onDismiss = onDismiss,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = stringResource(R.string.viewer_options_title),
        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
        color = colorScheme.onSurface,
      )
      TextButton(
        onClick = {
          haptics.tap()
          onDismiss()
        },
        modifier = Modifier.defaultMinSize(minWidth = 56.dp, minHeight = PrismDimens.TouchTargetMin),
      ) {
        Text(stringResource(R.string.viewer_options_done))
      }
    }

    Spacer(Modifier.height(12.dp))

    Column(
      modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      if (kind == ViewerDocumentKind.PDF || kind != ViewerDocumentKind.IMAGE) {
        Text(
          text = stringResource(R.string.viewer_section_paper_theme),
          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
          color = colorScheme.onSurfaceVariant,
        )
        GlassSegmentedControl(
          options =
            listOf(
              stringResource(R.string.viewer_paper_light),
              stringResource(R.string.viewer_paper_sepia),
              stringResource(R.string.viewer_paper_dark),
            ),
          selectedIndex = paperMode.ordinal,
          onSelect = { idx ->
            onPaperModeChange(ReaderPaperMode.entries.getOrElse(idx) { ReaderPaperMode.LIGHT })
          },
          modifier = Modifier.fillMaxWidth().testTag("viewer_paper_mode_segmented"),
        )
      }

      if (kind == ViewerDocumentKind.PDF) {
        Text(
          text = stringResource(R.string.viewer_section_layout),
          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
          color = colorScheme.onSurfaceVariant,
        )
        GlassSegmentedControl(
          options =
            listOf(
              stringResource(R.string.viewer_layout_continuous),
              stringResource(R.string.viewer_layout_paged),
            ),
          selectedIndex = scrollMode.ordinal,
          onSelect = { idx ->
            onScrollModeChange(PdfScrollMode.entries.getOrElse(idx) { PdfScrollMode.CONTINUOUS })
          },
          modifier = Modifier.fillMaxWidth().testTag("viewer_scroll_mode_segmented"),
        )

        Column {
          ConnectedGlassCardSlice(
            isFirst = true,
            isLast = false,
            modifier =
              Modifier.fillMaxWidth()
                .glassClickable(role = Role.Button) {
                  onToggleBookmark()
                  onDismiss()
                }
                .testTag("viewer_toggle_bookmark_row"),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Icon(
                imageVector = if (isBookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                contentDescription = null,
                tint = colorScheme.primary,
              )
              Text(
                text =
                  if (isBookmarked) stringResource(R.string.viewer_action_remove_bookmark_page, currentPage + 1)
                  else stringResource(R.string.viewer_action_bookmark_page, currentPage + 1),
                style = MaterialTheme.typography.bodyLarge,
                color = colorScheme.onSurface,
                modifier = Modifier.weight(1f),
              )
            }
          }
          ConnectedGlassCardSlice(
            isFirst = false,
            isLast = false,
            modifier =
              Modifier.fillMaxWidth()
                .glassClickable(role = Role.Button) {
                  onDismiss()
                  onOpenOutline()
                }
                .testTag("viewer_open_outline_row"),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                contentDescription = null,
                tint = colorScheme.primary,
              )
              Text(
                text = stringResource(R.string.viewer_action_outline),
                style = MaterialTheme.typography.bodyLarge,
                color = colorScheme.onSurface,
                modifier = Modifier.weight(1f),
              )
            }
          }
          ConnectedGlassCardSlice(
            isFirst = false,
            isLast = !hasExternalOpener,
            modifier =
              Modifier.fillMaxWidth()
                .glassClickable(role = Role.Button) {
                  onDismiss()
                  onOpenAnnotate()
                }
                .testTag("viewer_editor_route_button"),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.EditNote,
                contentDescription = null,
                tint = colorScheme.primary,
              )
              Text(
                text = stringResource(R.string.viewer_action_annotate),
                style = MaterialTheme.typography.bodyLarge,
                color = colorScheme.onSurface,
                modifier = Modifier.weight(1f),
              )
            }
          }
          if (hasExternalOpener) {
            ConnectedGlassCardSlice(
              isFirst = false,
              isLast = true,
              modifier =
                Modifier.fillMaxWidth()
                  .glassClickable(role = Role.Button) {
                    onDismiss()
                    onOpenExternal()
                  }
                  .testTag("viewer_open_external_button"),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                Icon(
                  imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                  contentDescription = null,
                  tint = colorScheme.onSurfaceVariant,
                )
                Text(
                  text = stringResource(R.string.viewer_action_open_external),
                  style = MaterialTheme.typography.bodyLarge,
                  color = colorScheme.onSurface,
                  modifier = Modifier.weight(1f),
                )
              }
            }
          }
        }
      } else if (hasExternalOpener) {
        ConnectedGlassCardSlice(
          isFirst = true,
          isLast = true,
          modifier =
            Modifier.fillMaxWidth()
              .glassClickable(role = Role.Button) {
                onDismiss()
                onOpenExternal()
              }
              .testTag("viewer_open_external_button"),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
              contentDescription = null,
              tint = colorScheme.primary,
            )
            Text(
              text = stringResource(R.string.viewer_action_open_external),
              style = MaterialTheme.typography.bodyLarge,
              color = colorScheme.onSurface,
              modifier = Modifier.weight(1f),
            )
          }
        }
      }
      Spacer(Modifier.height(12.dp))
    }
  }
}

@Composable
internal fun ViewerOutlineBookmarksSheet(
  visible: Boolean,
  detent: GlassSheetDetent,
  onDetentChange: (GlassSheetDetent) -> Unit,
  onDismiss: () -> Unit,
  outlineNodes: List<PdfOutlineNode>,
  bookmarks: List<DocumentBookmarkEntity>,
  currentPage: Int,
  currentPageBookmarked: Boolean,
  onToggleCurrentBookmark: () -> Unit,
  onRemoveBookmark: (Long) -> Unit,
  onJumpToPage: (Int) -> Unit,
) {
  if (visible) {
    BackHandler(onBack = onDismiss)
  }
  val colorScheme = MaterialTheme.colorScheme
  val haptics = rememberPrismHaptics()
  var selectedTab by remember { mutableIntStateOf(if (outlineNodes.isEmpty() && bookmarks.isNotEmpty()) 1 else 0) }

  GlassBottomSheet(
    visible = visible,
    detent = detent,
    onDetentChange = onDetentChange,
    onDismiss = onDismiss,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = stringResource(R.string.viewer_outline_title),
        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
        color = colorScheme.onSurface,
        modifier = Modifier.weight(1f),
      )
      IconButton(
        onClick = {
          haptics.segmentTick()
          onToggleCurrentBookmark()
        },
        modifier =
          Modifier.size(PrismDimens.TouchTargetMin)
            .defaultMinSize(PrismDimens.TouchTargetMin, PrismDimens.TouchTargetMin)
            .testTag("viewer_sheet_bookmark_button"),
      ) {
        Icon(
          imageVector = if (currentPageBookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
          contentDescription =
            if (currentPageBookmarked) stringResource(R.string.viewer_bookmark_remove_cd)
            else stringResource(R.string.viewer_bookmark_add_cd),
          tint = if (currentPageBookmarked) colorScheme.primary else colorScheme.onSurface,
        )
      }
      TextButton(
        onClick = {
          haptics.tap()
          onDismiss()
        },
        modifier = Modifier.defaultMinSize(minWidth = 56.dp, minHeight = PrismDimens.TouchTargetMin),
      ) {
        Text(stringResource(R.string.viewer_options_done))
      }
    }

    Spacer(Modifier.height(10.dp))

    GlassSegmentedControl(
      options =
        listOf(
          stringResource(R.string.viewer_outline_tab_toc),
          stringResource(R.string.viewer_outline_tab_bookmarks),
        ),
      selectedIndex = selectedTab,
      onSelect = { selectedTab = it },
      modifier = Modifier.fillMaxWidth().testTag("viewer_outline_tabs"),
    )

    Spacer(Modifier.height(12.dp))

    if (selectedTab == 0) {
      if (outlineNodes.isEmpty()) {
        Box(
          modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = stringResource(R.string.viewer_outline_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )
        }
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxWidth().weight(1f, fill = false).testTag("viewer_outline_list"),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          items(outlineNodes) { node ->
            val isCurrent = node.pageIndex == currentPage
            ConnectedGlassCardSlice(
              isFirst = true,
              isLast = true,
              accentTint = if (isCurrent) colorScheme.primary else null,
              modifier =
                Modifier.fillMaxWidth().glassClickable(role = Role.Button) {
                  onJumpToPage(node.pageIndex)
                  onDismiss()
                },
            ) {
              Row(
                modifier =
                  Modifier.fillMaxWidth()
                    .padding(start = (16 + node.depth * 14).dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Text(
                  text = node.title,
                  style =
                    MaterialTheme.typography.bodyMedium.copy(
                      fontWeight = if (node.depth == 0 || isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                  color = if (isCurrent) colorScheme.primary else colorScheme.onSurface,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.weight(1f),
                )
                Text(
                  text = "${node.pageIndex + 1}",
                  style = MaterialTheme.typography.labelMedium,
                  color = colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      }
    } else {
      if (bookmarks.isEmpty()) {
        Box(
          modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = stringResource(R.string.viewer_bookmarks_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )
        }
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxWidth().weight(1f, fill = false).testTag("viewer_bookmarks_list"),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          items(bookmarks, key = { it.id }) { bm ->
            ConnectedGlassCardSlice(
              isFirst = true,
              isLast = true,
              accentTint = if (bm.pageIndex == currentPage) colorScheme.primary else null,
              modifier =
                Modifier.fillMaxWidth().glassClickable(role = Role.Button) {
                  onJumpToPage(bm.pageIndex)
                  onDismiss()
                },
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Icon(
                  imageVector = Icons.Rounded.Bookmark,
                  contentDescription = null,
                  tint = colorScheme.primary,
                )
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = bm.label,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colorScheme.onSurface,
                  )
                  Text(
                    text = "Page ${bm.pageIndex + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                  )
                }
                IconButton(
                  onClick = {
                    haptics.tap()
                    onRemoveBookmark(bm.id)
                  },
                  modifier = Modifier.size(PrismDimens.TouchTargetMin),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = stringResource(R.string.viewer_bookmark_remove_cd),
                    tint = colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

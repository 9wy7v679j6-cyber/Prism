package com.prism.docs.feature.viewer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.WrapText
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.TextDecrease
import androidx.compose.material.icons.rounded.TextIncrease
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.prism.docs.core.data.model.IncomingDocumentRequest
import com.prism.docs.core.ui.glass.GlassElevation
import com.prism.docs.core.ui.glass.GlassSurface
import com.prism.docs.core.ui.glass.LocalGlassMotion
import com.prism.docs.core.ui.glass.glassClickable
import com.prism.docs.core.ui.theme.PrismDimens
import kotlin.math.abs

@Composable
internal fun ImageDocumentViewer(
  uriString: String,
  displayName: String,
  rotationDegrees: Int,
  paperMode: ReaderPaperMode,
  onToggleChrome: () -> Unit,
  onOpenExternal: (() -> Unit)?,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val motion = LocalGlassMotion.current
  val animatedRotation by
    animateFloatAsState(
      targetValue = rotationDegrees.toFloat(),
      animationSpec = if (motion) spring(dampingRatio = 0.8f, stiffness = 360f) else snap(),
      label = "image_rotation",
    )

  var zoom by remember(uriString) { mutableFloatStateOf(1f) }
  var pan by remember(uriString) { mutableStateOf(Offset.Zero) }
  val bg = if (paperMode == ReaderPaperMode.DARK) Color(0xFF0B0D12) else MaterialTheme.colorScheme.background

  Box(
    modifier =
      modifier
        .fillMaxSize()
        .background(bg)
        .pointerInput(Unit) {
          detectTapGestures(
            onTap = { onToggleChrome() },
            onDoubleTap = {
              if (zoom > 1.15f) {
                zoom = 1f
                pan = Offset.Zero
              } else {
                zoom = 2.5f
              }
            },
          )
        }
        .pointerInput(Unit) {
          awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            do {
              val event = awaitPointerEvent()
              val pressed = event.changes.count { it.pressed }
              if (pressed >= 2 || zoom > 1.03f) {
                val zChange = event.calculateZoom()
                val panChange = event.calculatePan()
                val newZoom = (zoom * zChange).coerceIn(1f, 5f)
                val maxX = (size.width * (newZoom - 1f) * 0.5f).coerceAtLeast(0f)
                val maxY = (size.height * (newZoom - 1f) * 0.5f).coerceAtLeast(0f)
                zoom = newZoom
                pan =
                  if (newZoom <= 1.01f) Offset.Zero
                  else Offset(
                    x = (pan.x + panChange.x).coerceIn(-maxX, maxX),
                    y = (pan.y + panChange.y).coerceIn(-maxY, maxY),
                  )
                if (pressed >= 2 || abs(panChange.x) > 1f || abs(panChange.y) > 1f) {
                  event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
              }
            } while (event.changes.any { it.pressed })
          }
        }
        .testTag("viewer_image_canvas"),
    contentAlignment = Alignment.Center,
  ) {
    SubcomposeAsyncImage(
      model = remember(uriString) { ImageRequest.Builder(context).data(uriString).build() },
      contentDescription = displayName,
      contentScale = ContentScale.Fit,
      loading = { CircularProgressIndicator() },
      error = {
        UnsupportedDocumentCard(
          document =
            IncomingDocumentRequest(
              uriString = uriString,
              displayName = displayName,
              mimeType = "image/*",
            ),
          title = stringResource(R.string.viewer_error_title),
          body = stringResource(R.string.viewer_error_body),
          onOpenExternal = onOpenExternal,
        )
      },
      modifier =
        Modifier.fillMaxSize().graphicsLayer {
          scaleX = zoom
          scaleY = zoom
          translationX = pan.x
          translationY = pan.y
          rotationZ = animatedRotation
        },
    )
  }
}

@Composable
internal fun TextCodeDocumentViewer(
  kind: ViewerDocumentKind,
  rawText: String,
  formattedJsonText: String?,
  isTruncated: Boolean,
  loadedBytes: Int,
  paperMode: ReaderPaperMode,
  wrapLines: Boolean,
  fontSizeSp: Float,
  prettyPrintJson: Boolean,
  textMatches: List<IntRange>,
  currentMatchIndex: Int,
  topContentPadding: Dp,
  bottomContentPadding: Dp,
  nestedScrollConnection: NestedScrollConnection,
  onToggleWrap: () -> Unit,
  onTogglePrettyJson: () -> Unit,
  onFontSizeDelta: (Float) -> Unit,
  onToggleChrome: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val paperBg = paperSurfaceColor(paperMode)
  val textColor =
    when (paperMode) {
      ReaderPaperMode.LIGHT -> Color(0xFF171A21)
      ReaderPaperMode.SEPIA -> Color(0xFF3E3022)
      ReaderPaperMode.DARK -> Color(0xFFE5E9F0)
    }
  val subtleColor =
    when (paperMode) {
      ReaderPaperMode.LIGHT -> Color(0xFF64748B)
      ReaderPaperMode.SEPIA -> Color(0xFF7C6650)
      ReaderPaperMode.DARK -> Color(0xFF94A3B8)
    }

  val activeText =
    if (kind == ViewerDocumentKind.JSON && prettyPrintJson && formattedJsonText != null) {
      formattedJsonText
    } else {
      rawText
    }

  Column(
    modifier =
      modifier
        .fillMaxSize()
        .background(paperBg)
        .nestedScroll(nestedScrollConnection)
        .testTag("viewer_text_canvas"),
  ) {
    // Opaque reading controls bar under top chrome
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .padding(top = topContentPadding + 6.dp, start = 14.dp, end = 14.dp, bottom = 6.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (kind != ViewerDocumentKind.CSV) {
        TextControlPill(
          label = stringResource(R.string.viewer_text_wrap_label),
          icon = Icons.AutoMirrored.Rounded.WrapText,
          active = wrapLines,
          onClick = onToggleWrap,
          testTag = "viewer_text_wrap_toggle",
        )
      }
      if (kind == ViewerDocumentKind.JSON && formattedJsonText != null) {
        TextControlPill(
          label = stringResource(R.string.viewer_text_pretty_json_label),
          icon = Icons.Rounded.DataObject,
          active = prettyPrintJson,
          onClick = onTogglePrettyJson,
          testTag = "viewer_json_pretty_toggle",
        )
      }
      Spacer(Modifier.weight(1f))
      IconButton(
        onClick = { onFontSizeDelta(-1.5f) },
        modifier =
          Modifier.size(PrismDimens.TouchTargetMin)
            .defaultMinSize(PrismDimens.TouchTargetMin, PrismDimens.TouchTargetMin)
            .testTag("viewer_font_decrease"),
      ) {
        Icon(
          imageVector = Icons.Rounded.TextDecrease,
          contentDescription = stringResource(R.string.viewer_text_font_decrease_cd),
          tint = textColor,
        )
      }
      Text(
        text = "${fontSizeSp.toInt()}sp",
        style = MaterialTheme.typography.labelMedium,
        color = subtleColor,
      )
      IconButton(
        onClick = { onFontSizeDelta(1.5f) },
        modifier =
          Modifier.size(PrismDimens.TouchTargetMin)
            .defaultMinSize(PrismDimens.TouchTargetMin, PrismDimens.TouchTargetMin)
            .testTag("viewer_font_increase"),
      ) {
        Icon(
          imageVector = Icons.Rounded.TextIncrease,
          contentDescription = stringResource(R.string.viewer_text_font_increase_cd),
          tint = textColor,
        )
      }
    }

    if (isTruncated) {
      Text(
        text = stringResource(R.string.viewer_text_truncated_notice, "${loadedBytes / 1024} KB"),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
      )
    }

    if (kind == ViewerDocumentKind.CSV) {
      CsvTableViewer(
        csvText = activeText,
        textColor = textColor,
        subtleColor = subtleColor,
        fontSizeSp = fontSizeSp,
        bottomPadding = bottomContentPadding,
        onTap = onToggleChrome,
      )
    } else {
      val lines = remember(activeText) { activeText.split('\n') }
      val lineStartOffsets =
        remember(lines) {
          IntArray(lines.size).also { starts ->
            var offset = 0
            for (i in lines.indices) {
              starts[i] = offset
              offset += lines[i].length + 1
            }
          }
        }
      val listState = rememberLazyListState()

      LaunchedEffect(currentMatchIndex, textMatches, lineStartOffsets) {
        if (currentMatchIndex in textMatches.indices) {
          val hit = textMatches[currentMatchIndex]
          val lineIdx =
            lineStartOffsets.binarySearch(hit.first).let { idx ->
              if (idx >= 0) idx else (-idx - 2).coerceIn(0, lines.lastIndex.coerceAtLeast(0))
            }
          listState.animateScrollToItem(lineIdx)
        }
      }

      val hScroll = rememberScrollState()
      val contentModifier =
        Modifier.fillMaxSize()
          .then(if (wrapLines) Modifier else Modifier.horizontalScroll(hScroll))
          .pointerInput(Unit) { detectTapGestures(onTap = { onToggleChrome() }) }

      SelectionContainer(modifier = Modifier.weight(1f)) {
        LazyColumn(
          state = listState,
          contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomContentPadding + 16.dp),
          modifier = contentModifier,
        ) {
          itemsIndexed(lines, key = { idx, _ -> idx }) { lineIdx, line ->
            val lineStart = lineStartOffsets[lineIdx]
            val lineEnd = lineStart + line.length
            val lineMatches =
              remember(lineIdx, textMatches, currentMatchIndex) {
                textMatches.withIndex().filter { (_, r) -> r.last >= lineStart && r.first < lineEnd }
              }
            val annotated =
              remember(line, lineMatches, currentMatchIndex) {
                if (lineMatches.isEmpty()) null
                else
                  buildAnnotatedString {
                    append(line)
                    lineMatches.forEach { (idx, range) ->
                      val s = (range.first - lineStart).coerceIn(0, line.length)
                      val e = (range.last + 1 - lineStart).coerceIn(s, line.length)
                      if (e > s) {
                        addStyle(
                          style =
                            SpanStyle(
                              background =
                                if (idx == currentMatchIndex) Color(0x99F97316) else Color(0x66FACC15),
                            ),
                          start = s,
                          end = e,
                        )
                      }
                    }
                  }
              }

            Row(
              modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Text(
                text = "${lineIdx + 1}",
                style = MaterialTheme.typography.bodySmall.copy(
                  fontFamily = FontFamily.Monospace,
                  fontSize = (fontSizeSp - 2f).coerceAtLeast(10f).sp,
                ),
                color = subtleColor.copy(alpha = 0.65f),
                modifier = Modifier.width(38.dp),
              )
              if (annotated != null) {
                Text(
                  text = annotated,
                  style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = fontSizeSp.sp,
                    lineHeight = (fontSizeSp * 1.45f).sp,
                  ),
                  color = textColor,
                  softWrap = wrapLines,
                )
              } else {
                Text(
                  text = line.ifEmpty { " " },
                  style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = fontSizeSp.sp,
                    lineHeight = (fontSizeSp * 1.45f).sp,
                  ),
                  color = textColor,
                  softWrap = wrapLines,
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun CsvTableViewer(
  csvText: String,
  textColor: Color,
  subtleColor: Color,
  fontSizeSp: Float,
  bottomPadding: Dp,
  onTap: () -> Unit,
) {
  val rows = remember(csvText) { NonPdfDocumentLoader.parseCsvPreviewLines(csvText) }
  val hScroll = rememberScrollState()
  LazyColumn(
    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = bottomPadding + 16.dp),
    modifier =
      Modifier.fillMaxSize()
        .horizontalScroll(hScroll)
        .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) },
  ) {
    itemsIndexed(rows) { rowIdx, cells ->
      val isHeader = rowIdx == 0
      Row(
        modifier =
          Modifier.background(
            if (isHeader) subtleColor.copy(alpha = 0.14f)
            else if (rowIdx % 2 == 1) subtleColor.copy(alpha = 0.05f)
            else Color.Transparent
          )
            .border(0.5.dp, subtleColor.copy(alpha = 0.22f))
            .padding(vertical = 6.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        cells.forEach { cell ->
          Text(
            text = cell,
            style = MaterialTheme.typography.bodySmall.copy(
              fontFamily = FontFamily.Monospace,
              fontSize = fontSizeSp.sp,
              fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
            ),
            color = textColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(min = 84.dp, max = 220.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun TextControlPill(
  label: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  active: Boolean,
  onClick: () -> Unit,
  testTag: String,
) {
  val colorScheme = MaterialTheme.colorScheme
  Row(
    modifier =
      Modifier.defaultMinSize(minHeight = PrismDimens.TouchTargetMin)
        .clip(CircleShape)
        .background(if (active) colorScheme.primary.copy(alpha = 0.16f) else colorScheme.surfaceVariant.copy(alpha = 0.55f))
        .border(1.dp, if (active) colorScheme.primary else colorScheme.outlineVariant, CircleShape)
        .glassClickable(role = Role.Button, onClick = onClick)
        .testTag(testTag)
        .padding(horizontal = 12.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = if (active) colorScheme.primary else colorScheme.onSurfaceVariant,
      modifier = Modifier.size(16.dp),
    )
    Text(
      text = label,
      style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
      color = if (active) colorScheme.primary else colorScheme.onSurface,
    )
  }
}

@Composable
internal fun UnsupportedDocumentCard(
  document: IncomingDocumentRequest?,
  title: String,
  body: String,
  isError: Boolean = false,
  onOpenExternal: (() -> Unit)?,
  onOpenAnnotate: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme
  Box(
    modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
    contentAlignment = Alignment.Center,
  ) {
    GlassSurface(
      cornerRadius = 28.dp,
      elevation = GlassElevation.SUBTLE,
      useBackdropBlur = false,
      modifier = Modifier.widthIn(max = 540.dp).fillMaxWidth().testTag("viewer_unsupported_card"),
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
                .background((if (isError) colorScheme.error else colorScheme.primary).copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = if (isError) Icons.Rounded.ErrorOutline else Icons.Rounded.Description,
              contentDescription = null,
              tint = if (isError) colorScheme.error else colorScheme.primary,
              modifier = Modifier.size(26.dp),
            )
          }
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = title,
              style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
              color = colorScheme.onSurface,
            )
            if (document != null) {
              Text(
                text = document.displayName,
                style = MaterialTheme.typography.labelMedium,
                color = colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }

        Text(
          text = body,
          style = MaterialTheme.typography.bodyMedium,
          color = colorScheme.onSurfaceVariant,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          if (onOpenExternal != null) {
            Button(
              onClick = onOpenExternal,
              modifier =
                Modifier.defaultMinSize(minHeight = PrismDimens.TouchTargetMin)
                  .testTag("viewer_open_external_button"),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(Modifier.size(8.dp))
              Text(stringResource(R.string.viewer_action_open_external))
            }
          }
          if (onOpenAnnotate != null) {
            OutlinedButton(
              onClick = onOpenAnnotate,
              modifier =
                Modifier.defaultMinSize(minHeight = PrismDimens.TouchTargetMin)
                  .testTag("viewer_editor_route_button"),
            ) {
              Icon(
                imageVector = Icons.Rounded.Code,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(Modifier.size(8.dp))
              Text(stringResource(R.string.viewer_action_annotate))
            }
          }
        }
      }
    }
  }
}

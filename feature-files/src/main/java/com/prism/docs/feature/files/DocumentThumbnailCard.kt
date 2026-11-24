package com.prism.docs.feature.files

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.prism.docs.core.data.db.IndexedDocumentEntity
import com.prism.docs.core.data.model.DocumentCategory
import com.prism.docs.core.data.thumbnail.DocumentThumbnailPipeline
import com.prism.docs.core.data.thumbnail.ThumbnailRequest
import com.prism.docs.core.ui.theme.LocalGlassEnvironment
import com.prism.docs.core.ui.theme.LocalPrismVisualTokens
import com.prism.docs.core.ui.theme.PrismColors
import com.prism.docs.core.ui.theme.PrismDimens
import com.prism.docs.core.ui.theme.PrismVisualTokens
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class CategoryVisualSpec(
  val icon: ImageVector,
  val accent: Color,
  val shortTag: String,
)

fun categoryVisualSpec(category: DocumentCategory): CategoryVisualSpec =
  when (category) {
    DocumentCategory.PDF -> CategoryVisualSpec(Icons.Rounded.PictureAsPdf, PrismColors.CoralPdf, "PDF")
    DocumentCategory.DOCUMENTS -> CategoryVisualSpec(Icons.Rounded.Description, PrismColors.Sapphire, "DOCX")
    DocumentCategory.SPREADSHEETS -> CategoryVisualSpec(Icons.Rounded.TableChart, PrismColors.EmeraldSheet, "XLSX")
    DocumentCategory.SLIDES -> CategoryVisualSpec(Icons.Rounded.Slideshow, PrismColors.OrangeSlide, "PPTX")
    DocumentCategory.IMAGES -> CategoryVisualSpec(Icons.Rounded.Image, PrismColors.OrchidImage, "IMG")
    DocumentCategory.VIDEOS -> CategoryVisualSpec(Icons.Rounded.VideoFile, PrismColors.Amethyst, "VID")
    DocumentCategory.AUDIO -> CategoryVisualSpec(Icons.Rounded.AudioFile, PrismColors.TealCode, "AUD")
    DocumentCategory.ARCHIVES -> CategoryVisualSpec(Icons.Rounded.FolderZip, PrismColors.IndigoArchive, "ZIP")
    DocumentCategory.EBOOKS -> CategoryVisualSpec(Icons.AutoMirrored.Rounded.MenuBook, PrismColors.OrchidImage, "BOOK")
    DocumentCategory.CODE_TEXT -> CategoryVisualSpec(Icons.Rounded.Code, PrismColors.TealCode, "TXT")
    DocumentCategory.OTHER -> CategoryVisualSpec(Icons.AutoMirrored.Rounded.Article, PrismColors.GlacierBlue, "FILE")
  }

/**
 * Resolves the uppercase badge label and semantic colour:
 * PDF red, DOCX blue, XLSX green, PPTX orange, SCAN purple, plus distinct hues for other types.
 */
fun resolveFileTypeBadge(displayName: String, category: DocumentCategory): Pair<String, Color> {
  val lowerName = displayName.lowercase(Locale.US)
  if (lowerName.startsWith("scan ") || lowerName.startsWith("scan_") || lowerName.startsWith("scan-")) {
    return "SCAN" to PrismColors.ScanPurple
  }
  val visual = categoryVisualSpec(category)
  val ext =
    displayName
      .substringAfterLast('.', "")
      .takeIf { it.isNotBlank() && it.length <= 5 }
      ?.uppercase(Locale.US)
  val label = ext ?: visual.shortTag
  val color =
    when (label) {
      "PDF" -> PrismColors.CoralPdf
      "DOC", "DOCX", "RTF", "ODT" -> PrismColors.Sapphire
      "XLS", "XLSX", "CSV", "ODS" -> PrismColors.EmeraldSheet
      "PPT", "PPTX", "ODP", "KEY" -> PrismColors.OrangeSlide
      "SCAN" -> PrismColors.ScanPurple
      else -> visual.accent
    }
  return label to color
}

/**
 * Compact pill badge used in the top-right corner of Recent & Grid thumbnail cards.
 */
@Composable
fun FileTypeBadgePill(
  label: String,
  accent: Color,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier =
      modifier
        .clip(CircleShape)
        .background(accent)
        .padding(
          horizontal = PrismDimens.BadgePillHorizontalPadding,
          vertical = PrismDimens.BadgePillVerticalPadding,
        )
        .clearAndSetSemantics {},
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = label,
      fontFamily = FontFamily.SansSerif,
      fontSize = PrismDimens.FontSizeBadgePill,
      fontWeight = FontWeight.ExtraBold,
      letterSpacing = PrismDimens.LetterSpacingWideBadge,
      color = PrismColors.OnAccent,
      maxLines = 1,
    )
  }
}

/**
 * 46dp rounded-square solid colour file-type badge for the Library file list card
 * (PDF red, DOCX blue, XLSX green, PPTX orange, SCAN purple).
 */
@Composable
fun FileTypeSquareBadge(
  label: String,
  accent: Color,
  isSelected: Boolean = false,
  modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme
  val badgeShape = RoundedCornerShape(PrismDimens.BadgeSquareCornerRadius)
  val fontSize =
    if (label.length >= 4) PrismDimens.FontSizeBadgeSquareCompact
    else PrismDimens.FontSizeBadgeSquareRegular

  Box(modifier = modifier.size(PrismDimens.BadgeSquareSize).clearAndSetSemantics {}) {
    Box(
      modifier =
        Modifier.fillMaxSize()
          .shadow(
            elevation = PrismDimens.ShadowSmall,
            shape = badgeShape,
            ambientColor = accent.copy(alpha = 0.36f),
            spotColor = accent.copy(alpha = 0.44f),
          )
          .clip(badgeShape)
          .background(accent),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = label,
        fontFamily = FontFamily.SansSerif,
        fontSize = fontSize,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = PrismDimens.LetterSpacingTightBadge,
        color = PrismColors.OnAccent,
        maxLines = 1,
      )
    }

    if (isSelected) {
      Icon(
        imageVector = Icons.Rounded.CheckCircle,
        contentDescription = null,
        tint = colorScheme.primary,
        modifier =
          Modifier.align(Alignment.TopEnd)
            .size(PrismDimens.SelectedOverlayBadgeSize)
            .clip(CircleShape)
            .background(colorScheme.surface),
      )
    }
  }
}

fun IndexedDocumentEntity.resolvedCategory(): DocumentCategory =
  runCatching { DocumentCategory.valueOf(category) }.getOrDefault(DocumentCategory.OTHER)

fun IndexedDocumentEntity.toThumbnailRequest(targetSizePx: Int = 320): ThumbnailRequest =
  ThumbnailRequest(
    uriString = uriString,
    displayName = displayName,
    mimeType = mimeType,
    category = resolvedCategory(),
    lastModifiedEpochMs = lastModifiedEpochMs,
    sizeBytes = sizeBytes,
    targetSizePx = targetSizePx,
  )

/**
 * Renders a real cached document thumbnail from [DocumentThumbnailPipeline] (PDF first page,
 * Image, Video frame, or embedded OOXML `docProps/thumbnail.jpeg`) with a small coloured type
 * badge in the top-right corner, and falls back to a crisp paper/sheet miniature card when no
 * embedded thumbnail is present.
 */
@Composable
fun DocumentThumbnailView(
  request: ThumbnailRequest,
  pipeline: DocumentThumbnailPipeline,
  cornerRadius: Dp = PrismDimens.RecentThumbnailCornerRadius,
  compact: Boolean = false,
  showCornerBadge: Boolean = !compact,
  modifier: Modifier = Modifier,
) {
  val glassEnv = LocalGlassEnvironment.current
  val tokens = LocalPrismVisualTokens.current
  val visual = remember(request.category) { categoryVisualSpec(request.category) }
  val (badgeLabel, badgeColor) =
    remember(request.displayName, request.category) {
      resolveFileTypeBadge(request.displayName, request.category)
    }

  var bitmap by
    remember(request.cacheKey) { mutableStateOf<Bitmap?>(pipeline.peekMemoryCache(request)) }

  LaunchedEffect(request.cacheKey) {
    if (bitmap == null && !pipeline.hasKnownMissingThumbnail(request)) {
      bitmap = pipeline.loadThumbnail(request)
    }
  }

  val shape = RoundedCornerShape(cornerRadius)
  val imageAlpha =
    animateFloatAsState(
      targetValue = if (bitmap != null) 1f else 0f,
      animationSpec = tween(durationMillis = if (glassEnv.reduceMotion) 0 else 160),
      label = "thumbnail_fade",
    )

  Box(
    modifier =
      modifier
        .clip(shape)
        .background(tokens.thumbnailSheetBg)
        .border(width = PrismDimens.HairlineWidth, color = tokens.thumbnailSheetBorder, shape = shape),
    contentAlignment = Alignment.Center,
  ) {
    if (compact) {
      Icon(
        imageVector = visual.icon,
        contentDescription = null,
        tint = badgeColor,
        modifier = Modifier.size(PrismDimens.ThumbnailCompactIconSize),
      )
    } else {
      MiniatureSheetFallback(
        category = request.category,
        accent = badgeColor,
        tokens = tokens,
      )
    }

    val loaded = bitmap
    if (loaded != null && !loaded.isRecycled) {
      val imageBitmap = remember(loaded) { loaded.asImageBitmap() }
      Image(
        bitmap = imageBitmap,
        contentDescription = stringResource(R.string.doc_thumbnail_cd, request.displayName),
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize().graphicsLayer { alpha = imageAlpha.value },
      )
    }

    if (showCornerBadge) {
      FileTypeBadgePill(
        label = badgeLabel,
        accent = badgeColor,
        modifier = Modifier.align(Alignment.TopEnd).padding(PrismDimens.BadgeCornerInset),
      )
    }
  }
}

@Composable
private fun MiniatureSheetFallback(
  category: DocumentCategory,
  accent: Color,
  tokens: PrismVisualTokens,
) {
  val lineColor = tokens.fallbackLine
  val headerColor = tokens.fallbackHeader
  val isDark = tokens.isDark

  when (category) {
    DocumentCategory.SPREADSHEETS -> {
      val gridColor = tokens.sheetGridLine
      val headerBg = tokens.sheetHeaderBg
      Column(modifier = Modifier.fillMaxSize()) {
        Box(
          modifier =
            Modifier.fillMaxWidth()
              .height(PrismDimens.ThumbnailSheetHeaderHeight)
              .background(headerBg)
        )
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
          Canvas(modifier = Modifier.fillMaxSize()) {
            val cols = 4
            val rows = 5
            val colW = size.width / cols
            val rowH = size.height / rows
            for (c in 1 until cols) {
              drawLine(
                color = gridColor,
                start = Offset(c * colW, 0f),
                end = Offset(c * colW, size.height),
                strokeWidth = 1.dp.toPx(),
              )
            }
            for (r in 1 until rows) {
              drawLine(
                color = gridColor,
                start = Offset(0f, r * rowH),
                end = Offset(size.width, r * rowH),
                strokeWidth = 1.dp.toPx(),
              )
            }
            drawRect(
              color = tokens.successAccent,
              topLeft = Offset(colW, rowH),
              size = Size(colW, rowH),
              style = Stroke(width = 2.dp.toPx()),
            )
          }
        }
      }
    }
    DocumentCategory.SLIDES -> {
      Column(
        modifier =
          Modifier.fillMaxSize()
            .background(tokens.slideBg)
            .padding(PrismDimens.SpaceStandard),
        verticalArrangement = Arrangement.spacedBy(PrismDimens.SpaceMedium),
      ) {
        Box(
          modifier =
            Modifier.fillMaxWidth(0.72f)
              .height(PrismDimens.SpaceMedium)
              .clip(CircleShape)
              .background(PrismColors.OrangeSlide.copy(alpha = 0.85f))
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(PrismDimens.SpaceSmallMedium),
        ) {
          Box(
            modifier =
              Modifier.size(PrismDimens.SpaceSmallMedium)
                .clip(CircleShape)
                .background(PrismColors.OrangeSlide)
          )
          Box(
            modifier =
              Modifier.fillMaxWidth(0.75f)
                .height(PrismDimens.SpaceSmall)
                .clip(CircleShape)
                .background(PrismColors.OrangeSlide.copy(alpha = 0.55f))
          )
        }
        Box(
          modifier =
            Modifier.fillMaxWidth()
              .weight(1f)
              .clip(RoundedCornerShape(PrismDimens.SpaceMediumLarge))
              .background(tokens.slideInnerCard)
        )
      }
    }
    else -> {
      Column(
        modifier = Modifier.fillMaxSize().padding(PrismDimens.SpaceStandard),
        verticalArrangement = Arrangement.spacedBy(PrismDimens.SpaceSmallMedium),
      ) {
        Box(
          modifier =
            Modifier.fillMaxWidth(0.54f)
              .height(PrismDimens.SpaceMedium)
              .clip(CircleShape)
              .background(headerColor)
        )
        Spacer(Modifier.height(PrismDimens.SpaceXSmall))
        Box(
          modifier =
            Modifier.fillMaxWidth(0.92f)
              .height(PrismDimens.SpaceSmall)
              .clip(CircleShape)
              .background(lineColor)
        )
        Box(
          modifier =
            Modifier.fillMaxWidth(0.76f)
              .height(PrismDimens.SpaceSmall)
              .clip(CircleShape)
              .background(lineColor)
        )
        Box(
          modifier =
            Modifier.fillMaxWidth(0.84f)
              .height(PrismDimens.SpaceSmall)
              .clip(CircleShape)
              .background(lineColor)
        )
        Spacer(Modifier.height(PrismDimens.SpaceXSmall))
        Box(
          modifier =
            Modifier.fillMaxWidth()
              .weight(1f)
              .clip(RoundedCornerShape(PrismDimens.SpaceMediumLarge))
              .background(accent.copy(alpha = if (isDark) 0.24f else 0.18f)),
          contentAlignment = Alignment.Center,
        ) {
          val visual = categoryVisualSpec(category)
          Icon(
            imageVector = visual.icon,
            contentDescription = null,
            tint = accent.copy(alpha = 0.78f),
            modifier = Modifier.size(PrismDimens.ThumbnailFallbackIconSize),
          )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(PrismDimens.SpaceSmallMedium)) {
          Box(
            modifier =
              Modifier.fillMaxWidth(0.68f)
                .height(PrismDimens.SpaceSmall)
                .clip(CircleShape)
                .background(lineColor)
          )
        }
      }
    }
  }
}

fun formatFileCount(count: Int): String = NumberFormat.getIntegerInstance(Locale.getDefault()).format(count)

fun formatByteSize(bytes: Long): String {
  if (bytes < 0L) return "—"
  if (bytes < 1024L) return "$bytes B"
  val kb = bytes / 1024.0
  if (kb < 1024.0) return String.format(Locale.US, "%.0f KB", kb)
  val mb = kb / 1024.0
  if (mb < 1024.0) return String.format(Locale.US, "%.1f MB", mb)
  val gb = mb / 1024.0
  return String.format(Locale.US, "%.2f GB", gb)
}

@Composable
fun rememberFormattedByteSize(bytes: Long): String {
  if (bytes < 0L) return stringResource(R.string.format_unknown_dash)
  if (bytes < 1024L) return stringResource(R.string.format_bytes_b, bytes)
  val kb = bytes / 1024.0
  if (kb < 1024.0) return stringResource(R.string.format_bytes_kb, kb)
  val mb = kb / 1024.0
  if (mb < 1024.0) return stringResource(R.string.format_bytes_mb, mb)
  val gb = mb / 1024.0
  return stringResource(R.string.format_bytes_gb, gb)
}

@Composable
fun rememberFormattedRelativeDay(epochMs: Long): String {
  if (epochMs <= 0L) return stringResource(R.string.format_unknown_date)
  val dayDiff = remember(epochMs) { computeDayDifference(epochMs) }
  return when {
    dayDiff <= 0 -> stringResource(R.string.format_day_today)
    dayDiff == 1 -> stringResource(R.string.format_day_yesterday)
    dayDiff in 2..6 ->
      remember(epochMs) { SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(epochMs)) }
    else ->
      remember(epochMs) { SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(epochMs)) }
  }
}

@Composable
fun rememberFormattedModifiedTimestamp(epochMs: Long): String {
  if (epochMs <= 0L) return stringResource(R.string.format_unknown_date_title)
  val now = System.currentTimeMillis()
  val diff = (now - epochMs).coerceAtLeast(0L)
  val oneDay = 24L * 60L * 60L * 1000L
  return when {
    diff < 60_000L -> stringResource(R.string.format_just_now)
    diff < 3_600_000L -> {
      val mins = (diff / 60_000L).toInt().coerceAtLeast(1)
      pluralStringResource(R.plurals.format_minutes_ago, mins, mins)
    }
    diff < oneDay -> {
      val hours = (diff / 3_600_000L).toInt().coerceAtLeast(1)
      pluralStringResource(R.plurals.format_hours_ago, hours, hours)
    }
    diff < 7L * oneDay -> {
      val days = (diff / oneDay).toInt().coerceAtLeast(1)
      pluralStringResource(R.plurals.format_days_ago, days, days)
    }
    else ->
      remember(epochMs) { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(epochMs)) }
  }
}

private fun computeDayDifference(epochMs: Long): Int {
  val nowCal = Calendar.getInstance()
  val targetCal = Calendar.getInstance().apply { timeInMillis = epochMs }
  return if (nowCal.get(Calendar.YEAR) == targetCal.get(Calendar.YEAR)) {
    nowCal.get(Calendar.DAY_OF_YEAR) - targetCal.get(Calendar.DAY_OF_YEAR)
  } else {
    ((nowCal.timeInMillis - epochMs) / (24L * 60L * 60L * 1000L)).toInt()
  }
}

fun formatRelativeDay(epochMs: Long): String {
  if (epochMs <= 0L) return "unknown date"
  val dayDiff = computeDayDifference(epochMs)
  return when {
    dayDiff <= 0 -> "today"
    dayDiff == 1 -> "yesterday"
    dayDiff in 2..6 -> SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(epochMs))
    else -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(epochMs))
  }
}

fun formatModifiedTimestamp(epochMs: Long): String {
  if (epochMs <= 0L) return "Unknown date"
  val now = System.currentTimeMillis()
  val diff = (now - epochMs).coerceAtLeast(0L)
  val oneDay = 24L * 60L * 60L * 1000L
  return when {
    diff < 60_000L -> "Just now"
    diff < 3_600_000L -> "${diff / 60_000L}m ago"
    diff < oneDay -> "${diff / 3_600_000L}h ago"
    diff < 7L * oneDay -> "${diff / oneDay}d ago"
    else -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(epochMs))
  }
}

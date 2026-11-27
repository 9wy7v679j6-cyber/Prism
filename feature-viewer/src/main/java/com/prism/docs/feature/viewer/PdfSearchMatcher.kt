package com.prism.docs.feature.viewer

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Pure Kotlin PDF search & character-level geometry helper:
 * - Case-insensitive and diacritic-insensitive matching via Unicode NFD decomposition.
 * - Maps normalized match ranges cleanly back to original PDF character bounding boxes.
 * - Groups contiguous character boxes on the same visual line into smooth highlight rectangles.
 */
object PdfSearchMatcher {

  data class NormalizedTextIndex(
    val normalized: String,
    val normalizedToRawIndex: IntArray,
  )

  fun buildNormalizedIndex(rawText: String): NormalizedTextIndex {
    if (rawText.isEmpty()) {
      return NormalizedTextIndex(normalized = "", normalizedToRawIndex = IntArray(0))
    }
    val sb = StringBuilder(rawText.length)
    val indexMap = IntArray(rawText.length * 2 + 8)
    var mapSize = 0

    for (rawIdx in rawText.indices) {
      val ch = rawText[rawIdx]
      val decomposed =
        if (ch.code < 128) {
          ch.lowercaseChar().toString()
        } else {
          Normalizer.normalize(ch.toString(), Normalizer.Form.NFD)
        }
      for (j in decomposed.indices) {
        val dc = decomposed[j]
        if (Character.getType(dc) == Character.NON_SPACING_MARK.toInt()) continue
        val folded =
          when (dc) {
            '\u00A0', '\u2007', '\u202F' -> ' '
            else -> dc.lowercaseChar()
          }
        sb.append(folded)
        if (mapSize < indexMap.size) {
          indexMap[mapSize++] = rawIdx
        }
      }
    }
    return NormalizedTextIndex(
      normalized = sb.toString(),
      normalizedToRawIndex = indexMap.copyOf(mapSize),
    )
  }

  fun normalizeQuery(query: String): String =
    buildNormalizedIndex(query).normalized.trim()

  fun findMatchesInPage(
    pageData: PdfPageTextData,
    rawQuery: String,
  ): List<PdfSearchMatch> {
    val normQuery = normalizeQuery(rawQuery)
    if (normQuery.isEmpty() || pageData.rawText.isEmpty()) return emptyList()

    val indexed = buildNormalizedIndex(pageData.rawText)
    if (indexed.normalized.isEmpty()) return emptyList()

    val matches = ArrayList<PdfSearchMatch>()
    var searchFrom = 0
    while (searchFrom <= indexed.normalized.length - normQuery.length) {
      val hitIdx = indexed.normalized.indexOf(normQuery, startIndex = searchFrom)
      if (hitIdx < 0) break
      val hitEndNorm = hitIdx + normQuery.length - 1
      val rawStart = indexed.normalizedToRawIndex.getOrElse(hitIdx) { 0 }
      val rawEndInclusive =
        indexed.normalizedToRawIndex.getOrElse(hitEndNorm) {
          (pageData.rawText.length - 1).coerceAtLeast(rawStart)
        }
      val rawEndExclusive = (rawEndInclusive + 1).coerceAtMost(pageData.rawText.length)
      val matchedSlice = pageData.rawText.substring(rawStart, rawEndExclusive)
      val rects = mergeCharacterRects(pageData.charBoxes, rawStart, rawEndExclusive)
      matches.add(
        PdfSearchMatch(
          pageIndex = pageData.pageIndex,
          startCharIndex = rawStart,
          endCharIndex = rawEndExclusive,
          matchText = matchedSlice,
          rects = rects,
        )
      )
      searchFrom = hitIdx + normQuery.length.coerceAtLeast(1)
    }
    return matches
  }

  fun findPlainTextMatches(
    text: String,
    rawQuery: String,
  ): List<IntRange> {
    val normQuery = normalizeQuery(rawQuery)
    if (normQuery.isEmpty() || text.isEmpty()) return emptyList()
    val indexed = buildNormalizedIndex(text)
    if (indexed.normalized.isEmpty()) return emptyList()

    val ranges = ArrayList<IntRange>()
    var searchFrom = 0
    while (searchFrom <= indexed.normalized.length - normQuery.length) {
      val hitIdx = indexed.normalized.indexOf(normQuery, startIndex = searchFrom)
      if (hitIdx < 0) break
      val rawStart = indexed.normalizedToRawIndex.getOrElse(hitIdx) { 0 }
      val rawEnd =
        indexed.normalizedToRawIndex.getOrElse(hitIdx + normQuery.length - 1) { rawStart }
      ranges.add(rawStart..rawEnd.coerceAtLeast(rawStart))
      searchFrom = hitIdx + normQuery.length.coerceAtLeast(1)
    }
    return ranges
  }

  /**
   * Merges contiguous character bounding boxes on the same visual line into smooth rectangles
   * so multi-character search highlights and text selections render cleanly.
   */
  fun mergeCharacterRects(
    charBoxes: List<PdfCharBox>,
    startInclusive: Int,
    endExclusive: Int,
  ): List<NormalizedRect> {
    if (charBoxes.isEmpty()) return emptyList()
    val safeStart = startInclusive.coerceIn(0, charBoxes.size)
    val safeEnd = endExclusive.coerceIn(safeStart, charBoxes.size)
    if (safeStart >= safeEnd) return emptyList()

    val merged = ArrayList<NormalizedRect>(4)
    var curLeft = Float.NaN
    var curTop = Float.NaN
    var curRight = Float.NaN
    var curBottom = Float.NaN

    for (i in safeStart until safeEnd) {
      val box = charBoxes[i]
      if (box.right <= box.left || box.bottom <= box.top) continue
      if (curLeft.isNaN()) {
        curLeft = box.left
        curTop = box.top
        curRight = box.right
        curBottom = box.bottom
      } else {
        val curMidY = (curTop + curBottom) * 0.5f
        val boxMidY = (box.top + box.bottom) * 0.5f
        val lineHeight = max(curBottom - curTop, box.bottom - box.top).coerceAtLeast(0.008f)
        val sameLine = abs(curMidY - boxMidY) <= lineHeight * 0.65f
        val smallHorizontalGap = (box.left - curRight) <= lineHeight * 2.2f && box.right >= curLeft - lineHeight * 0.5f
        if (sameLine && smallHorizontalGap) {
          curLeft = min(curLeft, box.left)
          curTop = min(curTop, box.top)
          curRight = max(curRight, box.right)
          curBottom = max(curBottom, box.bottom)
        } else {
          merged.add(NormalizedRect(curLeft, curTop, curRight, curBottom))
          curLeft = box.left
          curTop = box.top
          curRight = box.right
          curBottom = box.bottom
        }
      }
    }
    if (!curLeft.isNaN()) {
      merged.add(NormalizedRect(curLeft, curTop, curRight, curBottom))
    }
    return merged
  }

  /**
   * Finds the closest character index on a page to a normalized touch coordinate (0..1, 0..1).
   */
  fun findNearestCharIndex(
    pageData: PdfPageTextData,
    xNorm: Float,
    yNorm: Float,
    maxDistanceNorm: Float = 0.08f,
  ): Int? {
    val boxes = pageData.charBoxes
    if (boxes.isEmpty() || pageData.rawText.isEmpty()) return null
    var bestIdx = -1
    var bestDistSq = maxDistanceNorm * maxDistanceNorm
    val limit = min(boxes.size, pageData.rawText.length)
    for (i in 0 until limit) {
      val box = boxes[i]
      if (box.right <= box.left || box.bottom <= box.top) continue
      val clampedX = xNorm.coerceIn(box.left, box.right)
      val clampedY = yNorm.coerceIn(box.top, box.bottom)
      val dx = xNorm - clampedX
      val dy = yNorm - clampedY
      val distSq = dx * dx + dy * dy * 1.4f
      if (distSq <= bestDistSq) {
        bestDistSq = distSq
        bestIdx = i
      }
    }
    return if (bestIdx >= 0) bestIdx else null
  }

  /**
   * Expands a single character index to the enclosing word boundaries for ergonomic long-press selection.
   */
  fun expandToWordRange(rawText: String, charIndex: Int): IntRange {
    if (rawText.isEmpty()) return 0 until 0
    val idx = charIndex.coerceIn(0, rawText.lastIndex)
    var start = idx
    var end = idx
    fun isWordChar(c: Char): Boolean = c.isLetterOrDigit() || c == '_' || c == '-' || c == '\''
    if (isWordChar(rawText[idx])) {
      while (start > 0 && isWordChar(rawText[start - 1])) start--
      while (end < rawText.lastIndex && isWordChar(rawText[end + 1])) end++
    }
    return start..end
  }

  fun buildSelectionRange(
    pageData: PdfPageTextData,
    anchorA: Int,
    anchorB: Int,
  ): TextSelectionRange? {
    if (pageData.rawText.isEmpty() || pageData.charBoxes.isEmpty()) return null
    val rawStart = min(anchorA, anchorB).coerceIn(0, pageData.rawText.lastIndex)
    val rawEndInclusive = max(anchorA, anchorB).coerceIn(rawStart, pageData.rawText.lastIndex)
    val endExclusive = (rawEndInclusive + 1).coerceAtMost(pageData.rawText.length)
    val text = pageData.rawText.substring(rawStart, endExclusive).trim()
    if (text.isEmpty()) return null
    val rects = mergeCharacterRects(pageData.charBoxes, rawStart, endExclusive)
    if (rects.isEmpty()) return null
    val startAnchor = pageData.charBoxes.getOrNull(rawStart)?.toNormalizedRect() ?: rects.firstOrNull()
    val endAnchor = pageData.charBoxes.getOrNull(rawEndInclusive)?.toNormalizedRect() ?: rects.lastOrNull()
    return TextSelectionRange(
      pageIndex = pageData.pageIndex,
      startIndex = rawStart,
      endIndex = rawEndInclusive,
      selectedText = text,
      rects = rects,
      startAnchor = startAnchor,
      endAnchor = endAnchor,
    )
  }
}

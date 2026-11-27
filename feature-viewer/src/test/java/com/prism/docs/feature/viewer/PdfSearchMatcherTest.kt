package com.prism.docs.feature.viewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfSearchMatcherTest {

  private fun samplePageData(pageIndex: Int, text: String): PdfPageTextData {
    var col = 0
    var row = 0
    val boxes =
      text.map { ch ->
        if (ch == '\n') {
          row++
          col = 0
          PdfCharBox(0.9f, 0.05f + row * 0.04f, 0.9f, 0.07f + row * 0.04f)
        } else {
          val left = 0.1f + (col * 0.02f).coerceAtMost(0.78f)
          val top = 0.05f + row * 0.04f
          col++
          PdfCharBox(left = left, top = top, right = left + 0.018f, bottom = top + 0.022f)
        }
      }
    return PdfPageTextData(
      pageIndex = pageIndex,
      rawText = text,
      charBoxes = boxes,
      hasTextLayer = text.any { !it.isWhitespace() },
    )
  }

  @Test
  fun searchMatchesAreCaseInsensitiveAndDiacriticInsensitive() {
    val page = samplePageData(
      pageIndex = 3,
      text = "Café Résumé\nPrepare a naive resume at the cafe tomorrow.",
    )

    val cafeHits = PdfSearchMatcher.findMatchesInPage(page, "CAFE")
    assertEquals(2, cafeHits.size)
    assertEquals("Café", cafeHits[0].matchText)
    assertEquals("cafe", cafeHits[1].matchText)
    assertTrue(cafeHits.all { it.pageIndex == 3 && it.rects.isNotEmpty() })

    val resumeHits = PdfSearchMatcher.findMatchesInPage(page, "résumé")
    assertEquals(2, resumeHits.size)
    assertEquals("Résumé", resumeHits[0].matchText)
    assertEquals("resume", resumeHits[1].matchText)
  }

  @Test
  fun multilineCharBoxesSplitCleanlyPerLineAndSameLineMerges() {
    val page = samplePageData(
      pageIndex = 0,
      text = "Alpha Beta\nGamma Delta",
    )
    val mergedAll = PdfSearchMatcher.mergeCharacterRects(page.charBoxes, 0, page.rawText.length)
    assertEquals(2, mergedAll.size)
    assertTrue(mergedAll[0].bottom < mergedAll[1].top)
  }

  @Test
  fun nearestCharLookupAndWordExpansionSelectsTargetWord() {
    val page = samplePageData(
      pageIndex = 1,
      text = "Quarterly Financial Report",
    )
    val fBox = page.charBoxes[12] // inside "Financial"
    val hit = PdfSearchMatcher.findNearestCharIndex(page, (fBox.left + fBox.right) * 0.5f, (fBox.top + fBox.bottom) * 0.5f)
    assertNotNull(hit)

    val wordRange = PdfSearchMatcher.expandToWordRange(page.rawText, hit!!)
    val selection = PdfSearchMatcher.buildSelectionRange(page, wordRange.first, wordRange.last)
    assertNotNull(selection)
    assertEquals("Financial", selection?.selectedText)
    assertNotNull(selection?.startAnchor)
    assertNotNull(selection?.endAnchor)
  }

  @Test
  fun plainTextMatchesPreserveOriginalIndicesAcrossDiacritics() {
    val text = "Crème Brûlée and creme brulee"
    val hits = PdfSearchMatcher.findPlainTextMatches(text, "creme")
    assertEquals(2, hits.size)
    assertEquals("Crème", text.substring(hits[0].first, hits[0].last + 1))
    assertEquals("creme", text.substring(hits[1].first, hits[1].last + 1))
  }
}

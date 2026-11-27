package com.prism.docs.feature.viewer

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitWidthDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import java.io.File
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LargePdfPerformanceAndExtractorTest {

  private fun generateMultiPagePdf(context: Context, pageCount: Int): File {
    PDFBoxResourceLoader.init(context)
    val outFile = File(context.cacheDir, "benchmark_${pageCount}_pages.pdf")
    if (outFile.exists()) outFile.delete()

    PDDocument().use { doc ->
      val outline = PDDocumentOutline()
      doc.documentCatalog.documentOutline = outline

      for (i in 0 until pageCount) {
        val page = PDPage(PDRectangle.LETTER)
        doc.addPage(page)

        PDPageContentStream(doc, page).use { stream ->
          stream.beginText()
          stream.setFont(PDType1Font.HELVETICA, 12f)
          stream.newLineAtOffset(56f, 720f)
          stream.showText("Prism Benchmark Document - Section ${(i / 20) + 1} Page ${i + 1}")
          stream.newLineAtOffset(0f, -24f)
          if (i == 42 || i == 137 || i == 205) {
            stream.showText("Quarterly Financial Audit Summary with confidential clause.")
          } else {
            stream.showText("Standard archival body paragraph for latency measurement.")
          }
          stream.endText()
        }

        if (i % 25 == 0) {
          val dest = PDPageFitWidthDestination().apply { this.page = page }
          val item = PDOutlineItem().apply {
            title = "Chapter ${(i / 25) + 1}"
            destination = dest
          }
          outline.addLast(item)
        }
      }
      doc.save(outFile)
    }
    return outFile
  }

  @Test
  fun large220PagePdfOpensExtractsOutlineCachesTextAndSearchesQuickly() {
    runBlocking {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val pdfFile = generateMultiPagePdf(context, pageCount = 220)
      val uriString = Uri.fromFile(pdfFile).toString()

      val extractor = PdfTextAndOutlineExtractor(context)
      val openMs = measureTimeMillis {
        val res = extractor.open(uriString, pdfFile.length())
        assertTrue("Expected extractor open to succeed: ${res.exceptionOrNull()}", res.isSuccess)
        assertEquals(220, res.getOrThrow())
      }

      val outline = extractor.extractOutline()
      assertEquals(9, outline.size)
      assertEquals("Chapter 1", outline.first().title)
      assertEquals(0, outline.first().pageIndex)
      assertEquals(200, outline.last().pageIndex)

      // Cold extract single page 42
      val page42ColdMs = measureTimeMillis {
        val pageData = extractor.getOrExtractPageText(42)
        assertNotNull(pageData)
        assertTrue(pageData!!.hasTextLayer)
        assertTrue(pageData.rawText.contains("Quarterly Financial"))
      }

      // Reopen fresh extractor to verify disk cache read latency
      extractor.close()
      val warmExtractor = PdfTextAndOutlineExtractor(context)
      warmExtractor.open(uriString, pdfFile.length())
      val page42DiskCacheMs = measureTimeMillis {
        val cached = warmExtractor.getOrExtractPageText(42)
        assertNotNull(cached)
        assertTrue(cached!!.rawText.contains("Quarterly Financial"))
      }

      // Search all 220 pages lazily
      val allHits = ArrayList<PdfSearchMatch>()
      val fullSearchMs = measureTimeMillis {
        for (p in 0 until 220) {
          val data = warmExtractor.getOrExtractPageText(p) ?: continue
          allHits.addAll(PdfSearchMatcher.findMatchesInPage(data, "quarterly financial"))
        }
      }

      assertEquals(3, allHits.size)
      assertEquals(listOf(42, 137, 205), allHits.map { it.pageIndex })

      // Verify controller behavior safely on JVM
      val controller = PdfDocumentController(context)
      val rendererResult = controller.open(uriString)
      // Log benchmark metrics for test output inspection
      println(
        "PRISM_BENCHMARK_220P: fileBytes=${pdfFile.length()} pdfBoxOpenMs=$openMs " +
          "page42ColdMs=$page42ColdMs page42DiskCacheMs=$page42DiskCacheMs " +
          "full220PageSearchMs=$fullSearchMs pdfRendererOnJvm=${rendererResult.isSuccess}"
      )

      controller.close()
      warmExtractor.close()
      pdfFile.delete()
    }
  }

  @Test
  fun emptyTextPageFlagsHasTextLayerFalseForScannedChip() {
    runBlocking {
      val context = ApplicationProvider.getApplicationContext<Context>()
      PDFBoxResourceLoader.init(context)
      val outFile = File(context.cacheDir, "scanned_stub.pdf")
      if (outFile.exists()) outFile.delete()
      PDDocument().use { doc ->
        doc.addPage(PDPage(PDRectangle.LETTER))
        doc.save(outFile)
      }

      val extractor = PdfTextAndOutlineExtractor(context)
      extractor.open(Uri.fromFile(outFile).toString(), outFile.length())
      val page0 = extractor.getOrExtractPageText(0)
      assertNotNull(page0)
      assertFalse(page0!!.hasTextLayer)
      extractor.close()
      outFile.delete()
    }
  }
}

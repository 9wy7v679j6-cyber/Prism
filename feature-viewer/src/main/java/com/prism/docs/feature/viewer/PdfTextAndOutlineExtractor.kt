package com.prism.docs.feature.viewer

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Lazy per-page PDF text & character-position extractor and outline loader backed by PdfBox-Android.
 * Extracted pages are cached both in-memory and on disk under `cacheDir/prism_pdf_text` so
 * subsequent opens and searches remain instant without re-parsing the PDF stream.
 */
class PdfTextAndOutlineExtractor(
  private val context: Context,
) : Closeable {

  private val pdfBoxMutex = Mutex()
  private var pdDocument: PDDocument? = null
  private var docCacheKey: String = ""
  private val memoryPageCache = ConcurrentHashMap<Int, PdfPageTextData>()

  @Volatile var pageCount: Int = 0
    private set

  suspend fun open(uriString: String, sizeBytes: Long): Result<Int> =
    withContext(Dispatchers.IO) {
      pdfBoxMutex.withLock {
        runCatching {
          ensurePdfBoxInitialized(context.applicationContext)
          closeInternal()
          docCacheKey = computeDocCacheKey(uriString, sizeBytes)
          val inputStream =
            openInputStream(context, uriString)
              ?: throw IllegalStateException("Cannot open PDF stream for $uriString")
          val doc =
            inputStream.use { stream ->
              PDDocument.load(stream)
            }
          pdDocument = doc
          pageCount = doc.numberOfPages.coerceAtLeast(0)
          pageCount
        }.onFailure {
          closeInternal()
        }
      }
    }

  suspend fun extractOutline(): List<PdfOutlineNode> =
    withContext(Dispatchers.Default) {
      pdfBoxMutex.withLock {
        val doc = pdDocument ?: return@withLock emptyList()
        runCatching {
          val outline = doc.documentCatalog?.documentOutline ?: return@runCatching emptyList()
          val pageTree = doc.pages
          val nodes = ArrayList<PdfOutlineNode>()
          fun walk(item: PDOutlineItem?, depth: Int) {
            var current = item
            var guard = 0
            while (current != null && guard < 1500) {
              guard++
              val title = current.title?.trim().orEmpty()
              val destPage = runCatching { current.findDestinationPage(doc) }.getOrNull()
              val pageIdx =
                if (destPage != null) {
                  runCatching { pageTree.indexOf(destPage) }.getOrDefault(-1)
                } else {
                  -1
                }
              if (title.isNotEmpty() && pageIdx in 0 until doc.numberOfPages) {
                nodes.add(PdfOutlineNode(title = title, pageIndex = pageIdx, depth = depth.coerceAtMost(4)))
              }
              if (current.hasChildren() && depth < 4) {
                walk(current.firstChild, depth + 1)
              }
              current = current.nextSibling
            }
          }
          walk(outline.firstChild, 0)
          nodes
        }.getOrDefault(emptyList())
      }
    }

  suspend fun getOrExtractPageText(pageIndex: Int): PdfPageTextData? =
    withContext(Dispatchers.Default) {
      memoryPageCache[pageIndex]?.let { return@withContext it }
      ensureActive()

      // 1. Check disk cache before invoking PdfBox
      readFromDiskCache(pageIndex)?.let { cached ->
        memoryPageCache[pageIndex] = cached
        return@withContext cached
      }

      // 2. Lazily parse the requested page under the PDDocument mutex on Dispatchers.Default
      val extracted =
        pdfBoxMutex.withLock {
          ensureActive()
          memoryPageCache[pageIndex]?.let { return@withLock it }
          val doc = pdDocument ?: return@withLock null
          if (pageIndex !in 0 until doc.numberOfPages) return@withLock null
          try {
            val page = doc.getPage(pageIndex)
            extractSinglePageData(doc, page, pageIndex)
          } catch (ce: CancellationException) {
            throw ce
          } catch (_: Throwable) {
            PdfPageTextData(
              pageIndex = pageIndex,
              rawText = "",
              charBoxes = emptyList(),
              hasTextLayer = false,
            )
          }
        } ?: return@withContext null

      memoryPageCache[pageIndex] = extracted
      writeToDiskCache(extracted)
      extracted
    }

  fun peekCachedPageText(pageIndex: Int): PdfPageTextData? = memoryPageCache[pageIndex]

  private fun extractSinglePageData(
    doc: PDDocument,
    page: PDPage,
    pageIndex: Int,
  ): PdfPageTextData {
    val cropBox = page.cropBox ?: page.mediaBox
    val pageWidth = cropBox?.width?.takeIf { it > 1f } ?: 612f
    val pageHeight = cropBox?.height?.takeIf { it > 1f } ?: 792f

    val builder = StringBuilder(1024)
    val boxes = ArrayList<PdfCharBox>(1024)

    val stripper =
      object : PDFTextStripper() {
        init {
          sortByPosition = true
          startPage = pageIndex + 1
          endPage = pageIndex + 1
        }

        override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
          if (text.isEmpty()) return
          val fallbackBox =
            textPositions.firstOrNull()?.let { tp ->
              toNormalizedBox(tp, pageWidth, pageHeight)
            } ?: PdfCharBox(0f, 0f, 0f, 0f)

          for (i in text.indices) {
            val ch = text[i]
            val tp = textPositions.getOrNull(i)
            val box =
              if (tp != null) toNormalizedBox(tp, pageWidth, pageHeight) else fallbackBox
            builder.append(ch)
            boxes.add(box)
          }
        }

        override fun writeWordSeparator() {
          builder.append(' ')
          val prev = boxes.lastOrNull()
          if (prev != null) {
            boxes.add(PdfCharBox(prev.right, prev.top, prev.right, prev.bottom))
          } else {
            boxes.add(PdfCharBox(0f, 0f, 0f, 0f))
          }
        }

        override fun writeLineSeparator() {
          builder.append('\n')
          val prev = boxes.lastOrNull()
          if (prev != null) {
            boxes.add(PdfCharBox(prev.right, prev.top, prev.right, prev.bottom))
          } else {
            boxes.add(PdfCharBox(0f, 0f, 0f, 0f))
          }
        }
      }

    stripper.getText(doc)
    val raw = builder.toString()
    val hasText = raw.any { !it.isWhitespace() }
    return PdfPageTextData(
      pageIndex = pageIndex,
      rawText = raw,
      charBoxes = boxes,
      hasTextLayer = hasText,
    )
  }

  private fun toNormalizedBox(
    tp: TextPosition,
    pageWidth: Float,
    pageHeight: Float,
  ): PdfCharBox {
    val left = (tp.xDirAdj / pageWidth).coerceIn(0f, 1f)
    val width = (tp.widthDirAdj / pageWidth).coerceIn(0f, 1f)
    val height = (tp.heightDir.coerceAtLeast(tp.fontSizeInPt * 0.65f) / pageHeight).coerceIn(0.004f, 0.25f)
    // TextPosition.yDirAdj is baseline/bottom Y measured from top of page
    val bottom = ((tp.yDirAdj + tp.heightDir * 0.22f) / pageHeight).coerceIn(0f, 1f)
    val top = (bottom - height * 1.15f).coerceIn(0f, bottom)
    val right = (left + width).coerceIn(left, 1f)
    return PdfCharBox(left = left, top = top, right = right, bottom = bottom)
  }

  private fun diskCacheFile(pageIndex: Int): File {
    val dir = File(context.cacheDir, "prism_pdf_text/$docCacheKey")
    if (!dir.exists()) dir.mkdirs()
    return File(dir, "p_$pageIndex.bin")
  }

  private fun readFromDiskCache(pageIndex: Int): PdfPageTextData? {
    if (docCacheKey.isEmpty()) return null
    val file = File(context.cacheDir, "prism_pdf_text/$docCacheKey/p_$pageIndex.bin")
    if (!file.exists() || file.length() < 12L) return null
    return runCatching {
      DataInputStream(BufferedInputStream(FileInputStream(file))).use { input ->
        val magic = input.readInt()
        if (magic != CACHE_MAGIC) return@use null
        val hasText = input.readBoolean()
        val textLen = input.readInt().coerceIn(0, 2_000_000)
        val chars = CharArray(textLen)
        for (i in 0 until textLen) {
          chars[i] = input.readChar()
        }
        val boxCount = input.readInt().coerceIn(0, 2_000_000)
        val boxes = ArrayList<PdfCharBox>(boxCount)
        for (i in 0 until boxCount) {
          boxes.add(
            PdfCharBox(
              left = input.readFloat(),
              top = input.readFloat(),
              right = input.readFloat(),
              bottom = input.readFloat(),
            )
          )
        }
        PdfPageTextData(
          pageIndex = pageIndex,
          rawText = String(chars),
          charBoxes = boxes,
          hasTextLayer = hasText,
        )
      }
    }.getOrNull()
  }

  private fun writeToDiskCache(data: PdfPageTextData) {
    if (docCacheKey.isEmpty()) return
    runCatching {
      val file = diskCacheFile(data.pageIndex)
      DataOutputStream(BufferedOutputStream(FileOutputStream(file))).use { out ->
        out.writeInt(CACHE_MAGIC)
        out.writeBoolean(data.hasTextLayer)
        out.writeInt(data.rawText.length)
        for (i in data.rawText.indices) {
          out.writeChar(data.rawText[i].code)
        }
        out.writeInt(data.charBoxes.size)
        for (box in data.charBoxes) {
          out.writeFloat(box.left)
          out.writeFloat(box.top)
          out.writeFloat(box.right)
          out.writeFloat(box.bottom)
        }
      }
    }
  }

  override fun close() {
    closeInternal()
  }

  private fun closeInternal() {
    memoryPageCache.clear()
    runCatching { pdDocument?.close() }
    pdDocument = null
    pageCount = 0
  }

  companion object {
    private const val CACHE_MAGIC = 0x50525431 // "PRT1"
    private val initialized = AtomicBoolean(false)

    fun ensurePdfBoxInitialized(appContext: Context) {
      if (initialized.compareAndSet(false, true)) {
        runCatching { PDFBoxResourceLoader.init(appContext) }
      }
    }

    fun openInputStream(context: Context, uriString: String): InputStream? {
      if (uriString.isBlank()) return null
      val parsed = Uri.parse(uriString)
      return when (parsed.scheme?.lowercase()) {
        null, "" -> {
          val f = File(uriString)
          if (f.exists() && f.canRead()) FileInputStream(f) else null
        }
        "file" -> {
          val path = parsed.path ?: return null
          val f = File(path)
          if (f.exists() && f.canRead()) FileInputStream(f) else null
        }
        else -> runCatching { context.contentResolver.openInputStream(parsed) }.getOrNull()
      }
    }

    private fun computeDocCacheKey(uriString: String, sizeBytes: Long): String {
      val digest = MessageDigest.getInstance("SHA-256").digest("$uriString|$sizeBytes".toByteArray())
      return digest.take(12).joinToString("") { "%02x".format(it) }
    }
  }
}

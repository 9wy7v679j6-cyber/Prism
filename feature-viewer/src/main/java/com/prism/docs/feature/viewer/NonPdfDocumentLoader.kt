package com.prism.docs.feature.viewer

import android.content.Context
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class LoadedTextDocument(
  val rawText: String,
  val formattedJsonText: String?,
  val isTruncated: Boolean,
  val loadedBytes: Int,
)

object NonPdfDocumentLoader {

  private const val MAX_TEXT_PREVIEW_BYTES = 1_500_000 // 1.5 MB cap for smooth UI rendering

  suspend fun loadTextDocument(
    context: Context,
    uriString: String,
    kind: ViewerDocumentKind,
  ): Result<LoadedTextDocument> =
    withContext(Dispatchers.IO) {
      runCatching {
        val stream =
          PdfTextAndOutlineExtractor.openInputStream(context, uriString)
            ?: throw IllegalStateException("Cannot open document stream")
        val baos = ByteArrayOutputStream(8192)
        var truncated = false
        stream.use { input ->
          val buffer = ByteArray(16 * 1024)
          var remaining = MAX_TEXT_PREVIEW_BYTES
          while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size, remaining))
            if (read <= 0) break
            baos.write(buffer, 0, read)
            remaining -= read
          }
          if (remaining == 0 && input.read() != -1) {
            truncated = true
          }
        }
        val bytes = baos.toByteArray()
        val text = String(bytes, StandardCharsets.UTF_8)
        val prettyJson =
          if (kind == ViewerDocumentKind.JSON && !truncated && text.length <= 512_000) {
            formatJsonSafely(text)
          } else {
            null
          }
        LoadedTextDocument(
          rawText = text,
          formattedJsonText = prettyJson,
          isTruncated = truncated,
          loadedBytes = bytes.size,
        )
      }
    }

  fun formatJsonSafely(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    return runCatching {
      when {
        trimmed.startsWith("{") -> JSONObject(trimmed).toString(2)
        trimmed.startsWith("[") -> JSONArray(trimmed).toString(2)
        else -> null
      }
    }.getOrNull()
  }

  fun parseCsvPreviewLines(raw: String, maxLines: Int = 500): List<List<String>> {
    val rows = ArrayList<List<String>>()
    val lines = raw.lineSequence().take(maxLines)
    for (line in lines) {
      if (line.isBlank()) continue
      val delimiter = if (line.count { it == '\t' } > line.count { it == ',' }) '\t' else ','
      rows.add(splitCsvRow(line, delimiter))
    }
    return rows
  }

  private fun splitCsvRow(line: String, delimiter: Char): List<String> {
    val cells = ArrayList<String>()
    val cur = StringBuilder()
    var inQuotes = false
    var i = 0
    while (i < line.length) {
      val ch = line[i]
      when {
        ch == '"' -> {
          if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
            cur.append('"')
            i++
          } else {
            inQuotes = !inQuotes
          }
        }
        ch == delimiter && !inQuotes -> {
          cells.add(cur.toString().trim())
          cur.clear()
        }
        else -> cur.append(ch)
      }
      i++
    }
    cells.add(cur.toString().trim())
    return cells
  }
}

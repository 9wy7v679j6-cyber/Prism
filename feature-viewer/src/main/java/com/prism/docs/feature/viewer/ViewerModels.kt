package com.prism.docs.feature.viewer

import androidx.compose.runtime.Immutable

enum class ViewerDocumentKind {
  PDF,
  IMAGE,
  TEXT,
  MARKDOWN,
  CODE,
  CSV,
  JSON,
  UNSUPPORTED,
  ERROR,
}

enum class PdfScrollMode {
  CONTINUOUS,
  PAGED,
}

enum class ReaderPaperMode {
  LIGHT,
  SEPIA,
  DARK,
}

@Immutable
data class NormalizedRect(
  val left: Float,
  val top: Float,
  val right: Float,
  val bottom: Float,
)

@Immutable
data class PdfCharBox(
  val left: Float,
  val top: Float,
  val right: Float,
  val bottom: Float,
) {
  fun toNormalizedRect(): NormalizedRect = NormalizedRect(left, top, right, bottom)
}

data class PdfPageTextData(
  val pageIndex: Int,
  val rawText: String,
  val charBoxes: List<PdfCharBox>,
  val hasTextLayer: Boolean,
)

@Immutable
data class PdfSearchMatch(
  val pageIndex: Int,
  val startCharIndex: Int,
  val endCharIndex: Int,
  val matchText: String,
  val rects: List<NormalizedRect>,
)

@Immutable
data class PdfOutlineNode(
  val title: String,
  val pageIndex: Int,
  val depth: Int,
)

@Immutable
data class TextSelectionRange(
  val pageIndex: Int,
  val startIndex: Int,
  val endIndex: Int,
  val selectedText: String,
  val rects: List<NormalizedRect>,
  val startAnchor: NormalizedRect?,
  val endAnchor: NormalizedRect?,
)

@Immutable
data class TileRenderSpec(
  val scaleFactor: Float,
  val regionLeftFraction: Float,
  val regionTopFraction: Float,
  val regionRightFraction: Float,
  val regionBottomFraction: Float,
  val targetWidthPx: Int,
  val targetHeightPx: Int,
)

fun resolveViewerKind(displayName: String, mimeType: String): ViewerDocumentKind {
  val lowerMime = mimeType.lowercase().substringBefore(';').trim()
  val ext = displayName.substringAfterLast('.', "").lowercase()
  return when {
    lowerMime == "application/pdf" || ext == "pdf" -> ViewerDocumentKind.PDF
    lowerMime.startsWith("image/") ||
      ext in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif") ->
      ViewerDocumentKind.IMAGE
    lowerMime == "application/json" || ext in setOf("json", "jsonl") -> ViewerDocumentKind.JSON
    lowerMime == "text/csv" || lowerMime == "text/comma-separated-values" || ext in setOf("csv", "tsv") ->
      ViewerDocumentKind.CSV
    lowerMime == "text/markdown" || ext in setOf("md", "markdown") -> ViewerDocumentKind.MARKDOWN
    ext in setOf(
      "kt", "kts", "java", "py", "js", "ts", "tsx", "jsx", "xml", "html", "htm", "css",
      "yaml", "yml", "toml", "ini", "cfg", "conf", "sh", "bash", "sql", "c", "cpp", "h",
      "hpp", "rs", "go", "gradle", "properties", "pro",
    ) || lowerMime in setOf("application/xml", "text/xml", "application/javascript") ->
      ViewerDocumentKind.CODE
    lowerMime.startsWith("text/") || ext in setOf("txt", "log", "rtf") -> ViewerDocumentKind.TEXT
    else -> ViewerDocumentKind.UNSUPPORTED
  }
}

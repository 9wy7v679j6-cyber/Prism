package com.prism.docs.core.data.storage

import android.webkit.MimeTypeMap
import com.prism.docs.core.data.model.DocumentCategory

/**
 * Deterministic MIME + file extension classifier mapping every file on device to one of Prism's
 * 11 library categories.
 */
object DocumentCategoryClassifier {

  private val documentExtensions = setOf("doc", "docx", "odt", "rtf", "pages", "wpd")
  private val spreadsheetExtensions = setOf("xls", "xlsx", "ods", "csv", "tsv", "numbers")
  private val slideExtensions = setOf("ppt", "pptx", "odp", "key")
  private val imageExtensions =
    setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "heif", "bmp", "svg", "tif", "tiff", "avif")
  private val videoExtensions = setOf("mp4", "mkv", "webm", "mov", "avi", "m4v", "3gp", "ts")
  private val audioExtensions = setOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus", "wma")
  private val archiveExtensions = setOf("zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz")
  private val ebookExtensions = setOf("epub", "mobi", "azw", "azw3", "fb2", "cbz", "cbr")
  private val codeTextExtensions =
    setOf(
      "txt",
      "md",
      "markdown",
      "json",
      "xml",
      "yaml",
      "yml",
      "toml",
      "ini",
      "cfg",
      "log",
      "html",
      "htm",
      "css",
      "js",
      "ts",
      "kt",
      "kts",
      "java",
      "py",
      "c",
      "cpp",
      "h",
      "hpp",
      "rs",
      "go",
      "sh",
      "sql",
      "gradle",
      "properties",
    )

  fun extractExtension(displayName: String): String {
    val dot = displayName.lastIndexOf('.')
    if (dot <= 0 || dot == displayName.lastIndex) return ""
    return displayName.substring(dot + 1).lowercase()
  }

  fun inferMimeType(displayName: String, providedMime: String?): String {
    if (!providedMime.isNullOrBlank() && providedMime != "application/octet-stream" && providedMime != "*/*") {
      return providedMime.lowercase()
    }
    val ext = extractExtension(displayName)
    if (ext.isBlank()) return providedMime?.lowercase() ?: "application/octet-stream"
    return when (ext) {
      "pdf" -> "application/pdf"
      "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
      "doc" -> "application/msword"
      "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
      "xls" -> "application/vnd.ms-excel"
      "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
      "ppt" -> "application/vnd.ms-powerpoint"
      "odt" -> "application/vnd.oasis.opendocument.text"
      "ods" -> "application/vnd.oasis.opendocument.spreadsheet"
      "odp" -> "application/vnd.oasis.opendocument.presentation"
      "epub" -> "application/epub+zip"
      "md", "markdown" -> "text/markdown"
      "csv" -> "text/csv"
      "json" -> "application/json"
      "xml" -> "application/xml"
      "7z" -> "application/x-7z-compressed"
      "rar" -> "application/vnd.rar"
      else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }
  }

  fun classify(mimeType: String?, displayName: String): DocumentCategory {
    val ext = extractExtension(displayName)
    val mime = mimeType?.lowercase().orEmpty()

    return when {
      mime == "application/pdf" || ext == "pdf" -> DocumentCategory.PDF
      ext in spreadsheetExtensions ||
        mime.contains("spreadsheet") ||
        mime == "application/vnd.ms-excel" ||
        mime == "text/csv" -> DocumentCategory.SPREADSHEETS
      ext in slideExtensions ||
        mime.contains("presentation") ||
        mime == "application/vnd.ms-powerpoint" -> DocumentCategory.SLIDES
      ext in documentExtensions ||
        mime.contains("wordprocessingml") ||
        mime == "application/msword" ||
        mime == "application/rtf" ||
        mime == "application/vnd.oasis.opendocument.text" -> DocumentCategory.DOCUMENTS
      ext in ebookExtensions || mime == "application/epub+zip" -> DocumentCategory.EBOOKS
      ext in archiveExtensions ||
        mime == "application/zip" ||
        mime == "application/x-7z-compressed" ||
        mime == "application/x-rar-compressed" ||
        mime == "application/vnd.rar" -> DocumentCategory.ARCHIVES
      mime.startsWith("image/") || ext in imageExtensions -> DocumentCategory.IMAGES
      mime.startsWith("video/") || ext in videoExtensions -> DocumentCategory.VIDEOS
      mime.startsWith("audio/") || ext in audioExtensions -> DocumentCategory.AUDIO
      mime.startsWith("text/") ||
        mime == "application/json" ||
        mime == "application/xml" ||
        ext in codeTextExtensions -> DocumentCategory.CODE_TEXT
      else -> DocumentCategory.OTHER
    }
  }
}

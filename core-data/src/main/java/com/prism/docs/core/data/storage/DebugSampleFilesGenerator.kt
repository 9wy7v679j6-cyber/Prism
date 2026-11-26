package com.prism.docs.core.data.storage

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.prism.docs.core.data.indexer.IncrementalDocumentIndexer
import com.prism.docs.core.data.paging.DocumentRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SampleGenerationResult(
  val createdCount: Int,
  val directoryLabel: String,
  val message: String,
)

/**
 * Debug-only generator that writes valid sample PDF, DOCX, XLSX, PPTX, and JPG documents
 * into `Documents/PrismSamples`, runs an incremental re-index, and marks three items as
 * recently opened so Recent cards, type badges, and real thumbnails can be verified immediately.
 */
@Singleton
class DebugSampleFilesGenerator
@Inject
constructor(
  @ApplicationContext private val context: Context,
  private val permissionManager: StoragePermissionManager,
  private val incrementalIndexer: IncrementalDocumentIndexer,
  private val documentRepository: DocumentRepository,
) {

  suspend fun createSamplesAndReindex(): SampleGenerationResult =
    withContext(Dispatchers.IO) {
      val specs = buildSampleSpecs()
      val writtenPaths = mutableListOf<String>()
      var createdCount = 0

      val canWriteDirect =
        permissionManager.hasAllFilesAccess() || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

      if (canWriteDirect) {
        val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val sampleDir = File(docsDir, SAMPLE_FOLDER_NAME)
        if (!sampleDir.exists()) {
          sampleDir.mkdirs()
        }
        val writtenFiles = createSampleFilesInDirectory(sampleDir, specs)
        val mimeByName = specs.associate { it.fileName to it.mimeType }
        val writtenMimeTypes = mutableListOf<String>()
        createdCount = writtenFiles.size
        writtenFiles.forEach { file ->
          writtenPaths += file.absolutePath
          writtenMimeTypes += mimeByName[file.name] ?: "application/octet-stream"
        }
        if (writtenPaths.isNotEmpty()) {
          runCatching {
            MediaScannerConnection.scanFile(
              context,
              writtenPaths.toTypedArray(),
              writtenMimeTypes.toTypedArray(),
              null,
            )
          }
        }
      } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        for (spec in specs) {
          val ok = writeSampleViaMediaStore(spec)
          if (ok) createdCount++
        }
      }

      // Immediately sync Room index so new sample files appear in Library without waiting for WorkManager
      runCatching { incrementalIndexer.runIncrementalSync() }

      // Mark 4 representative samples as recently opened so the Recent carousel displays thumbnails + badges
      val now = System.currentTimeMillis()
      val recentNames =
        listOf(
          "Pharmacology Notes.pdf",
          "Posting Log.xlsx",
          "Microbiology Slides.pptx",
          "Lab Diagram.jpg",
        )
      val activeDocs = documentRepository.observeActiveDocumentsSnapshot()
      recentNames.forEachIndexed { idx, targetName ->
        val match = activeDocs.firstOrNull { it.displayName.equals(targetName, ignoreCase = true) }
        if (match != null) {
          documentRepository.recordDocumentOpened(
            documentId = match.id,
            openedAtEpochMs = now - (idx * 60_000L),
          )
        }
      }

      SampleGenerationResult(
        createdCount = createdCount,
        directoryLabel = "Documents/$SAMPLE_FOLDER_NAME",
        message =
          if (createdCount > 0) "Created $createdCount sample files in Documents/$SAMPLE_FOLDER_NAME"
          else "Could not write sample files to Documents/$SAMPLE_FOLDER_NAME",
      )
    }

  internal fun createSampleFilesInDirectory(
    targetDir: File,
    specs: List<SampleFileSpec> = buildSampleSpecs(),
  ): List<File> {
    if (!targetDir.exists()) targetDir.mkdirs()
    val created = mutableListOf<File>()
    for (spec in specs) {
      val file = File(targetDir, spec.fileName)
      runCatching {
        file.outputStream().use { out -> out.write(spec.bytes) }
        created += file
      }
    }
    return created
  }

  private fun writeSampleViaMediaStore(spec: SampleFileSpec): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
    val isImage = spec.mimeType.startsWith("image/")
    val primaryRelPath = "${Environment.DIRECTORY_DOCUMENTS}/$SAMPLE_FOLDER_NAME/"
    val imageFallbackRelPath = "${Environment.DIRECTORY_PICTURES}/$SAMPLE_FOLDER_NAME/"

    val attempts =
      if (isImage) {
        listOf(
          MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to primaryRelPath,
          MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to imageFallbackRelPath,
        )
      } else {
        listOf(
          MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to primaryRelPath
        )
      }

    for ((collection, relPath) in attempts) {
      val ok =
        runCatching {
            // Delete any previously generated copy with the same display name in PrismSamples
            runCatching {
              context.contentResolver.delete(
                collection,
                "${MediaStore.Files.FileColumns.DISPLAY_NAME} = ? AND ${MediaStore.Files.FileColumns.RELATIVE_PATH} = ?",
                arrayOf(spec.fileName, relPath),
              )
            }

            val values =
              ContentValues().apply {
                put(MediaStore.Files.FileColumns.DISPLAY_NAME, spec.fileName)
                put(MediaStore.Files.FileColumns.MIME_TYPE, spec.mimeType)
                put(MediaStore.Files.FileColumns.RELATIVE_PATH, relPath)
                put(MediaStore.Files.FileColumns.IS_PENDING, 1)
              }

            val uri = context.contentResolver.insert(collection, values) ?: return@runCatching false
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out -> out.write(spec.bytes) }
                  ?: error("Failed to open output stream for $uri")
                val finish = ContentValues().apply { put(MediaStore.Files.FileColumns.IS_PENDING, 0) }
                context.contentResolver.update(uri, finish, null, null)
                true
              }
              .getOrElse {
                runCatching { context.contentResolver.delete(uri, null, null) }
                false
              }
          }
          .getOrDefault(false)
      if (ok) return true
    }
    return false
  }

  internal data class SampleFileSpec(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray,
  )

  internal fun buildSampleSpecs(): List<SampleFileSpec> =
    listOf(
      SampleFileSpec(
        fileName = "Pharmacology Notes.pdf",
        mimeType = "application/pdf",
        bytes =
          buildSamplePdfBytes(
            title = "Pharmacology Notes",
            subtitle = "Autonomic & Cardiovascular Systems",
            accentColor = Color.rgb(249, 178, 174),
            headerColor = Color.rgb(32, 42, 68),
            pageCount = 3,
          ),
      ),
      SampleFileSpec(
        fileName = "Anatomy Atlas.pdf",
        mimeType = "application/pdf",
        bytes =
          buildSamplePdfBytes(
            title = "Anatomy Atlas",
            subtitle = "Thoracic & Cranial Plates — Edition IV",
            accentColor = Color.rgb(217, 56, 58),
            headerColor = Color.rgb(25, 36, 58),
            pageCount = 2,
          ),
      ),
      SampleFileSpec(
        fileName = "Research Proposal.docx",
        mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        bytes =
          buildSampleOoxmlBytes(
            contentEntryPath = "word/document.xml",
            contentXml =
              """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body>
    <w:p><w:r><w:t>Prism Offline Research Proposal — Clinical Cohort Study</w:t></w:r></w:p>
    <w:p><w:r><w:t>Prepared for ethics review and local archival.</w:t></w:r></w:p>
  </w:body>
</w:document>""",
            contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml",
            thumbnailJpeg = buildDocumentThumbnailJpeg(),
          ),
      ),
      SampleFileSpec(
        fileName = "Posting Log.xlsx",
        mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        bytes =
          buildSampleOoxmlBytes(
            contentEntryPath = "xl/workbook.xml",
            contentXml =
              """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <sheets>
    <sheet name="Rotation Log" sheetId="1" r:id="rId1" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"/>
  </sheets>
</workbook>""",
            contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml",
            thumbnailJpeg = buildSpreadsheetThumbnailJpeg(),
          ),
      ),
      SampleFileSpec(
        fileName = "Microbiology Slides.pptx",
        mimeType = "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        bytes =
          buildSampleOoxmlBytes(
            contentEntryPath = "ppt/presentation.xml",
            contentXml =
              """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:presentation xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
  <p:sldIdLst/>
</p:presentation>""",
            contentType = "application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml",
            thumbnailJpeg = buildSlidesThumbnailJpeg(),
          ),
      ),
      SampleFileSpec(
        fileName = "Scan 20 Nov.pdf",
        mimeType = "application/pdf",
        bytes =
          buildSamplePdfBytes(
            title = "Scan 20 Nov",
            subtitle = "6 pages • text recognised offline",
            accentColor = Color.rgb(123, 60, 233),
            headerColor = Color.rgb(45, 32, 82),
            pageCount = 2,
          ),
      ),
      SampleFileSpec(
        fileName = "Lab Diagram.jpg",
        mimeType = "image/jpeg",
        bytes = buildSampleJpgBytes(),
      ),
    )

  private fun buildSamplePdfBytes(
    title: String,
    subtitle: String,
    accentColor: Int,
    headerColor: Int,
    pageCount: Int,
  ): ByteArray {
    val doc = PdfDocument()
    return try {
      for (pageIdx in 1..pageCount) {
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageIdx).create()
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas
        canvas.drawColor(Color.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        // Top heavy title bar (echoes clean document card layout)
        paint.color = headerColor
        canvas.drawRoundRect(RectF(56f, 64f, 350f, 90f), 12f, 12f, paint)

        // Title & subtitle text
        paint.color = Color.rgb(20, 30, 50)
        paint.textSize = 26f
        paint.isFakeBoldText = true
        canvas.drawText(title, 56f, 146f, paint)

        paint.color = Color.rgb(92, 108, 134)
        paint.textSize = 15f
        paint.isFakeBoldText = false
        canvas.drawText("$subtitle — Page $pageIdx of $pageCount", 56f, 174f, paint)

        // Subtle text lines
        paint.color = Color.rgb(212, 222, 236)
        canvas.drawRoundRect(RectF(56f, 210f, 520f, 226f), 8f, 8f, paint)
        canvas.drawRoundRect(RectF(56f, 244f, 470f, 260f), 8f, 8f, paint)
        canvas.drawRoundRect(RectF(56f, 278f, 504f, 294f), 8f, 8f, paint)

        // Colored highlight diagram panel
        paint.color = accentColor
        canvas.drawRoundRect(RectF(56f, 336f, 538f, 560f), 28f, 28f, paint)

        // Footer lines
        paint.color = Color.rgb(212, 222, 236)
        canvas.drawRoundRect(RectF(56f, 606f, 420f, 622f), 8f, 8f, paint)
        canvas.drawRoundRect(RectF(56f, 640f, 490f, 656f), 8f, 8f, paint)

        doc.finishPage(page)
      }
      ByteArrayOutputStream().use { out ->
        doc.writeTo(out)
        out.toByteArray()
      }
    } finally {
      doc.close()
    }
  }

  private fun buildSampleOoxmlBytes(
    contentEntryPath: String,
    contentXml: String,
    contentType: String,
    thumbnailJpeg: ByteArray,
  ): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
      writeZipString(
        zip,
        "[Content_Types].xml",
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Default Extension="jpeg" ContentType="image/jpeg"/>
  <Override PartName="/$contentEntryPath" ContentType="$contentType"/>
</Types>""",
      )
      writeZipString(
        zip,
        "_rels/.rels",
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="$contentEntryPath"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/thumbnail" Target="docProps/thumbnail.jpeg"/>
</Relationships>""",
      )
      writeZipString(zip, contentEntryPath, contentXml)
      zip.putNextEntry(ZipEntry("docProps/thumbnail.jpeg"))
      zip.write(thumbnailJpeg)
      zip.closeEntry()
    }
    return out.toByteArray()
  }

  private fun writeZipString(zip: ZipOutputStream, entryName: String, content: String) {
    zip.putNextEntry(ZipEntry(entryName))
    zip.write(content.toByteArray(Charsets.UTF_8))
    zip.closeEntry()
  }

  private fun buildSpreadsheetThumbnailJpeg(): ByteArray {
    val bmp = Bitmap.createBitmap(360, 320, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(Color.WHITE)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    // Soft green header row
    paint.color = Color.rgb(194, 240, 211)
    canvas.drawRect(0f, 0f, 360f, 64f, paint)

    // Grid lines
    paint.color = Color.rgb(214, 224, 236)
    paint.strokeWidth = 2.5f
    val cols = 4
    val rows = 6
    val colW = 360f / cols
    val rowH = 320f / rows
    for (c in 1 until cols) {
      canvas.drawLine(c * colW, 0f, c * colW, 320f, paint)
    }
    for (r in 1 until rows) {
      canvas.drawLine(0f, r * rowH, 360f, r * rowH, paint)
    }

    // Highlighted active cell
    paint.style = Paint.Style.FILL
    paint.color = Color.rgb(225, 247, 234)
    canvas.drawRect(colW, rowH * 2f, colW * 2f, rowH * 3f, paint)
    paint.style = Paint.Style.STROKE
    paint.color = Color.rgb(21, 122, 66)
    paint.strokeWidth = 5f
    canvas.drawRect(colW, rowH * 2f, colW * 2f, rowH * 3f, paint)

    return compressJpeg(bmp)
  }

  private fun buildSlidesThumbnailJpeg(): ByteArray {
    val bmp = Bitmap.createBitmap(360, 320, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(Color.rgb(255, 223, 196))

    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = Color.rgb(163, 73, 21)
    canvas.drawRoundRect(RectF(32f, 52f, 260f, 78f), 12f, 12f, paint)

    paint.color = Color.rgb(217, 99, 27)
    canvas.drawCircle(54f, 134f, 12f, paint)
    canvas.drawRoundRect(RectF(80f, 124f, 310f, 144f), 8f, 8f, paint)

    paint.color = Color.WHITE
    canvas.drawRoundRect(RectF(32f, 176f, 328f, 286f), 20f, 20f, paint)

    return compressJpeg(bmp)
  }

  private fun buildDocumentThumbnailJpeg(): ByteArray {
    val bmp = Bitmap.createBitmap(360, 320, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(Color.WHITE)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = Color.rgb(35, 108, 224)
    canvas.drawRoundRect(RectF(30f, 36f, 230f, 56f), 10f, 10f, paint)

    paint.color = Color.rgb(212, 222, 238)
    for (i in 0..3) {
      val y = 84f + i * 28f
      val right = if (i == 3) 240f else 330f
      canvas.drawRoundRect(RectF(30f, y, right, y + 14f), 7f, 7f, paint)
    }

    paint.color = Color.rgb(216, 232, 255)
    canvas.drawRoundRect(RectF(30f, 208f, 330f, 286f), 18f, 18f, paint)

    return compressJpeg(bmp)
  }

  private fun buildSampleJpgBytes(): ByteArray {
    val bmp = Bitmap.createBitmap(480, 360, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(Color.rgb(236, 243, 255))

    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = Color.rgb(107, 158, 255)
    canvas.drawCircle(130f, 130f, 96f, paint)

    paint.color = Color.rgb(182, 142, 255)
    canvas.drawCircle(350f, 150f, 110f, paint)

    paint.color = Color.rgb(102, 236, 206)
    canvas.drawCircle(240f, 280f, 108f, paint)

    paint.color = Color.WHITE
    canvas.drawRoundRect(RectF(72f, 92f, 408f, 268f), 28f, 28f, paint)

    paint.color = Color.rgb(35, 108, 224)
    paint.textSize = 30f
    paint.isFakeBoldText = true
    canvas.drawText("Prism Sample", 136f, 174f, paint)

    paint.color = Color.rgb(79, 93, 120)
    paint.textSize = 18f
    paint.isFakeBoldText = false
    canvas.drawText("Offline Image Thumbnail Preview", 104f, 212f, paint)

    return compressJpeg(bmp)
  }

  private fun compressJpeg(bitmap: Bitmap): ByteArray {
    return try {
      ByteArrayOutputStream().use { out: OutputStream ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        (out as ByteArrayOutputStream).toByteArray()
      }
    } finally {
      bitmap.recycle()
    }
  }

  companion object {
    const val SAMPLE_FOLDER_NAME: String = "PrismSamples"
  }
}

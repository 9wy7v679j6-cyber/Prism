package com.prism.docs.core.data.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.prism.docs.core.data.model.DocumentCategory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DocumentThumbnailPipelineTest {

  private lateinit var context: Context
  private lateinit var tempDir: File
  private lateinit var cacheDir: File
  private lateinit var pipeline: DocumentThumbnailPipeline

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    tempDir = File(context.cacheDir, "thumb_test_files_${System.nanoTime()}").apply { mkdirs() }
    cacheDir = File(context.cacheDir, "thumb_test_cache_${System.nanoTime()}").apply { mkdirs() }
    pipeline =
      DocumentThumbnailPipeline(
        context = context,
        diskCacheDir = cacheDir,
        maxDiskBytes = 256L * 1024L,
        maxMemoryCacheKb = 4 * 1024,
      )
  }

  @After
  fun tearDown() {
    tempDir.deleteRecursively()
    cacheDir.deleteRecursively()
  }

  @Test
  fun cacheKeyChangesWhenPathModifiedTimeOrSizeChanges() {
    val base = ThumbnailCacheKey.compute("/storage/a.pdf", 1_000L, 4096L, 320)
    val diffPath = ThumbnailCacheKey.compute("/storage/b.pdf", 1_000L, 4096L, 320)
    val diffModified = ThumbnailCacheKey.compute("/storage/a.pdf", 2_000L, 4096L, 320)
    val diffSize = ThumbnailCacheKey.compute("/storage/a.pdf", 1_000L, 8192L, 320)

    assertNotEquals(base, diffPath)
    assertNotEquals(base, diffModified)
    assertNotEquals(base, diffSize)
  }

  @Test
  fun ooxmlExtractsDocPropsThumbnailJpegWhenPresentAndReturnsNullWhenAbsent() = runTest {
    val jpegBytes = createJpegBytes(width = 160, height = 120, color = Color.BLUE)
    val docxWithThumb =
      createZipFile(
        name = "proposal.docx",
        entries =
          mapOf(
            "word/document.xml" to "<w:document/>".toByteArray(),
            "docProps/thumbnail.jpeg" to jpegBytes,
          ),
      )

    val reqWithThumb =
      ThumbnailRequest(
        uriString = Uri.fromFile(docxWithThumb).toString(),
        displayName = docxWithThumb.name,
        mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        category = DocumentCategory.DOCUMENTS,
        lastModifiedEpochMs = docxWithThumb.lastModified(),
        sizeBytes = docxWithThumb.length(),
        targetSizePx = 128,
      )

    val bitmap = pipeline.loadThumbnail(reqWithThumb)
    assertNotNull("Expected embedded docProps/thumbnail.jpeg to decode", bitmap)
    assertNotNull("Expected decoded thumbnail in memory cache", pipeline.peekMemoryCache(reqWithThumb))

    // Verify disk cache hit on a fresh pipeline pointing to the same disk cache directory
    val coldPipeline =
      DocumentThumbnailPipeline(
        context = context,
        diskCacheDir = cacheDir,
        maxDiskBytes = 256L * 1024L,
        maxMemoryCacheKb = 4 * 1024,
      )
    assertNull(coldPipeline.peekMemoryCache(reqWithThumb))
    val fromDisk = coldPipeline.loadThumbnail(reqWithThumb)
    assertNotNull("Expected thumbnail to load from disk cache", fromDisk)
    assertTrue(coldPipeline.computeCacheStats().diskEntryCount >= 1)

    // OOXML without docProps/thumbnail.jpeg must return null (falls back to type-icon card in UI)
    val docxNoThumb =
      createZipFile(
        name = "plain.docx",
        entries = mapOf("word/document.xml" to "<w:document/>".toByteArray()),
      )
    val reqNoThumb =
      ThumbnailRequest(
        uriString = Uri.fromFile(docxNoThumb).toString(),
        displayName = docxNoThumb.name,
        mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        category = DocumentCategory.DOCUMENTS,
        lastModifiedEpochMs = docxNoThumb.lastModified(),
        sizeBytes = docxNoThumb.length(),
      )
    assertNull(pipeline.loadThumbnail(reqNoThumb))
    assertTrue(pipeline.hasKnownMissingThumbnail(reqNoThumb))

    // Clearing caches frees disk bytes
    val freed = coldPipeline.clearCaches()
    assertTrue(freed > 0L)
    assertEquals(0, coldPipeline.computeCacheStats().diskEntryCount)
  }

  private fun createJpegBytes(width: Int, height: Int, color: Int): ByteArray {
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    bmp.eraseColor(color)
    return ByteArrayOutputStream().use { out ->
      bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
      bmp.recycle()
      out.toByteArray()
    }
  }

  private fun createZipFile(name: String, entries: Map<String, ByteArray>): File {
    val file = File(tempDir, name)
    ZipOutputStream(FileOutputStream(file)).use { zip ->
      for ((entryName, content) in entries) {
        zip.putNextEntry(ZipEntry(entryName))
        zip.write(content)
        zip.closeEntry()
      }
    }
    return file
  }
}

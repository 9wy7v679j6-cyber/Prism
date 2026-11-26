package com.prism.docs.feature.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageAnalyzerArcGeometryTest {

  @Test
  fun tinyCategoriesReceiveMinimumVisibleArcWithoutOverlap() {
    val segments =
      listOf(
        StorageRingSegment(StorageRingBucket.OTHER, itemCount = 9_500, totalBytes = 67_000_000_000L, fraction = 0.960f),
        StorageRingSegment(StorageRingBucket.PDF, itemCount = 841, totalBytes = 2_180_000_000L, fraction = 0.031f),
        StorageRingSegment(StorageRingBucket.WORD, itemCount = 92, totalBytes = 26_500_000L, fraction = 0.0004f),
        StorageRingSegment(StorageRingBucket.EXCEL, itemCount = 12, totalBytes = 464_000L, fraction = 0.00001f),
        StorageRingSegment(StorageRingBucket.POWERPOINT, itemCount = 5, totalBytes = 120_000L, fraction = 0.000002f),
      )

    val gapDeg = 3.5f
    val minSweepDeg = 6f
    val slices = computeNormalizedArcSlices(segments, gapDeg = gapDeg, minSweepDeg = minSweepDeg)

    assertEquals(segments.size, slices.size)

    // Every slice meets the minimum legible arc floor
    slices.forEach { slice ->
      assertTrue(
        "Slice ${slice.segment.bucket} sweep=${slice.sweepAngleDeg} below floor $minSweepDeg",
        slice.sweepAngleDeg >= minSweepDeg - 0.01f,
      )
    }

    // Total sweep + gaps equals full 360-degree circle
    val totalSweep = slices.sumOf { it.sweepAngleDeg.toDouble() }.toFloat()
    val expectedTotalSweep = 360f - gapDeg * segments.size
    assertEquals(expectedTotalSweep, totalSweep, 0.05f)

    // Adjacent slices never overlap and preserve the exact gapDeg separation
    for (i in 0 until slices.lastIndex) {
      val currentEnd = slices[i].startAngleDeg + slices[i].sweepAngleDeg
      val nextStart = slices[i + 1].startAngleDeg
      assertEquals(gapDeg, nextStart - currentEnd, 0.05f)
    }
  }

  @Test
  fun singleSegmentFillsFullCircle() {
    val single =
      listOf(
        StorageRingSegment(StorageRingBucket.PDF, itemCount = 10, totalBytes = 10_000L, fraction = 1f)
      )
    val slices = computeNormalizedArcSlices(single)
    assertEquals(1, slices.size)
    assertEquals(-90f, slices.first().startAngleDeg, 0.001f)
    assertEquals(360f, slices.first().sweepAngleDeg, 0.001f)
  }
}

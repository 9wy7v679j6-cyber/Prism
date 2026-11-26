package com.prism.docs.feature.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DateFormattingAndBucketSafetyTest {

  @Test
  fun extremeAndCorruptTimestampsNeverThrowDateTimeException() {
    val extremeValues =
      listOf(
        Long.MIN_VALUE,
        -1_000_000_000L,
        -1L,
        0L,
        1L,
        1_700_000_000_000L,
        7_258_118_400_000L,
        7_258_118_400_001L,
        Long.MAX_VALUE / 2,
        Long.MAX_VALUE,
      )

    for (ts in extremeValues) {
      val rel = formatRelativeDay(ts)
      val mod = formatModifiedTimestamp(ts)
      assertTrue("formatRelativeDay blank for $ts", rel.isNotBlank())
      assertTrue("formatModifiedTimestamp blank for $ts", mod.isNotBlank())

      val boundaries = DateBucketBoundaries.current(ts)
      assertTrue(boundaries.todayStartMs >= boundaries.yesterdayStartMs)
      assertTrue(boundaries.yesterdayStartMs >= boundaries.thisWeekStartMs)
    }

    assertEquals("unknown date", formatRelativeDay(Long.MAX_VALUE))
    assertEquals("Unknown date", formatModifiedTimestamp(Long.MAX_VALUE))
    assertEquals("unknown date", formatRelativeDay(Long.MIN_VALUE))
    assertEquals("Unknown date", formatModifiedTimestamp(Long.MIN_VALUE))
    assertEquals("unknown date", formatRelativeDay(0L))
    assertEquals("Unknown date", formatModifiedTimestamp(0L))
  }

  @Test
  fun resolveDateBucketHandlesNonPositiveAndValidDates() {
    val nowMs = 1_764_000_000_000L
    val boundaries = DateBucketBoundaries.current(nowMs)

    assertEquals(DateBucket.EARLIER, resolveDateBucket(0L, boundaries))
    assertEquals(DateBucket.EARLIER, resolveDateBucket(-5000L, boundaries))
    assertEquals(DateBucket.EARLIER, resolveDateBucket(Long.MIN_VALUE, boundaries))
    assertEquals(DateBucket.TODAY, resolveDateBucket(boundaries.todayStartMs, boundaries))
    assertEquals(DateBucket.YESTERDAY, resolveDateBucket(boundaries.yesterdayStartMs, boundaries))
    assertEquals(DateBucket.THIS_WEEK, resolveDateBucket(boundaries.thisWeekStartMs, boundaries))
    assertEquals(DateBucket.EARLIER, resolveDateBucket(boundaries.thisWeekStartMs - 1L, boundaries))
  }
}

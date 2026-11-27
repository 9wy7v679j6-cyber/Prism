package com.prism.docs.core.data.viewer

import com.prism.docs.core.data.db.DocumentBookmarkEntity
import com.prism.docs.core.data.db.DocumentReadingStateEntity
import com.prism.docs.core.data.db.ViewerStateDao
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class ViewerStateRepository
@Inject
constructor(
  private val viewerStateDao: ViewerStateDao,
) {
  fun observeReadingState(uriString: String): Flow<DocumentReadingStateEntity?> =
    viewerStateDao.observeReadingState(uriString)

  suspend fun getReadingState(uriString: String): DocumentReadingStateEntity? =
    viewerStateDao.getReadingState(uriString)

  suspend fun saveReadingState(
    uriString: String,
    lastPage: Int,
    zoomScale: Float,
    scrollOffsetFraction: Float = 0f,
    scrollMode: String = "CONTINUOUS",
    paperMode: String = "LIGHT",
  ) {
    viewerStateDao.upsertReadingState(
      DocumentReadingStateEntity(
        uriString = uriString,
        lastPage = lastPage.coerceAtLeast(0),
        zoomScale = zoomScale.coerceIn(1f, 5f),
        scrollOffsetFraction = scrollOffsetFraction.coerceIn(0f, 1f),
        scrollMode = scrollMode,
        paperMode = paperMode,
        updatedAtEpochMs = System.currentTimeMillis(),
      )
    )
  }

  fun observeBookmarks(uriString: String): Flow<List<DocumentBookmarkEntity>> =
    viewerStateDao.observeBookmarks(uriString)

  suspend fun getBookmarks(uriString: String): List<DocumentBookmarkEntity> =
    viewerStateDao.getBookmarks(uriString)

  suspend fun toggleBookmark(uriString: String, pageIndex: Int, defaultLabel: String): Boolean {
    val existing = viewerStateDao.getBookmarks(uriString).firstOrNull { it.pageIndex == pageIndex }
    return if (existing != null) {
      viewerStateDao.deleteBookmarkByPage(uriString, pageIndex)
      false
    } else {
      viewerStateDao.upsertBookmark(
        DocumentBookmarkEntity(
          uriString = uriString,
          pageIndex = pageIndex,
          label = defaultLabel,
          createdAtEpochMs = System.currentTimeMillis(),
        )
      )
      true
    }
  }

  suspend fun removeBookmarkById(id: Long) {
    viewerStateDao.deleteBookmarkById(id)
  }
}

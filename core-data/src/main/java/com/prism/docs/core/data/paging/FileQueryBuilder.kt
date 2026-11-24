package com.prism.docs.core.data.paging

import androidx.sqlite.db.SimpleSQLiteQuery
import com.prism.docs.core.data.model.FileListQuery
import com.prism.docs.core.data.model.FileSortField
import com.prism.docs.core.data.model.SortDirection

/**
 * Builds strictly parameterized [SimpleSQLiteQuery] instances for Room Paging 3 file queries.
 * All dynamic filter values are bound via SQLite `?` arguments and `ORDER BY` clauses are
 * mapped exclusively from closed Kotlin enums to prevent SQL injection.
 */
object FileQueryBuilder {

  fun build(query: FileListQuery): SimpleSQLiteQuery {
    val sql = StringBuilder("SELECT d.* FROM indexed_documents d")
    val whereClauses = mutableListOf("d.isTrashed = 0")
    val args = mutableListOf<Any>()

    if (query.collectionId != null) {
      sql.append(" INNER JOIN document_collection_cross_ref cr ON d.id = cr.documentId")
      whereClauses += "cr.collectionId = ?"
      args += query.collectionId
    }

    if (query.tagId != null) {
      sql.append(" INNER JOIN document_tag_cross_ref tr ON d.id = tr.documentId")
      whereClauses += "tr.tagId = ?"
      args += query.tagId
    }

    if (query.category != null) {
      whereClauses += "d.category = ?"
      args += query.category.name
    } else if (query.otherCategoriesOnly) {
      whereClauses += "d.category NOT IN ('PDF', 'DOCUMENTS', 'SPREADSHEETS', 'SLIDES', 'IMAGES')"
    }

    if (!query.parentPath.isNullOrBlank()) {
      whereClauses += "d.parentPath = ?"
      args += query.parentPath
    }

    if (query.favoritesOnly) {
      whereClauses += "d.isFavorite = 1"
    }

    if (query.recentsOnly) {
      whereClauses += "d.lastOpenedEpochMs IS NOT NULL"
    }

    if (!query.namePrefix.isNullOrBlank()) {
      whereClauses += "d.normalizedName LIKE ? ESCAPE '\\'"
      val escaped =
        query.namePrefix.trim().lowercase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
      args += "$escaped%"
    }

    if (query.minSizeBytes != null) {
      whereClauses += "d.sizeBytes >= ?"
      args += query.minSizeBytes
    }

    if (query.maxSizeBytes != null) {
      whereClauses += "d.sizeBytes <= ?"
      args += query.maxSizeBytes
    }

    if (query.modifiedAfterEpochMs != null) {
      whereClauses += "d.lastModifiedEpochMs >= ?"
      args += query.modifiedAfterEpochMs
    }

    sql.append(" WHERE ").append(whereClauses.joinToString(" AND "))

    val dir =
      when (query.sortDirection) {
        SortDirection.ASC -> "ASC"
        SortDirection.DESC -> "DESC"
      }

    val orderBy =
      if (query.recentsOnly && query.sortField == FileSortField.DATE_MODIFIED) {
        "d.lastOpenedEpochMs $dir, d.normalizedName ASC"
      } else {
        when (query.sortField) {
          FileSortField.NAME -> "d.normalizedName $dir, d.id ASC"
          FileSortField.DATE_MODIFIED -> "d.lastModifiedEpochMs $dir, d.normalizedName ASC"
          FileSortField.SIZE -> "d.sizeBytes $dir, d.normalizedName ASC"
          FileSortField.TYPE -> "d.category $dir, d.extension $dir, d.normalizedName ASC"
        }
      }

    sql.append(" ORDER BY ").append(orderBy)

    return SimpleSQLiteQuery(sql.toString(), args.toTypedArray())
  }
}

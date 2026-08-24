package com.example.markdownviewer.data

import android.content.Context
import androidx.core.content.edit
import com.example.markdownviewer.model.FolderReference
import org.json.JSONArray
import org.json.JSONObject

class FolderPreferences(context: Context) {
  private val preferences =
    context.getSharedPreferences("markdown_viewer_folders", Context.MODE_PRIVATE)

  fun recentFolders(): List<FolderReference> = readFolders(KEY_RECENT)

  fun bookmarks(): List<FolderReference> = readFolders(KEY_BOOKMARKS)

  fun addRecent(folder: FolderReference): List<FolderReference> {
    val next =
      listOf(folder) + recentFolders().filterNot { it.uri == folder.uri }
    return next.take(MAX_RECENT).also { writeFolders(KEY_RECENT, it) }
  }

  fun toggleBookmark(folder: FolderReference): List<FolderReference> {
    val current = bookmarks()
    val next =
      if (current.any { it.uri == folder.uri }) {
        current.filterNot { it.uri == folder.uri }
      } else {
        current + folder
      }
    writeFolders(KEY_BOOKMARKS, next)
    return next
  }

  fun removeFolder(uri: String) {
    writeFolders(KEY_RECENT, recentFolders().filterNot { it.uri == uri })
    writeFolders(KEY_BOOKMARKS, bookmarks().filterNot { it.uri == uri })
  }

  fun contentWidth(): Int = preferences.getInt(KEY_CONTENT_WIDTH, DEFAULT_CONTENT_WIDTH)

  fun setContentWidth(width: Int) {
    preferences.edit { putInt(KEY_CONTENT_WIDTH, width) }
  }

  private fun readFolders(key: String): List<FolderReference> {
    val raw = preferences.getString(key, null) ?: return emptyList()
    return runCatching {
        val array = JSONArray(raw)
        buildList {
          for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val uri = item.optString("uri")
            val name = item.optString("name")
            if (uri.isNotBlank()) add(FolderReference(uri = uri, name = name.ifBlank { uri }))
          }
        }
      }
      .getOrDefault(emptyList())
  }

  private fun writeFolders(key: String, folders: List<FolderReference>) {
    val array = JSONArray()
    folders.forEach { folder ->
      array.put(JSONObject().put("uri", folder.uri).put("name", folder.name))
    }
    preferences.edit { putString(key, array.toString()) }
  }

  private companion object {
    const val KEY_RECENT = "recent"
    const val KEY_BOOKMARKS = "bookmarks"
    const val KEY_CONTENT_WIDTH = "content_width"
    const val MAX_RECENT = 5
    const val DEFAULT_CONTENT_WIDTH = 900
  }
}

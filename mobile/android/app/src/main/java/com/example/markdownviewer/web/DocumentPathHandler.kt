package com.example.markdownviewer.web

import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import java.io.InputStream

class DocumentPathHandler(
  private val isAllowed: (String) -> Boolean,
  private val openStream: (String) -> InputStream?,
  private val mimeType: (String) -> String,
) : WebViewAssetLoader.PathHandler {
  override fun handle(path: String): WebResourceResponse? {
    val uri = DocumentUrl.decodeToken(path) ?: return null
    if (!isAllowed(uri)) return null
    val stream = runCatching { openStream(uri) }.getOrNull() ?: return null
    val mime = runCatching { mimeType(uri) }.getOrDefault("application/octet-stream")
    val encoding = if (mime.startsWith("text/") || mime == "image/svg+xml") "utf-8" else null
    return WebResourceResponse(mime, encoding, stream)
  }
}

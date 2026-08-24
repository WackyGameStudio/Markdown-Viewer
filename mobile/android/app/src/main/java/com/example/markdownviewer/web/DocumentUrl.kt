package com.example.markdownviewer.web

import android.util.Base64

object DocumentUrl {
  const val ORIGIN = "https://appassets.androidplatform.net"
  const val VIEWER_URL = "$ORIGIN/assets/viewer/index.html"

  fun fromUri(uri: String): String {
    return "$ORIGIN/document/${tokenFor(uri)}"
  }

  fun tokenFor(uri: String): String =
    Base64.encodeToString(
      uri.toByteArray(Charsets.UTF_8),
      Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
    )

  fun decodeToken(token: String): String? =
    runCatching {
        val bytes =
          Base64.decode(token.substringBefore('/'), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        bytes.toString(Charsets.UTF_8)
      }
      .getOrNull()
}

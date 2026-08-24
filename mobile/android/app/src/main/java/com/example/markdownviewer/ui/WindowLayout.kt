package com.example.markdownviewer.ui

enum class WindowLayout {
  Compact,
  Medium,
  Expanded,
  Large,
}

fun classifyWindow(widthDp: Int, heightDp: Int): WindowLayout =
  when {
    widthDp < 600 || heightDp < 480 -> WindowLayout.Compact
    widthDp < 840 -> WindowLayout.Medium
    widthDp < 1200 -> WindowLayout.Expanded
    else -> WindowLayout.Large
  }

val WindowLayout.paneCount: Int
  get() =
    when (this) {
      WindowLayout.Compact -> 1
      WindowLayout.Medium, WindowLayout.Expanded -> 2
      WindowLayout.Large -> 3
    }

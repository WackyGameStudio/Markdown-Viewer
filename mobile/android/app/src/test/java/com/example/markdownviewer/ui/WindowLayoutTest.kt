package com.example.markdownviewer.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class WindowLayoutTest {
  @Test
  fun compactForPhoneAndShortSplitWindow() {
    assertEquals(WindowLayout.Compact, classifyWindow(widthDp = 412, heightDp = 915))
    assertEquals(WindowLayout.Compact, classifyWindow(widthDp = 900, heightDp = 420))
  }

  @Test
  fun mediumForFoldableOrSmallTabletWindow() {
    assertEquals(WindowLayout.Medium, classifyWindow(widthDp = 700, heightDp = 900))
  }

  @Test
  fun expandedForTabletWindow() {
    assertEquals(WindowLayout.Expanded, classifyWindow(widthDp = 1000, heightDp = 700))
  }

  @Test
  fun largeForWideTabletOrDesktopWindow() {
    assertEquals(WindowLayout.Large, classifyWindow(widthDp = 1280, heightDp = 800))
  }
}

package com.example.markdownviewer.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoPlayerStateTest {
  @Test
  fun restoresOnlyValidNonNegativeVideoPositions() {
    assertEquals(12_345L, videoPositionFromState("""{"kind":"video","positionMs":12345}"""))
    assertEquals(0L, videoPositionFromState("""{"kind":"video","positionMs":-20}"""))
    assertEquals(0L, videoPositionFromState("""{"kind":"pdf","positionMs":12345}"""))
    assertEquals(0L, videoPositionFromState("not-json"))
    assertEquals(0L, videoPositionFromState(null))
  }
}

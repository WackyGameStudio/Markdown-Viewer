package com.example.markdownviewer.ui

import androidx.compose.ui.geometry.Offset
import com.example.markdownviewer.data.ViewerSettings
import com.example.markdownviewer.model.GestureTrigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NativeDocumentGesturesTest {
  private val settings = ViewerSettings()

  @Test
  fun mapsInwardSwipesFromLeftRightAndTopEdges() {
    assertEquals(
      GestureTrigger.EdgeLeftIn,
      classifyNativeEdgeSwipe(800f, Offset(8f, 500f), Offset(120f, 505f), 32f, 72f, settings, true),
    )
    assertEquals(
      GestureTrigger.EdgeRightIn,
      classifyNativeEdgeSwipe(800f, Offset(792f, 500f), Offset(680f, 495f), 32f, 72f, settings, true),
    )
    assertEquals(
      GestureTrigger.EdgeTopIn,
      classifyNativeEdgeSwipe(800f, Offset(400f, 8f), Offset(405f, 110f), 32f, 72f, settings, true),
    )
  }

  @Test
  fun rejectsDisabledShortAndDiagonalSwipes() {
    assertNull(
      classifyNativeEdgeSwipe(800f, Offset(8f, 500f), Offset(120f, 500f), 32f, 72f, settings, false)
    )
    assertNull(
      classifyNativeEdgeSwipe(800f, Offset(8f, 500f), Offset(60f, 500f), 32f, 72f, settings, true)
    )
    assertNull(
      classifyNativeEdgeSwipe(800f, Offset(8f, 500f), Offset(120f, 620f), 32f, 72f, settings, true)
    )
  }
}

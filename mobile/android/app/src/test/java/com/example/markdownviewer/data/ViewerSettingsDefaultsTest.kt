package com.example.markdownviewer.data

import com.example.markdownviewer.model.GestureTrigger
import com.example.markdownviewer.model.ViewerAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewerSettingsDefaultsTest {
  @Test
  fun usesDocumentFriendlyGestureDefaults() {
    val settings = ViewerSettings()

    assertEquals(GestureTrigger.TripleTap, settings.gestureBindings[ViewerAction.ToggleFocus])
    assertEquals(GestureTrigger.EdgeLeftIn, settings.gestureBindings[ViewerAction.ToggleExplorer])
    assertEquals(GestureTrigger.EdgeRightIn, settings.gestureBindings[ViewerAction.ToggleDetails])
    assertEquals(GestureTrigger.EdgeTopIn, settings.gestureBindings[ViewerAction.ToggleControls])
    assertEquals(GestureTrigger.None, settings.gestureBindings[ViewerAction.OpenExternalApp])
    assertTrue(settings.edgeGesturesFocusOnly)
    assertTrue(settings.pinchZoomImage)
    assertTrue(settings.pinchZoomPdf)
    assertTrue(settings.pinchZoomOffice)
    assertTrue(settings.pinchZoomHtml)
  }
}

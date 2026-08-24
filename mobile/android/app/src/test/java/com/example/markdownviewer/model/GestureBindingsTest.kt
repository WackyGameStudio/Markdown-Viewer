package com.example.markdownviewer.model

import org.junit.Assert.assertEquals
import org.junit.Test

class GestureBindingsTest {
  @Test
  fun rebindClearsThePreviousOwnerAtomically() {
    val rebound =
      GestureBindings.Default.rebind(
        action = ViewerAction.OpenExternalApp,
        trigger = GestureTrigger.EdgeLeftIn,
      )

    assertEquals(GestureTrigger.EdgeLeftIn, rebound[ViewerAction.OpenExternalApp])
    assertEquals(GestureTrigger.None, rebound[ViewerAction.ToggleExplorer])
    assertEquals(ViewerAction.OpenExternalApp, rebound.actionFor(GestureTrigger.EdgeLeftIn))
  }

  @Test
  fun assigningNoneDoesNotAffectAnotherAction() {
    val rebound =
      GestureBindings.Default.rebind(
        action = ViewerAction.ToggleFocus,
        trigger = GestureTrigger.None,
      )

    assertEquals(GestureTrigger.None, rebound[ViewerAction.ToggleFocus])
    assertEquals(GestureTrigger.EdgeLeftIn, rebound[ViewerAction.ToggleExplorer])
  }
}

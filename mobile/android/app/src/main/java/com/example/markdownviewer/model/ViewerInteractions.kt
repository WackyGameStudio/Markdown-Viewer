package com.example.markdownviewer.model

enum class GestureTrigger(val storageValue: String) {
  None("none"),
  TripleTap("triple_tap"),
  ThreeFingerTap("three_finger_tap"),
  EdgeLeftIn("edge_left_in"),
  EdgeRightIn("edge_right_in"),
  EdgeTopIn("edge_top_in"),
  ;

  companion object {
    fun fromStorageValue(value: String?): GestureTrigger? =
      entries.firstOrNull { it.storageValue == value }
  }
}

enum class ViewerAction {
  ToggleFocus,
  ToggleExplorer,
  ToggleDetails,
  ToggleControls,
  OpenExternalApp,
}

data class GestureBindings(
  val toggleFocus: GestureTrigger = GestureTrigger.TripleTap,
  val toggleExplorer: GestureTrigger = GestureTrigger.EdgeLeftIn,
  val toggleDetails: GestureTrigger = GestureTrigger.EdgeRightIn,
  val toggleControls: GestureTrigger = GestureTrigger.EdgeTopIn,
  val openExternalApp: GestureTrigger = GestureTrigger.None,
) {
  operator fun get(action: ViewerAction): GestureTrigger =
    when (action) {
      ViewerAction.ToggleFocus -> toggleFocus
      ViewerAction.ToggleExplorer -> toggleExplorer
      ViewerAction.ToggleDetails -> toggleDetails
      ViewerAction.ToggleControls -> toggleControls
      ViewerAction.OpenExternalApp -> openExternalApp
    }

  fun actionFor(trigger: GestureTrigger): ViewerAction? =
    if (trigger == GestureTrigger.None) {
      null
    } else {
      ViewerAction.entries.firstOrNull { this[it] == trigger }
    }

  fun isBound(trigger: GestureTrigger): Boolean = actionFor(trigger) != null

  fun rebind(action: ViewerAction, trigger: GestureTrigger): GestureBindings {
    var next = this
    val conflictingAction = next.actionFor(trigger)
    if (conflictingAction != null && conflictingAction != action) {
      next = next.withBinding(conflictingAction, GestureTrigger.None)
    }
    return next.withBinding(action, trigger)
  }

  private fun withBinding(action: ViewerAction, trigger: GestureTrigger): GestureBindings =
    when (action) {
      ViewerAction.ToggleFocus -> copy(toggleFocus = trigger)
      ViewerAction.ToggleExplorer -> copy(toggleExplorer = trigger)
      ViewerAction.ToggleDetails -> copy(toggleDetails = trigger)
      ViewerAction.ToggleControls -> copy(toggleControls = trigger)
      ViewerAction.OpenExternalApp -> copy(openExternalApp = trigger)
    }

  companion object {
    val Default = GestureBindings()

    fun normalized(bindings: GestureBindings): GestureBindings {
      var result = GestureBindings(
        toggleFocus = GestureTrigger.None,
        toggleExplorer = GestureTrigger.None,
        toggleDetails = GestureTrigger.None,
        toggleControls = GestureTrigger.None,
        openExternalApp = GestureTrigger.None,
      )
      ViewerAction.entries.forEach { action -> result = result.rebind(action, bindings[action]) }
      return result
    }
  }
}

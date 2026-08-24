package com.example.markdownviewer.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.markdownviewer.data.ViewerSettings
import com.example.markdownviewer.model.GestureTrigger

private data class PointerTrack(
  val start: Offset,
  var last: Offset,
  val startedAt: Long,
)

fun Modifier.nativeDocumentGestures(
  settings: ViewerSettings,
  focusMode: Boolean,
  onTrigger: (GestureTrigger) -> Unit,
): Modifier =
  pointerInput(settings, focusMode) {
    val active = mutableMapOf<Long, PointerTrack>()
    var maxPointerCount = 0
    var groupStartedAt = 0L
    var groupMoved = false
    var tapCount = 0
    var lastTapAt = 0L
    var lastTap = Offset.Zero
    val edgeWidth = 32.dp.toPx()
    val swipeThreshold = 72.dp.toPx()
    val tapMovement = 28.dp.toPx()
    val tapRadius = 48.dp.toPx()

    fun resetGroup() {
      active.clear()
      maxPointerCount = 0
      groupStartedAt = 0L
      groupMoved = false
    }

    awaitPointerEventScope {
      while (true) {
        val event = awaitPointerEvent(PointerEventPass.Final)
        event.changes.forEach { change ->
          when {
            change.changedToDownIgnoreConsumed() -> {
              if (active.isEmpty()) {
                groupStartedAt = change.uptimeMillis
                groupMoved = false
                maxPointerCount = 0
              }
              active[change.id.value] =
                PointerTrack(change.position, change.position, change.uptimeMillis)
              maxPointerCount = maxOf(maxPointerCount, active.size)
              if (active.size > 3) groupMoved = true
            }
            change.changedToUpIgnoreConsumed() -> {
              val pointer = active[change.id.value] ?: return@forEach
              pointer.last = change.position
              val countBeforeRelease = active.size
              if (maxPointerCount == 1 && countBeforeRelease == 1) {
                val edgeAction =
                  classifyNativeEdgeSwipe(
                    width = size.width.toFloat(),
                    start = pointer.start,
                    end = pointer.last,
                    edgeWidth = edgeWidth,
                    threshold = swipeThreshold,
                    settings = settings,
                    enabled = !settings.edgeGesturesFocusOnly || focusMode,
                  )
                if (edgeAction != null) {
                  tapCount = 0
                  onTrigger(edgeAction)
                } else if (
                  !groupMoved &&
                    settings.gestureBindings.isBound(GestureTrigger.TripleTap) &&
                    change.uptimeMillis - pointer.startedAt <= 320
                ) {
                  val continues =
                    change.uptimeMillis - lastTapAt <= 460 &&
                      (pointer.last - lastTap).getDistance() <= tapRadius
                  tapCount = if (continues) tapCount + 1 else 1
                  lastTapAt = change.uptimeMillis
                  lastTap = pointer.last
                  if (tapCount == 3) {
                    tapCount = 0
                    onTrigger(GestureTrigger.TripleTap)
                  }
                }
              }
              active.remove(change.id.value)
              if (active.isEmpty()) {
                if (
                  settings.gestureBindings.isBound(GestureTrigger.ThreeFingerTap) &&
                    maxPointerCount == 3 &&
                    !groupMoved &&
                    change.uptimeMillis - groupStartedAt <= 650
                ) {
                  tapCount = 0
                  onTrigger(GestureTrigger.ThreeFingerTap)
                }
                resetGroup()
              }
            }
            change.pressed -> {
              val pointer = active[change.id.value] ?: return@forEach
              pointer.last = change.position
              if ((pointer.last - pointer.start).getDistance() > tapMovement) groupMoved = true
            }
          }
        }
      }
    }
  }

internal fun classifyNativeEdgeSwipe(
  width: Float,
  start: Offset,
  end: Offset,
  edgeWidth: Float,
  threshold: Float,
  settings: ViewerSettings,
  enabled: Boolean,
): GestureTrigger? {
  if (!enabled) return null
  val dx = end.x - start.x
  val dy = end.y - start.y
  if (
    settings.gestureBindings.isBound(GestureTrigger.EdgeLeftIn) &&
      start.x <= edgeWidth &&
      dx >= threshold &&
      kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.25f
  ) {
    return GestureTrigger.EdgeLeftIn
  }
  if (
    settings.gestureBindings.isBound(GestureTrigger.EdgeRightIn) &&
      start.x >= width - edgeWidth &&
      dx <= -threshold &&
      kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.25f
  ) {
    return GestureTrigger.EdgeRightIn
  }
  if (
    settings.gestureBindings.isBound(GestureTrigger.EdgeTopIn) &&
      start.y <= edgeWidth &&
      dy >= threshold &&
      kotlin.math.abs(dy) > kotlin.math.abs(dx) * 1.25f
  ) {
    return GestureTrigger.EdgeTopIn
  }
  return null
}

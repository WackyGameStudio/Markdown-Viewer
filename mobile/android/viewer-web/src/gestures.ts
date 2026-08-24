import { type RefObject, useEffect, useRef } from 'react';
import {
  requestTrigger,
  type ViewerGestureSettings,
  type ViewerGestureTrigger,
} from './bridge';

type Point = { x: number; y: number };

export function classifyEdgeSwipe(
  viewport: { width: number; height: number },
  start: Point,
  end: Point,
  settings: Pick<ViewerGestureSettings, 'edgeLeft' | 'edgeRight' | 'edgeTop'>,
): ViewerGestureTrigger | null {
  const edge = 32;
  const threshold = 72;
  const dx = end.x - start.x;
  const dy = end.y - start.y;

  if (settings.edgeLeft && start.x <= edge && dx >= threshold && Math.abs(dx) > Math.abs(dy) * 1.25) {
    return 'edge-left-in';
  }
  if (
    settings.edgeRight &&
    start.x >= viewport.width - edge &&
    dx <= -threshold &&
    Math.abs(dx) > Math.abs(dy) * 1.25
  ) {
    return 'edge-right-in';
  }
  if (settings.edgeTop && start.y <= edge && dy >= threshold && Math.abs(dy) > Math.abs(dx) * 1.25) {
    return 'edge-top-in';
  }
  return null;
}

export function useViewerGestures(
  rootRef: RefObject<HTMLElement | null>,
  settings: ViewerGestureSettings,
): void {
  useEffect(() => {
    const root = rootRef.current;
    if (!root) return undefined;

    const active = new Map<number, { start: Point; last: Point; startedAt: number }>();
    let maxPointerCount = 0;
    let groupStartedAt = 0;
    let groupMoved = false;
    let tapCount = 0;
    let lastTapAt = 0;
    let lastTap = { x: 0, y: 0 };
    let edgeTouch: { start: Point; triggered: boolean } | null = null;

    const resetGroup = () => {
      active.clear();
      maxPointerCount = 0;
      groupStartedAt = 0;
      groupMoved = false;
    };

    const onPointerDown = (event: PointerEvent) => {
      if (event.pointerType !== 'touch') return;
      const now = performance.now();
      if (active.size === 0) {
        groupStartedAt = now;
        groupMoved = false;
        maxPointerCount = 0;
      }
      const point = { x: event.clientX, y: event.clientY };
      active.set(event.pointerId, { start: point, last: point, startedAt: now });
      maxPointerCount = Math.max(maxPointerCount, active.size);
      if (active.size > 3) groupMoved = true;
    };

    const onPointerMove = (event: PointerEvent) => {
      const pointer = active.get(event.pointerId);
      if (!pointer) return;
      pointer.last = { x: event.clientX, y: event.clientY };
      if (distance(pointer.start, pointer.last) > 28) groupMoved = true;
    };

    const onPointerUp = (event: PointerEvent) => {
      const pointer = active.get(event.pointerId);
      if (!pointer) return;
      pointer.last = { x: event.clientX, y: event.clientY };
      const now = performance.now();
      const countBeforeRelease = active.size;

      if (maxPointerCount === 1 && countBeforeRelease === 1) {
        if (
          !groupMoved &&
          settings.tripleTap &&
          now - pointer.startedAt <= 320 &&
          !isInteractiveTarget(event.target)
        ) {
          const continues = now - lastTapAt <= 460 && distance(lastTap, pointer.last) <= 48;
          tapCount = continues ? tapCount + 1 : 1;
          lastTapAt = now;
          lastTap = pointer.last;
          if (tapCount === 3) {
            tapCount = 0;
            requestTrigger('triple-tap');
          }
        }
      }

      active.delete(event.pointerId);
      if (active.size === 0) {
        if (
          settings.threeFingerTap &&
          maxPointerCount === 3 &&
          !groupMoved &&
          now - groupStartedAt <= 650
        ) {
          tapCount = 0;
          requestTrigger('three-finger-tap');
        }
        resetGroup();
      }
    };

    const onPointerCancel = () => resetGroup();
    const onTouchStart = (event: TouchEvent) => {
      if (event.touches.length !== 1) {
        edgeTouch = null;
        return;
      }
      const touch = event.touches.item(0);
      if (!touch) return;
      const start = { x: touch.clientX, y: touch.clientY };
      const atEnabledEdge =
        (settings.edgeLeft && start.x <= 32) ||
        (settings.edgeRight && start.x >= root.clientWidth - 32) ||
        (settings.edgeTop && start.y <= 32);
      edgeTouch = atEnabledEdge ? { start, triggered: false } : null;
    };
    const onTouchMove = (event: TouchEvent) => {
      if (!edgeTouch || event.touches.length !== 1) {
        edgeTouch = null;
        return;
      }
      const touch = event.touches.item(0);
      if (!touch) return;
      event.preventDefault();
      if (edgeTouch.triggered) return;
      const action = classifyEdgeSwipe(
        { width: root.clientWidth, height: root.clientHeight },
        edgeTouch.start,
        { x: touch.clientX, y: touch.clientY },
        settings,
      );
      if (action) {
        edgeTouch.triggered = true;
        tapCount = 0;
        requestTrigger(action);
      }
    };
    const onTouchEnd = () => {
      edgeTouch = null;
    };
    root.addEventListener('pointerdown', onPointerDown);
    root.addEventListener('pointermove', onPointerMove);
    root.addEventListener('pointerup', onPointerUp);
    root.addEventListener('pointercancel', onPointerCancel);
    root.addEventListener('touchstart', onTouchStart, { passive: true });
    root.addEventListener('touchmove', onTouchMove, { passive: false });
    root.addEventListener('touchend', onTouchEnd, { passive: true });
    root.addEventListener('touchcancel', onTouchEnd, { passive: true });
    return () => {
      root.removeEventListener('pointerdown', onPointerDown);
      root.removeEventListener('pointermove', onPointerMove);
      root.removeEventListener('pointerup', onPointerUp);
      root.removeEventListener('pointercancel', onPointerCancel);
      root.removeEventListener('touchstart', onTouchStart);
      root.removeEventListener('touchmove', onTouchMove);
      root.removeEventListener('touchend', onTouchEnd);
      root.removeEventListener('touchcancel', onTouchEnd);
    };
  }, [rootRef, settings]);
}

export function usePinchZoom(
  targetRef: RefObject<HTMLElement | null>,
  enabled: boolean,
  onScale: (factor: number) => void,
): void {
  const callback = useRef(onScale);
  callback.current = onScale;

  useEffect(() => {
    const target = targetRef.current;
    if (!target || !enabled) return undefined;
    let lastDistance = 0;

    const onTouchStart = (event: TouchEvent) => {
      if (event.touches.length === 2) lastDistance = touchDistance(event.touches);
    };
    const onTouchMove = (event: TouchEvent) => {
      if (event.touches.length !== 2) return;
      const nextDistance = touchDistance(event.touches);
      if (lastDistance > 0 && nextDistance > 0) {
        event.preventDefault();
        callback.current(nextDistance / lastDistance);
      }
      lastDistance = nextDistance;
    };
    const onTouchEnd = () => {
      lastDistance = 0;
    };

    target.addEventListener('touchstart', onTouchStart, { passive: true });
    target.addEventListener('touchmove', onTouchMove, { passive: false });
    target.addEventListener('touchend', onTouchEnd, { passive: true });
    target.addEventListener('touchcancel', onTouchEnd, { passive: true });
    return () => {
      target.removeEventListener('touchstart', onTouchStart);
      target.removeEventListener('touchmove', onTouchMove);
      target.removeEventListener('touchend', onTouchEnd);
      target.removeEventListener('touchcancel', onTouchEnd);
    };
  }, [enabled, targetRef]);
}

function touchDistance(touches: TouchList): number {
  const first = touches.item(0);
  const second = touches.item(1);
  if (!first || !second) return 0;
  return Math.hypot(second.clientX - first.clientX, second.clientY - first.clientY);
}

function distance(first: Point, second: Point): number {
  return Math.hypot(second.x - first.x, second.y - first.y);
}

function isInteractiveTarget(target: EventTarget | null): boolean {
  return target instanceof Element &&
    target.closest('a, button, input, textarea, select, [role="button"], [contenteditable="true"]') !== null;
}

import { useSyncExternalStore } from 'react';
import type { ViewerLanguage } from './i18n';

export type ViewerKind = 'empty' | 'markdown' | 'image' | 'pdf' | 'word' | 'presentation';

export type ViewerGestureTrigger =
  | 'triple-tap'
  | 'three-finger-tap'
  | 'edge-left-in'
  | 'edge-right-in'
  | 'edge-top-in';

export interface ViewerGestureSettings {
  threeFingerTap: boolean;
  tripleTap: boolean;
  pinchZoom: boolean;
  edgeLeft: boolean;
  edgeRight: boolean;
  edgeTop: boolean;
}

export type ViewerViewState =
  | { kind: 'markdown'; scrollTop: number; fontScale?: number }
  | { kind: 'image'; zoom: number; rotation: number }
  | { kind: 'pdf'; page: number; zoom: number; rotation: number }
  | { kind: 'word'; scrollTop: number; zoom: number }
  | { kind: 'presentation'; slide: number; zoom: number };

export interface ViewerPayload {
  language: ViewerLanguage;
  kind: ViewerKind;
  name: string;
  activePath: string;
  content: string;
  resourceUrl: string;
  contentWidth: number;
  legacyOffice: boolean;
  dark: boolean;
  focusMode: boolean;
  viewerControlsVisible: boolean;
  gestures: ViewerGestureSettings;
  viewState: ViewerViewState | null;
}

interface AndroidBridge {
  ready(): void;
  resolveResource(activePath: string, reference: string): string;
  openDocument(activePath: string, reference: string): void;
  openExternal(url: string): void;
  saveViewState(activePath: string, serializedState: string): void;
  requestTrigger(trigger: ViewerGestureTrigger): void;
}

declare global {
  interface Window {
    AndroidBridge?: AndroidBridge;
    MarkdownViewer: {
      setDocument(payload: ViewerPayload): void;
      scrollToHeading(index: number): void;
    };
  }
}

const emptyPayload: ViewerPayload = {
  language: 'ko',
  kind: 'empty',
  name: '',
  activePath: '',
  content: '',
  resourceUrl: '',
  contentWidth: 900,
  legacyOffice: false,
  dark: false,
  focusMode: false,
  viewerControlsVisible: true,
  gestures: {
    threeFingerTap: false,
    tripleTap: false,
    pinchZoom: false,
    edgeLeft: false,
    edgeRight: false,
    edgeTop: false,
  },
  viewState: null,
};

let currentPayload = emptyPayload;
const listeners = new Set<(payload: ViewerPayload) => void>();

interface ViewTransitionHandle {
  finished: Promise<void>;
  skipTransition?: () => void;
}

interface ViewTransitionDocument {
  startViewTransition?: (update: () => void) => ViewTransitionHandle;
}

let activeViewTransition: ViewTransitionHandle | null = null;

function syncViewportHeight(): void {
  if (typeof document === 'undefined' || typeof window === 'undefined') return;
  document.documentElement.style.setProperty('--viewer-height', `${window.innerHeight}px`);
}

if (typeof window !== 'undefined' && typeof document !== 'undefined') {
  syncViewportHeight();
  window.addEventListener('resize', syncViewportHeight);

  window.MarkdownViewer = {
    setDocument(payload) {
      syncViewportHeight();
      const nextPayload = { ...emptyPayload, ...payload };
      let committed = false;
      const commit = () => {
        if (committed) return;
        committed = true;
        currentPayload = nextPayload;
        document.documentElement.dataset.theme = currentPayload.dark ? 'dark' : 'light';
        listeners.forEach((listener) => listener(currentPayload));
      };
      const transitionDocument = document as unknown as ViewTransitionDocument;
      const shouldAnimate =
        currentPayload.activePath.length > 0 &&
        nextPayload.activePath.length > 0 &&
        currentPayload.activePath !== nextPayload.activePath &&
        typeof transitionDocument.startViewTransition === 'function';
      if (!shouldAnimate) {
        commit();
        return;
      }
      activeViewTransition?.skipTransition?.();
      try {
        const transition = transitionDocument.startViewTransition!.call(document, commit);
        activeViewTransition = transition;
        void transition.finished
          .catch(() => undefined)
          .finally(() => {
            if (activeViewTransition === transition) activeViewTransition = null;
          });
      } catch {
        commit();
      }
    },
    scrollToHeading(index) {
      const headings = document.querySelectorAll(
        '.markdown-body h1, .markdown-body h2, .markdown-body h3, .markdown-body h4, .markdown-body h5, .markdown-body h6',
      );
      headings.item(index)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    },
  };
}

export function useViewerPayload(): ViewerPayload {
  return useSyncExternalStore(
    (listener) => {
      listeners.add(listener);
      return () => {
        listeners.delete(listener);
      };
    },
    () => currentPayload,
    () => currentPayload,
  );
}

export function resolveResource(activePath: string, reference: string): string {
  return typeof window !== 'undefined' ? (window.AndroidBridge?.resolveResource(activePath, reference) ?? '') : '';
}

export function openDocument(activePath: string, reference: string): void {
  if (typeof window !== 'undefined') window.AndroidBridge?.openDocument(activePath, reference);
}

export function openExternal(url: string): void {
  if (typeof window !== 'undefined') window.AndroidBridge?.openExternal(url);
}

export function saveViewState(activePath: string, viewState: ViewerViewState): void {
  if (typeof window !== 'undefined') {
    window.AndroidBridge?.saveViewState(activePath, JSON.stringify(viewState));
  }
}

export function requestTrigger(trigger: ViewerGestureTrigger): void {
  if (typeof window !== 'undefined') window.AndroidBridge?.requestTrigger(trigger);
}

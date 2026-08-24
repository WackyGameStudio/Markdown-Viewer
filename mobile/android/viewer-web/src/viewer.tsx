import {
  createContext,
  type CSSProperties,
  type FormEvent,
  type MouseEvent,
  type ReactNode,
  useContext,
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import mermaid from 'mermaid';
import ReactMarkdown, { defaultUrlTransform } from 'react-markdown';
import { Document, Page, pdfjs } from 'react-pdf';
import rehypeHighlight from 'rehype-highlight';
import rehypeSlug from 'rehype-slug';
import remarkGfm from 'remark-gfm';
import {
  openDocument,
  openExternal,
  resolveResource,
  saveViewState,
  useViewerPayload,
  type ViewerPayload,
} from './bridge';
import {
  documentKindFromReference,
  isDirectImageSource,
  isExternalReference,
} from './references';
import { usePinchZoom, useViewerGestures } from './gestures';
import { viewerStrings, type ViewerLanguage, type ViewerStrings } from './i18n';

pdfjs.GlobalWorkerOptions.workerSrc = new URL(
  'pdfjs-dist/build/pdf.worker.min.mjs',
  import.meta.url,
).toString();

const assetBaseUrl = new URL('./', window.location.href);
const pdfOptions = {
  cMapUrl: new URL('cmaps/', assetBaseUrl).toString(),
  cMapPacked: true,
  wasmUrl: new URL('wasm/', assetBaseUrl).toString(),
  standardFontDataUrl: new URL('standard_fonts/', assetBaseUrl).toString(),
};

type PdfViewState = { page: number; zoom: number; rotation: number };
const pdfViewStates = new Map<string, PdfViewState>();
const MarkdownLinkContext = createContext(false);
const ViewerLanguageContext = createContext<ViewerLanguage>('ko');

function useViewerStrings(): ViewerStrings {
  return viewerStrings[useContext(ViewerLanguageContext)];
}

export function Viewer() {
  const payload = useViewerPayload();
  const strings = viewerStrings[payload.language];
  const rootRef = useRef<HTMLElement>(null);
  useViewerGestures(rootRef, payload.gestures);
  const style = { '--content-width': `${payload.contentWidth}px` } as CSSProperties;

  return (
    <ViewerLanguageContext.Provider value={payload.language}>
      <main className={payload.focusMode ? 'viewer-root focus-mode' : 'viewer-root'} ref={rootRef} style={style}>
        {payload.kind === 'empty' && <Status message={strings.selectDocument} />}
        {payload.kind === 'markdown' && <MarkdownViewer key={payload.activePath} payload={payload} />}
        {payload.kind === 'image' && <ImageViewer key={payload.activePath} payload={payload} />}
        {payload.kind === 'pdf' && <PdfViewer key={payload.activePath} payload={payload} />}
        {payload.kind === 'word' && <WordViewer key={payload.activePath} payload={payload} />}
        {payload.kind === 'presentation' && (
          <PresentationViewer key={payload.activePath} payload={payload} />
        )}
      </main>
    </ViewerLanguageContext.Provider>
  );
}

function MarkdownViewer({ payload }: { payload: ViewerPayload }) {
  const [lightbox, setLightbox] = useState<{ src: string; alt: string } | null>(null);
  const scrollRef = useRef<HTMLDivElement>(null);
  const restoredScrollTop =
    payload.viewState?.kind === 'markdown' && Number.isFinite(payload.viewState.scrollTop)
      ? Math.max(0, payload.viewState.scrollTop)
      : 0;
  const restoredFontScale =
    payload.viewState?.kind === 'markdown' && Number.isFinite(payload.viewState.fontScale)
      ? clamp(payload.viewState.fontScale ?? 1, 0.75, 2)
      : 1;
  const [fontScale, setFontScale] = useState(restoredFontScale);
  const currentFontScale = useRef(fontScale);
  currentFontScale.current = fontScale;

  usePinchZoom(scrollRef, payload.gestures.pinchZoom, (factor) => {
    setFontScale((value) => clamp(Math.round(value * factor * 100) / 100, 0.75, 2));
  });

  useLayoutEffect(() => {
    const element = scrollRef.current;
    if (!element) return undefined;
    let saveTimer: number | undefined;
    const save = () => {
      saveTimer = undefined;
      saveViewState(payload.activePath, {
        kind: 'markdown',
        scrollTop: element.scrollTop,
        fontScale: currentFontScale.current,
      });
    };
    const scheduleSave = () => {
      if (saveTimer !== undefined) window.clearTimeout(saveTimer);
      saveTimer = window.setTimeout(save, 150);
    };
    const restoreFrame = window.requestAnimationFrame(() => {
      element.scrollTop = Math.min(restoredScrollTop, Math.max(0, element.scrollHeight - element.clientHeight));
    });
    element.addEventListener('scroll', scheduleSave, { passive: true });
    return () => {
      window.cancelAnimationFrame(restoreFrame);
      if (saveTimer !== undefined) window.clearTimeout(saveTimer);
      element.removeEventListener('scroll', scheduleSave);
      save();
    };
  }, [payload.activePath, restoredScrollTop]);

  useEffect(() => {
    saveViewState(payload.activePath, {
      kind: 'markdown',
      scrollTop: scrollRef.current?.scrollTop ?? restoredScrollTop,
      fontScale,
    });
  }, [fontScale, payload.activePath, restoredScrollTop]);

  return (
    <div className="markdown-scroll" ref={scrollRef}>
      <article className="markdown-body" style={{ fontSize: `${16 * fontScale}px` }}>
        <ReactMarkdown
          remarkPlugins={[remarkGfm]}
          rehypePlugins={[rehypeSlug, rehypeHighlight]}
          urlTransform={(url, key) => {
            if (key === 'src' || (key === 'href' && documentKindFromReference(url))) return url;
            return defaultUrlTransform(url);
          }}
          components={{
            img: ({ src = '', alt = '' }) => (
              <MarkdownImage
                src={src}
                alt={alt}
                activePath={payload.activePath}
                onOpen={(imageSrc, imageAlt) => setLightbox({ src: imageSrc, alt: imageAlt })}
              />
            ),
            code: ({ inline, className, children, ...props }: any) => {
              const language = /language-(\w+)/.exec(className ?? '')?.[1] ?? '';
              if (!inline && language.toLowerCase() === 'mermaid') {
                return <MermaidDiagram chart={String(children).replace(/\n$/, '')} dark={payload.dark} />;
              }
              return (
                <code className={className} {...props}>
                  {children}
                </code>
              );
            },
            a: ({ href = '', children, ...props }) => {
              const handleClick = (event: MouseEvent<HTMLAnchorElement>) => {
                if (href.startsWith('#')) return;
                event.preventDefault();
                if (isExternalReference(href)) {
                  openExternal(href.startsWith('//') ? `https:${href}` : href);
                } else if (documentKindFromReference(href)) {
                  openDocument(payload.activePath, href);
                }
              };
              return (
                <a {...props} href={href} onClick={handleClick}>
                  <MarkdownLinkContext.Provider value>{children}</MarkdownLinkContext.Provider>
                </a>
              );
            },
          }}
        >
          {payload.content}
        </ReactMarkdown>
      </article>
      {lightbox && (
        <Lightbox src={lightbox.src} alt={lightbox.alt} onClose={() => setLightbox(null)} />
      )}
    </div>
  );
}

function MarkdownImage({
  src,
  alt,
  activePath,
  onOpen,
}: {
  src: string;
  alt: string;
  activePath: string;
  onOpen: (src: string, alt: string) => void;
}) {
  const strings = useViewerStrings();
  const insideLink = useContext(MarkdownLinkContext);
  const [failed, setFailed] = useState(false);
  const resolved = useMemo(
    () => (isDirectImageSource(src) ? src : resolveResource(activePath, src)),
    [activePath, src],
  );

  useEffect(() => setFailed(false), [resolved]);

  if (!resolved || failed) {
    return <span className="image-error">{strings.imageLoadError}: {alt || src}</span>;
  }

  return (
    <img
      src={resolved}
      alt={alt}
      loading="lazy"
      className={insideLink ? 'markdown-image linked' : 'markdown-image'}
      onError={() => setFailed(true)}
      onClick={() => {
        if (!insideLink) onOpen(resolved, alt);
      }}
    />
  );
}

function MermaidDiagram({ chart, dark }: { chart: string; dark: boolean }) {
  const strings = useViewerStrings();
  const [svg, setSvg] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    if (chart.length > 200_000) {
      setError(strings.diagramTooLarge);
      return undefined;
    }
    mermaid.initialize({
      startOnLoad: false,
      securityLevel: 'strict',
      theme: dark ? 'dark' : 'default',
      suppressErrorRendering: true,
    });
    const id = `mermaid-${Date.now()}-${Math.random().toString(36).slice(2)}`;
    setSvg('');
    setError('');
    mermaid
      .render(id, chart)
      .then((result) => {
        if (!cancelled) setSvg(result.svg);
      })
      .catch((reason: unknown) => {
        if (!cancelled) setError(reason instanceof Error ? reason.message : String(reason));
      });
    return () => {
      cancelled = true;
    };
  }, [chart, dark, strings.diagramTooLarge]);

  if (error) return <pre className="mermaid-error">{strings.mermaidError}: {error}</pre>;
  if (!svg) return <Status message={strings.renderingDiagram} compact />;
  return <div className="mermaid-diagram" dangerouslySetInnerHTML={{ __html: svg }} />;
}

function ImageViewer({ payload }: { payload: ViewerPayload }) {
  const strings = useViewerStrings();
  const restored = imageStateFor(payload);
  const [zoom, setZoom] = useState(restored.zoom);
  const [rotation, setRotation] = useState(restored.rotation);
  const [failed, setFailed] = useState(false);
  const [lightbox, setLightbox] = useState(false);
  const stageRef = useRef<HTMLDivElement>(null);

  usePinchZoom(stageRef, payload.gestures.pinchZoom, (factor) => {
    setZoom((value) => clamp(Math.round(value * factor * 100) / 100, 0.25, 4));
  });

  useEffect(() => {
    saveViewState(payload.activePath, { kind: 'image', zoom, rotation });
  }, [payload.activePath, rotation, zoom]);

  return (
    <section className="binary-viewer" aria-label={`${strings.image}: ${payload.name}`}>
      {payload.viewerControlsVisible && (
        <ViewerToolbar title={payload.name}>
          <ToolbarButton label={strings.zoomOut} onClick={() => setZoom((value) => clamp(value - 0.25, 0.25, 4))}>−</ToolbarButton>
          <span className="zoom-label">{Math.round(zoom * 100)}%</span>
          <ToolbarButton label={strings.zoomIn} onClick={() => setZoom((value) => clamp(value + 0.25, 0.25, 4))}>＋</ToolbarButton>
          <ToolbarButton label={strings.fit} onClick={() => setZoom(1)}>{strings.fit}</ToolbarButton>
          <ToolbarButton label={strings.rotateClockwise} onClick={() => setRotation((value) => (value + 90) % 360)}>↻</ToolbarButton>
        </ViewerToolbar>
      )}
      <div className={payload.gestures.pinchZoom ? 'image-stage pinch-enabled' : 'image-stage'} ref={stageRef}>
        {failed ? (
          <Status message={`${strings.imageLoadError}.`} error />
        ) : (
          <img
            className="standalone-image"
            src={payload.resourceUrl}
            alt={payload.name}
            onError={() => setFailed(true)}
            onDoubleClick={() => setLightbox(true)}
            style={{ transform: `scale(${zoom}) rotate(${rotation}deg)` }}
          />
        )}
      </div>
      {lightbox && <Lightbox src={payload.resourceUrl} alt={payload.name} onClose={() => setLightbox(false)} />}
    </section>
  );
}

function PdfViewer({ payload }: { payload: ViewerPayload }) {
  const strings = useViewerStrings();
  const restored = pdfStateFor(payload);
  const stageRef = useRef<HTMLDivElement>(null);
  const [pages, setPages] = useState(0);
  const [page, setPage] = useState(restored.page);
  const [pageInput, setPageInput] = useState(String(restored.page));
  const [zoom, setZoom] = useState(restored.zoom);
  const [rotation, setRotation] = useState(restored.rotation);
  const [stageWidth, setStageWidth] = useState(800);
  const [error, setError] = useState('');
  const [passwordRequest, setPasswordRequest] = useState<{
    submit: (password: string) => void;
    reason: number;
  } | null>(null);
  const [password, setPassword] = useState('');

  usePinchZoom(stageRef, payload.gestures.pinchZoom, (factor) => {
    setZoom((value) => clamp(Math.round(value * factor * 100) / 100, 0.5, 2.5));
  });

  useEffect(() => {
    const state = pdfStateFor(payload);
    setPage(state.page);
    setPageInput(String(state.page));
    setZoom(state.zoom);
    setRotation(state.rotation);
    setPages(0);
    setError('');
    setPasswordRequest(null);
  }, [payload.activePath]);

  useEffect(() => {
    pdfViewStates.set(payload.activePath, { page, zoom, rotation });
    saveViewState(payload.activePath, { kind: 'pdf', page, zoom, rotation });
  }, [page, payload.activePath, rotation, zoom]);

  useEffect(() => {
    const stage = stageRef.current;
    if (!stage) return undefined;
    const update = () => setStageWidth(stage.clientWidth);
    update();
    const observer = new ResizeObserver(update);
    observer.observe(stage);
    return () => observer.disconnect();
  }, []);

  const goToPage = (next: number) => {
    const bounded = Math.min(Math.max(next, 1), Math.max(pages, 1));
    setPage(bounded);
    setPageInput(String(bounded));
  };
  const commitPage = () => {
    const parsed = Number.parseInt(pageInput, 10);
    if (Number.isNaN(parsed)) setPageInput(String(page));
    else goToPage(parsed);
  };
  const submitPassword = (event: FormEvent) => {
    event.preventDefault();
    if (!passwordRequest || !password) return;
    passwordRequest.submit(password);
    setPasswordRequest(null);
    setPassword('');
  };
  const pageWidth = Math.max(260, Math.floor(stageWidth - 32));

  return (
    <section className="binary-viewer pdf-viewer" aria-label={`PDF: ${payload.name}`}>
      {payload.viewerControlsVisible && (
        <ViewerToolbar title={payload.name}>
          <ToolbarButton label={strings.previousPage} disabled={page <= 1} onClick={() => goToPage(page - 1)}>‹</ToolbarButton>
          <input
            className="page-input"
            aria-label={strings.pageNumber}
            inputMode="numeric"
            value={pageInput}
            onChange={(event) => setPageInput(event.target.value)}
            onBlur={commitPage}
            onKeyDown={(event) => {
              if (event.key === 'Enter') event.currentTarget.blur();
            }}
          />
          <span className="page-total">/ {pages || '–'}</span>
          <ToolbarButton label={strings.nextPage} disabled={!pages || page >= pages} onClick={() => goToPage(page + 1)}>›</ToolbarButton>
          <span className="toolbar-separator" />
          <ToolbarButton label={strings.zoomOut} onClick={() => setZoom((value) => clamp(value - 0.1, 0.5, 2.5))}>−</ToolbarButton>
          <span className="zoom-label">{Math.round(zoom * 100)}%</span>
          <ToolbarButton label={strings.zoomIn} onClick={() => setZoom((value) => clamp(value + 0.1, 0.5, 2.5))}>＋</ToolbarButton>
          <ToolbarButton label={strings.fitWidth} onClick={() => setZoom(1)}>{strings.fit}</ToolbarButton>
          <ToolbarButton label={strings.rotateClockwise} onClick={() => setRotation((value) => (value + 90) % 360)}>↻</ToolbarButton>
        </ViewerToolbar>
      )}
      <div className={payload.gestures.pinchZoom ? 'pdf-stage pinch-enabled' : 'pdf-stage'} ref={stageRef}>
        {error ? (
          <Status message={`${strings.pdfOpenError}: ${error}`} error />
        ) : (
          <Document
            key={payload.resourceUrl}
            file={payload.resourceUrl}
            options={pdfOptions}
            loading={<Status message={strings.analyzingPdf} />}
            onLoadSuccess={({ numPages }) => {
              setPages(numPages);
              goToPage(Math.min(page, numPages));
              setError('');
            }}
            onLoadError={(reason) => setError(reason.message)}
            onPassword={(submit, reason) => {
              setPasswordRequest({ submit, reason });
              setPassword('');
            }}
          >
            <Page
              pageNumber={page}
              width={pageWidth}
              scale={zoom}
              rotate={rotation}
              renderTextLayer
              renderAnnotationLayer
              loading={<Status message={strings.renderingPage} compact />}
            />
          </Document>
        )}
        {passwordRequest && (
          <form className="password-dialog" onSubmit={submitPassword}>
            <strong>{strings.passwordProtectedPdf}</strong>
            <span>{passwordRequest.reason === 2 ? strings.incorrectPassword : strings.enterPdfPassword}</span>
            <input
              type="password"
              value={password}
              autoFocus
              aria-label={strings.pdfPassword}
              onChange={(event) => setPassword(event.target.value)}
            />
            <button type="submit" disabled={!password}>{strings.open}</button>
          </form>
        )}
      </div>
    </section>
  );
}

function WordViewer({ payload }: { payload: ViewerPayload }) {
  const strings = useViewerStrings();
  const restored = wordStateFor(payload);
  const stageRef = useRef<HTMLDivElement>(null);
  const stylesRef = useRef<HTMLDivElement>(null);
  const documentRef = useRef<HTMLDivElement>(null);
  const currentZoom = useRef(restored.zoom);
  const [zoom, setZoom] = useState(restored.zoom);
  const [loading, setLoading] = useState(!payload.legacyOffice);
  const [error, setError] = useState('');
  currentZoom.current = zoom;

  usePinchZoom(stageRef, payload.gestures.pinchZoom, (factor) => {
    setZoom((value) => clamp(Math.round(value * factor * 100) / 100, 0.5, 2));
  });

  useEffect(() => {
    if (payload.legacyOffice || !payload.resourceUrl) return undefined;
    const controller = new AbortController();
    const stage = stageRef.current;
    const styles = stylesRef.current;
    const body = documentRef.current;
    if (!stage || !styles || !body) return undefined;
    let disposed = false;
    let removeLinkInterceptor: () => void = () => {};
    setLoading(true);
    setError('');
    releaseOfficeBlobUrls(styles, body);
    styles.replaceChildren();
    body.replaceChildren();

    void Promise.all([
      fetchOfficeBuffer(payload.resourceUrl, controller.signal, strings),
      import('docx-preview'),
    ])
      .then(async ([buffer, docx]) => {
        if (disposed) return;
        await docx.renderAsync(buffer, body, styles, {
          breakPages: true,
          debug: false,
          ignoreLastRenderedPageBreak: false,
          renderAltChunks: false,
          renderComments: false,
          useBase64URL: false,
        });
        if (disposed) return;
        removeLinkInterceptor = interceptOfficeLinks(body, payload);
        window.requestAnimationFrame(() => {
          stage.scrollTop = Math.min(
            restored.scrollTop,
            Math.max(0, stage.scrollHeight - stage.clientHeight),
          );
        });
        setLoading(false);
      })
      .catch((reason: unknown) => {
        if (disposed || controller.signal.aborted) return;
        setLoading(false);
        setError(errorMessage(reason, strings));
      });

    let saveTimer: number | undefined;
    const save = () => {
      saveTimer = undefined;
      saveViewState(payload.activePath, {
        kind: 'word',
        scrollTop: stage.scrollTop,
        zoom: currentZoom.current,
      });
    };
    const scheduleSave = () => {
      if (saveTimer !== undefined) window.clearTimeout(saveTimer);
      saveTimer = window.setTimeout(save, 160);
    };
    stage.addEventListener('scroll', scheduleSave, { passive: true });
    return () => {
      disposed = true;
      controller.abort();
      stage.removeEventListener('scroll', scheduleSave);
      if (saveTimer !== undefined) window.clearTimeout(saveTimer);
      save();
      removeLinkInterceptor();
      releaseOfficeBlobUrls(styles, body);
      styles.replaceChildren();
      body.replaceChildren();
    };
  }, [payload.activePath, payload.legacyOffice, payload.resourceUrl, restored.scrollTop, strings]);

  useEffect(() => {
    saveViewState(payload.activePath, {
      kind: 'word',
      scrollTop: stageRef.current?.scrollTop ?? restored.scrollTop,
      zoom,
    });
  }, [payload.activePath, restored.scrollTop, zoom]);

  if (payload.legacyOffice) {
    return <LegacyOfficeStatus format="DOC" />;
  }

  return (
    <section className="binary-viewer office-viewer" aria-label={`${strings.wordDocument}: ${payload.name}`}>
      {payload.viewerControlsVisible && (
        <ViewerToolbar title={payload.name}>
          <ToolbarButton label={strings.zoomOut} onClick={() => setZoom((value) => clamp(value - 0.1, 0.5, 2))}>−</ToolbarButton>
          <span className="zoom-label">{Math.round(zoom * 100)}%</span>
          <ToolbarButton label={strings.zoomIn} onClick={() => setZoom((value) => clamp(value + 0.1, 0.5, 2))}>＋</ToolbarButton>
          <ToolbarButton label={strings.originalSize} onClick={() => setZoom(1)}>100%</ToolbarButton>
        </ViewerToolbar>
      )}
      <div className={payload.gestures.pinchZoom ? 'office-stage pinch-enabled' : 'office-stage'} ref={stageRef}>
        <div ref={stylesRef} className="office-style-host" aria-hidden="true" />
        <div
          ref={documentRef}
          className="word-document"
          style={{ '--office-zoom': zoom } as CSSProperties}
        />
        {loading && <div className="office-overlay"><Status message={strings.analyzingWord} compact /></div>}
        {error && <div className="office-overlay"><Status message={`${strings.wordOpenError}: ${error}`} error /></div>}
      </div>
    </section>
  );
}

function PresentationViewer({ payload }: { payload: ViewerPayload }) {
  const strings = useViewerStrings();
  const restored = presentationStateFor(payload);
  const stageRef = useRef<HTMLDivElement>(null);
  const slidesRef = useRef<HTMLDivElement>(null);
  const viewerRef = useRef<import('@aiden0z/pptx-renderer').PptxViewer | null>(null);
  const currentZoom = useRef(restored.zoom);
  const currentSlide = useRef(restored.slide);
  const [zoom, setZoom] = useState(restored.zoom);
  const [slide, setSlide] = useState(restored.slide);
  const [slideCount, setSlideCount] = useState(0);
  const [loading, setLoading] = useState(!payload.legacyOffice);
  const [error, setError] = useState('');
  currentZoom.current = zoom;
  currentSlide.current = slide;

  usePinchZoom(stageRef, payload.gestures.pinchZoom, (factor) => {
    setZoom((value) => clamp(Math.round(value * factor * 100) / 100, 0.5, 2));
  });

  useEffect(() => {
    if (payload.legacyOffice || !payload.resourceUrl) return undefined;
    const controller = new AbortController();
    const stage = stageRef.current;
    const slides = slidesRef.current;
    if (!stage || !slides) return undefined;
    let disposed = false;
    let removeLinkInterceptor: () => void = () => {};
    setLoading(true);
    setError('');
    setSlideCount(0);
    slides.replaceChildren();

    void Promise.all([
      fetchOfficeBuffer(payload.resourceUrl, controller.signal, strings),
      import('@aiden0z/pptx-renderer'),
    ])
      .then(async ([buffer, pptx]) => {
        if (disposed) return;
        const viewer = await pptx.PptxViewer.open(buffer, slides, {
          fitMode: 'contain',
          lazyMedia: true,
          lazySlides: true,
          listOptions: {
            batchSize: 6,
            initialSlides: 3,
            overscanViewport: 1.5,
            showSlideLabels: true,
            windowed: true,
          },
          pdfjs: false,
          renderMode: 'list',
          scrollContainer: stage,
          signal: controller.signal,
          zoomPercent: Math.round(currentZoom.current * 100),
          zipLimits: pptx.RECOMMENDED_ZIP_LIMITS,
          onSlideChange: (index) => {
            if (disposed) return;
            const next = index + 1;
            currentSlide.current = next;
            setSlide(next);
            saveViewState(payload.activePath, {
              kind: 'presentation',
              slide: next,
              zoom: currentZoom.current,
            });
          },
        });
        if (disposed) {
          viewer.destroy();
          return;
        }
        viewerRef.current = viewer;
        setSlideCount(viewer.slideCount);
        removeLinkInterceptor = interceptOfficeLinks(slides, payload);
        const targetSlide = Math.min(Math.max(restored.slide, 1), Math.max(viewer.slideCount, 1));
        if (targetSlide > 1) {
          await viewer.goToSlide(targetSlide - 1, { behavior: 'instant', block: 'start' });
        }
        setSlide(targetSlide);
        setLoading(false);
      })
      .catch((reason: unknown) => {
        if (disposed || controller.signal.aborted) return;
        setLoading(false);
        setError(errorMessage(reason, strings));
      });

    return () => {
      disposed = true;
      controller.abort();
      removeLinkInterceptor();
      viewerRef.current?.destroy();
      viewerRef.current = null;
      slides.replaceChildren();
    };
  }, [payload.activePath, payload.legacyOffice, payload.resourceUrl, restored.slide, strings]);

  useEffect(() => {
    const viewer = viewerRef.current;
    if (viewer) void viewer.setZoom(Math.round(zoom * 100));
    saveViewState(payload.activePath, {
      kind: 'presentation',
      slide: currentSlide.current,
      zoom,
    });
  }, [payload.activePath, zoom]);

  const goToSlide = (next: number) => {
    const bounded = Math.min(Math.max(next, 1), Math.max(slideCount, 1));
    setSlide(bounded);
    currentSlide.current = bounded;
    void viewerRef.current?.goToSlide(bounded - 1, { behavior: 'smooth', block: 'start' });
  };

  if (payload.legacyOffice) {
    return <LegacyOfficeStatus format="PPT" />;
  }

  return (
    <section className="binary-viewer office-viewer" aria-label={`${strings.powerpointDocument}: ${payload.name}`}>
      {payload.viewerControlsVisible && (
        <ViewerToolbar title={payload.name}>
          <ToolbarButton label={strings.previousSlide} disabled={slide <= 1} onClick={() => goToSlide(slide - 1)}>‹</ToolbarButton>
          <span className="page-total">{slide} / {slideCount || '–'}</span>
          <ToolbarButton label={strings.nextSlide} disabled={!slideCount || slide >= slideCount} onClick={() => goToSlide(slide + 1)}>›</ToolbarButton>
          <span className="toolbar-separator" />
          <ToolbarButton label={strings.zoomOut} onClick={() => setZoom((value) => clamp(value - 0.1, 0.5, 2))}>−</ToolbarButton>
          <span className="zoom-label">{Math.round(zoom * 100)}%</span>
          <ToolbarButton label={strings.zoomIn} onClick={() => setZoom((value) => clamp(value + 0.1, 0.5, 2))}>＋</ToolbarButton>
          <ToolbarButton label={strings.fitWidth} onClick={() => setZoom(1)}>{strings.fit}</ToolbarButton>
        </ViewerToolbar>
      )}
      <div className={payload.gestures.pinchZoom ? 'office-stage presentation-stage pinch-enabled' : 'office-stage presentation-stage'} ref={stageRef}>
        <div ref={slidesRef} className="presentation-document" />
        {loading && <div className="office-overlay"><Status message={strings.analyzingPowerpoint} compact /></div>}
        {error && <div className="office-overlay"><Status message={`${strings.powerpointOpenError}: ${error}`} error /></div>}
      </div>
    </section>
  );
}

function LegacyOfficeStatus({ format }: { format: 'DOC' | 'PPT' }) {
  const strings = useViewerStrings();
  return (
    <div className="legacy-office-status">
      <Status
        message={strings.legacyOffice(format)}
      />
    </div>
  );
}

function ViewerToolbar({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="viewer-toolbar">
      <strong className="viewer-title" title={title}>{title}</strong>
      <div className="viewer-controls">{children}</div>
    </div>
  );
}

function ToolbarButton({
  label,
  disabled = false,
  onClick,
  children,
}: {
  label: string;
  disabled?: boolean;
  onClick: () => void;
  children: ReactNode;
}) {
  return (
    <button type="button" className="toolbar-button" aria-label={label} title={label} disabled={disabled} onClick={onClick}>
      {children}
    </button>
  );
}

function Lightbox({ src, alt, onClose }: { src: string; alt: string; onClose: () => void }) {
  const strings = useViewerStrings();
  useEffect(() => {
    const handleKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', handleKey);
    return () => window.removeEventListener('keydown', handleKey);
  }, [onClose]);

  return (
    <div className="lightbox" role="dialog" aria-modal="true" aria-label={alt || strings.enlargedImage} onClick={onClose}>
      <button type="button" className="lightbox-close" aria-label={strings.close} onClick={onClose}>×</button>
      <img src={src} alt={alt} onClick={(event) => event.stopPropagation()} />
    </div>
  );
}

function Status({ message, error = false, compact = false }: { message: string; error?: boolean; compact?: boolean }) {
  return <div className={`status ${error ? 'error' : ''} ${compact ? 'compact' : ''}`}>{message}</div>;
}

function clamp(value: number, minimum: number, maximum: number): number {
  return Math.min(maximum, Math.max(minimum, Number(value.toFixed(2))));
}

function imageStateFor(payload: ViewerPayload): { zoom: number; rotation: number } {
  if (payload.viewState?.kind !== 'image') return { zoom: 1, rotation: 0 };
  return {
    zoom: clamp(Number.isFinite(payload.viewState.zoom) ? payload.viewState.zoom : 1, 0.25, 4),
    rotation: normalizeRotation(payload.viewState.rotation),
  };
}

function pdfStateFor(payload: ViewerPayload): PdfViewState {
  const state =
    payload.viewState?.kind === 'pdf'
      ? payload.viewState
      : pdfViewStates.get(payload.activePath) ?? { page: 1, zoom: 1, rotation: 0 };
  return {
    page: Math.max(1, Math.floor(Number.isFinite(state.page) ? state.page : 1)),
    zoom: clamp(Number.isFinite(state.zoom) ? state.zoom : 1, 0.5, 2.5),
    rotation: normalizeRotation(state.rotation),
  };
}

function wordStateFor(payload: ViewerPayload): { scrollTop: number; zoom: number } {
  if (payload.viewState?.kind !== 'word') return { scrollTop: 0, zoom: 1 };
  return {
    scrollTop: Math.max(0, Number.isFinite(payload.viewState.scrollTop) ? payload.viewState.scrollTop : 0),
    zoom: clamp(Number.isFinite(payload.viewState.zoom) ? payload.viewState.zoom : 1, 0.5, 2),
  };
}

function presentationStateFor(payload: ViewerPayload): { slide: number; zoom: number } {
  if (payload.viewState?.kind !== 'presentation') return { slide: 1, zoom: 1 };
  return {
    slide: Math.max(1, Math.floor(Number.isFinite(payload.viewState.slide) ? payload.viewState.slide : 1)),
    zoom: clamp(Number.isFinite(payload.viewState.zoom) ? payload.viewState.zoom : 1, 0.5, 2),
  };
}

async function fetchOfficeBuffer(
  resourceUrl: string,
  signal: AbortSignal,
  strings: ViewerStrings,
): Promise<ArrayBuffer> {
  const response = await fetch(resourceUrl, { signal });
  if (!response.ok) throw new Error(strings.fileReadFailed(response.status));
  const declaredSize = Number(response.headers.get('content-length') ?? 0);
  if (Number.isFinite(declaredSize) && declaredSize > 60 * 1024 * 1024) {
    throw new Error(strings.fileTooLarge);
  }
  const buffer = await response.arrayBuffer();
  if (buffer.byteLength > 60 * 1024 * 1024) {
    throw new Error(strings.fileTooLarge);
  }
  return buffer;
}

function interceptOfficeLinks(container: HTMLElement, payload: ViewerPayload): () => void {
  const handleClick = (event: globalThis.MouseEvent) => {
    const target = event.target;
    if (!(target instanceof Element)) return;
    const anchor = target.closest<HTMLAnchorElement>('a[href]');
    if (!anchor) return;
    const reference = anchor.getAttribute('href')?.trim() ?? '';
    if (!reference || reference.startsWith('#')) return;
    event.preventDefault();
    event.stopPropagation();
    if (isExternalReference(reference)) {
      openExternal(reference.startsWith('//') ? `https:${reference}` : reference);
    } else if (documentKindFromReference(reference)) {
      openDocument(payload.activePath, reference);
    }
  };
  container.addEventListener('click', handleClick);
  return () => container.removeEventListener('click', handleClick);
}

function releaseOfficeBlobUrls(...containers: HTMLElement[]): void {
  const urls = new Set<string>();
  containers.forEach((container) => {
    container.querySelectorAll<HTMLElement>('*').forEach((element) => {
      for (const attribute of element.getAttributeNames()) {
        collectBlobUrls(element.getAttribute(attribute) ?? '', urls);
      }
    });
    collectBlobUrls(container.textContent ?? '', urls);
  });
  urls.forEach((url) => URL.revokeObjectURL(url));
}

function collectBlobUrls(value: string, urls: Set<string>): void {
  for (const match of value.matchAll(/blob:[^\s)'";]+/g)) urls.add(match[0]);
}

function errorMessage(reason: unknown, strings: ViewerStrings): string {
  if (reason instanceof DOMException && reason.name === 'AbortError') return strings.loadingCancelled;
  if (reason instanceof Error && reason.message) return reason.message;
  return String(reason || strings.unknownError);
}

function normalizeRotation(value: number): number {
  if (!Number.isFinite(value)) return 0;
  return ((Math.round(value / 90) * 90) % 360 + 360) % 360;
}

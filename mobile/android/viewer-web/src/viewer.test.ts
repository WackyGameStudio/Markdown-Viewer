import { describe, expect, it } from 'vitest';
import { classifyEdgeSwipe } from './gestures';
import { documentKindFromReference, isExternalReference } from './references';
import { viewerStrings } from './i18n';

describe('document references', () => {
  it('recognizes supported documents with suffixes', () => {
    expect(documentKindFromReference('../guide.MD#start')).toBe('markdown');
    expect(documentKindFromReference('./image.webp?raw=1')).toBe('image');
    expect(documentKindFromReference('manual.pdf')).toBe('pdf');
    expect(documentKindFromReference('../media/demo.mp4#t=10')).toBe('video');
    expect(documentKindFromReference('./report.docx')).toBe('word');
    expect(documentKindFromReference('./slides.PPTX?download=1')).toBe('presentation');
    expect(documentKindFromReference('./preview.html#section')).toBe('html');
  });

  it('separates external references from local paths', () => {
    expect(isExternalReference('https://example.com')).toBe(true);
    expect(isExternalReference('../guide.md')).toBe(false);
  });
});

describe('edge swipe classification', () => {
  const viewport = { width: 800, height: 1200 };
  const enabled = { edgeLeft: true, edgeRight: true, edgeTop: true };

  it('maps the three configured inward edge directions', () => {
    expect(classifyEdgeSwipe(viewport, { x: 8, y: 500 }, { x: 120, y: 510 }, enabled)).toBe(
      'edge-left-in',
    );
    expect(classifyEdgeSwipe(viewport, { x: 792, y: 500 }, { x: 680, y: 490 }, enabled)).toBe(
      'edge-right-in',
    );
    expect(classifyEdgeSwipe(viewport, { x: 400, y: 8 }, { x: 405, y: 110 }, enabled)).toBe(
      'edge-top-in',
    );
  });

  it('rejects short, diagonal, and disabled edge swipes', () => {
    expect(classifyEdgeSwipe(viewport, { x: 8, y: 500 }, { x: 60, y: 500 }, enabled)).toBeNull();
    expect(classifyEdgeSwipe(viewport, { x: 8, y: 500 }, { x: 110, y: 610 }, enabled)).toBeNull();
    expect(
      classifyEdgeSwipe(viewport, { x: 8, y: 500 }, { x: 120, y: 500 }, { ...enabled, edgeLeft: false }),
    ).toBeNull();
  });
});

describe('viewer localization', () => {
  it('provides Korean and English document controls', () => {
    expect(viewerStrings.ko.selectDocument).toBe('문서를 선택하세요.');
    expect(viewerStrings.en.selectDocument).toBe('Select a document.');
    expect(viewerStrings.ko.legacyOffice('PPT')).toContain('.ppt');
    expect(viewerStrings.en.legacyOffice('DOC')).toContain('.doc');
  });
});

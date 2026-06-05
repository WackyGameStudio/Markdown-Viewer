import { describe, expect, it } from 'vitest';
import { isDirectImageSource, resolveLocalImagePath } from './imagePaths';

describe('isDirectImageSource', () => {
  it('keeps browser-rendered sources unchanged', () => {
    expect(isDirectImageSource()).toBe(true);
    expect(isDirectImageSource('')).toBe(true);
    expect(isDirectImageSource('https://example.com/a.png')).toBe(true);
    expect(isDirectImageSource('http://example.com/a.png')).toBe(true);
    expect(isDirectImageSource('data:image/png;base64,abc')).toBe(true);
    expect(isDirectImageSource('//cdn.example.com/a.png')).toBe(true);
    expect(isDirectImageSource('blob:https://example.com/id')).toBe(true);
  });

  it('treats local paths as backend-loaded sources', () => {
    expect(isDirectImageSource('./a.png')).toBe(false);
    expect(isDirectImageSource('file:///D:/docs/a.png')).toBe(false);
    expect(isDirectImageSource('D:\\docs\\a.png')).toBe(false);
  });
});

describe('resolveLocalImagePath', () => {
  const activeFile = 'D:\\docs\\notes\\index.md';

  it('resolves dot-relative paths against the active markdown file', () => {
    expect(resolveLocalImagePath('./img.png', activeFile)).toBe('D:\\docs\\notes\\img.png');
  });

  it('resolves parent-relative paths against the active markdown file', () => {
    expect(resolveLocalImagePath('../img.png', activeFile)).toBe('D:\\docs\\img.png');
  });

  it('resolves bare relative paths against the active markdown file', () => {
    expect(resolveLocalImagePath('images/a.webp', activeFile)).toBe('D:\\docs\\notes\\images\\a.webp');
  });

  it('keeps Windows absolute paths absolute', () => {
    expect(resolveLocalImagePath('C:/assets/a.jpg', activeFile)).toBe('C:\\assets\\a.jpg');
    expect(resolveLocalImagePath('C:\\assets\\a.jpg', activeFile)).toBe('C:\\assets\\a.jpg');
  });

  it('keeps UNC paths absolute', () => {
    expect(resolveLocalImagePath('\\\\server\\share\\img.png', activeFile)).toBe('\\\\server\\share\\img.png');
  });

  it('converts file URLs to Windows paths', () => {
    expect(resolveLocalImagePath('file:///D:/docs/image%201.png', activeFile)).toBe('D:\\docs\\image 1.png');
  });

  it('converts four-slash file URLs to UNC paths', () => {
    expect(resolveLocalImagePath('file:////server/share/img.png', activeFile)).toBe('\\\\server\\share\\img.png');
  });

  it('resolves paths against active file URLs', () => {
    expect(resolveLocalImagePath('./img.png', 'file:///D:/docs/note.md')).toBe('D:\\docs\\img.png');
  });

  it('throws on malformed file URLs', () => {
    expect(() => resolveLocalImagePath('file://', activeFile)).toThrow('Invalid file URL');
    expect(() => resolveLocalImagePath('file:///D:/docs/%E0%A4%A.png', activeFile)).toThrow('Invalid file URL');
  });

  it('throws a source decode error for malformed normal paths', () => {
    expect(() => resolveLocalImagePath('images/%E0%A4%A.png', activeFile)).toThrow('Invalid image source path');
  });
});

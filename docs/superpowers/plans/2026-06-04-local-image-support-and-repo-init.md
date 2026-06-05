# Local Image Support and Repository Init Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Initialize the project repository and add local Markdown image rendering with click-to-lightbox support.

**Architecture:** Go reads trusted local image files and returns bounded `data:` URLs. React resolves image paths relative to the active Markdown file, renders local images through the Go API, and uses a small lightbox overlay for previews.

**Tech Stack:** Go 1.26.4, Wails module v2.11.0, Wails CLI v2.12.0, React 18, TypeScript, Vite, react-markdown, Vitest for frontend path tests.

---

## File Structure

- Modify: `.gitignore`
  - Exclude generated dependencies, built frontend output, and existing root release artifacts before initial commit.
- Modify: `app.go`
  - Add `ImageData`, MIME allowlist, max size guard, and `GetImageData`.
- Create: `app_image_test.go`
  - Unit tests for backend image data loading and error paths.
- Modify: `frontend/package.json`
  - Add `test` script and Vitest dev dependency through `npm install --save-dev vitest`.
- Modify: `frontend/package-lock.json`
  - Updated by npm.
- Create: `frontend/src/utils/imagePaths.ts`
  - Resolve Markdown image `src` values against the active `.md` path.
- Create: `frontend/src/utils/imagePaths.test.ts`
  - Vitest coverage for relative, absolute, `file://`, remote, and malformed inputs.
- Create: `frontend/src/components/MarkdownImage.tsx`
  - Render one Markdown image, load local files through Wails, expose click handler for lightbox.
- Create: `frontend/src/components/ImageLightbox.tsx`
  - Full-window image overlay with Escape and overlay close.
- Modify: `frontend/src/App.tsx`
  - Wire `img` component override and lightbox state.
- Modify: `frontend/src/App.css`
  - Add image loading/error styles and lightbox styles.
- Generated: `frontend/wailsjs/go/main/App.js`
  - Wails binding for `GetImageData`.
- Generated: `frontend/wailsjs/go/main/App.d.ts`
  - TypeScript binding for `GetImageData`.
- Generated: `frontend/wailsjs/go/models.ts`
  - Wails model for `ImageData`.

---

### Task 1: Repository Init and Ignore Hygiene

**Files:**
- Modify: `.gitignore`

- [ ] **Step 1: Confirm current repository state**

Run:

```powershell
git rev-parse --is-inside-work-tree
```

Expected now:

```text
fatal: not a git repository (or any of the parent directories): .git
```

- [ ] **Step 2: Check GitHub remote history**

Run:

```powershell
git ls-remote --heads https://github.com/WackyGameStudio/Markdown-Viewer.git
```

Expected if remote has no branches:

```text
```

If output contains `refs/heads/`, stop before pushing and fetch remote history after `git init`.

- [ ] **Step 3: Update `.gitignore` before first commit**

Change `.gitignore` to:

```gitignore
build/bin
node_modules
frontend/node_modules
frontend/dist
mdviewer*.exe
mdviewer*.zip
.worktrees
```

- [ ] **Step 4: Initialize local repository**

Run:

```powershell
git init -b main
git remote add origin https://github.com/WackyGameStudio/Markdown-Viewer.git
git status --short
```

Expected:

```text
?? .gitignore
?? README.md
?? app.go
?? build/
?? docs/
?? frontend/
?? go.mod
?? go.sum
?? main.go
?? wails.json
```

The ignored `mdviewer*.exe`, `mdviewer*.zip`, `frontend/node_modules`, `frontend/dist`, and `.worktrees` paths must not appear.

- [ ] **Step 5: Create initial commit**

Run:

```powershell
git add .
git commit -m "chore: init markdown viewer project"
```

Expected:

```text
[main (root-commit) ...] chore: init markdown viewer project
```

Do not push yet if Step 2 showed existing remote branches.

---

### Task 2: Toolchain Check

**Files:**
- No file changes

- [ ] **Step 1: Check installed tool files**

Run:

```powershell
where.exe git
where.exe npm
where.exe go
where.exe wails
Test-Path -LiteralPath "C:\Program Files\Go\bin\go.exe"
Test-Path -LiteralPath "$env:USERPROFILE\go\bin\wails.exe"
```

Expected:

```text
<one or more paths ending in git.exe>
<one or more paths ending in npm.cmd>
INFO: Could not find files for the given pattern(s).
INFO: Could not find files for the given pattern(s).
True
True
```

Verified current state:
- `go.exe` exists at `C:\Program Files\Go\bin\go.exe`.
- `wails.exe` exists at `%USERPROFILE%\go\bin\wails.exe`.
- Current Codex shell PATH does not expose `go` or `wails` yet.

- [ ] **Step 2: Add Go/Wails to current shell PATH**

Run:

```powershell
$env:Path = "C:\Program Files\Go\bin;$env:USERPROFILE\go\bin;$env:Path"
go version
wails version
```

Expected:

```text
go version go1.26.4 windows/amd64
v2.12.0
```

- [ ] **Step 3: Persist PATH outside this shell if needed**

Run only if future terminals still cannot find `go` or `wails`:

```powershell
[Environment]::SetEnvironmentVariable(
  "Path",
  [Environment]::GetEnvironmentVariable("Path", "User") + ";C:\Program Files\Go\bin;$env:USERPROFILE\go\bin",
  "User"
)
```

Open a new terminal, then run:

```powershell
go version
wails version
```

Expected:

```text
go version go1.26.4 windows/amd64
v2.12.0
```

- [ ] **Step 4: Reinstall only if tool files are missing**

Run only if Step 1 returns `False` for Go:

```powershell
winget install GoLang.Go
```

Run only if Step 1 returns `False` for Wails:

```powershell
go install github.com/wailsapp/wails/v2/cmd/wails@v2.12.0
```

---

### Task 3: Backend Image API Tests

**Files:**
- Create: `app_image_test.go`

- [ ] **Step 1: Write failing backend tests**

Create `app_image_test.go`:

```go
package main

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func writeTestFile(t *testing.T, dir string, name string, data []byte) string {
	t.Helper()
	path := filepath.Join(dir, name)
	if err := os.WriteFile(path, data, 0o600); err != nil {
		t.Fatalf("write test file: %v", err)
	}
	return path
}

func TestGetImageDataReturnsDataURL(t *testing.T) {
	dir := t.TempDir()
	path := writeTestFile(t, dir, "sample.png", []byte{0x89, 'P', 'N', 'G'})

	app := NewApp()
	got, err := app.GetImageData(path)
	if err != nil {
		t.Fatalf("GetImageData returned error: %v", err)
	}

	if got.MimeType != "image/png" {
		t.Fatalf("MimeType = %q, want image/png", got.MimeType)
	}
	if got.Size != 4 {
		t.Fatalf("Size = %d, want 4", got.Size)
	}
	if !strings.HasPrefix(got.DataURL, "data:image/png;base64,") {
		t.Fatalf("DataURL prefix = %q", got.DataURL)
	}
}

func TestGetImageDataSupportsJPEGAndSVG(t *testing.T) {
	dir := t.TempDir()
	app := NewApp()

	jpgPath := writeTestFile(t, dir, "sample.jpeg", []byte{0xff, 0xd8, 0xff, 0xd9})
	jpg, err := app.GetImageData(jpgPath)
	if err != nil {
		t.Fatalf("jpeg GetImageData returned error: %v", err)
	}
	if jpg.MimeType != "image/jpeg" {
		t.Fatalf("jpeg MimeType = %q, want image/jpeg", jpg.MimeType)
	}

	svgPath := writeTestFile(t, dir, "sample.svg", []byte(`<svg xmlns="http://www.w3.org/2000/svg"></svg>`))
	svg, err := app.GetImageData(svgPath)
	if err != nil {
		t.Fatalf("svg GetImageData returned error: %v", err)
	}
	if svg.MimeType != "image/svg+xml" {
		t.Fatalf("svg MimeType = %q, want image/svg+xml", svg.MimeType)
	}
}

func TestGetImageDataRejectsUnsupportedExtension(t *testing.T) {
	dir := t.TempDir()
	path := writeTestFile(t, dir, "sample.txt", []byte("not an image"))

	app := NewApp()
	_, err := app.GetImageData(path)
	if err == nil || !strings.Contains(err.Error(), "unsupported image type") {
		t.Fatalf("error = %v, want unsupported image type", err)
	}
}

func TestGetImageDataRejectsMissingFile(t *testing.T) {
	app := NewApp()
	_, err := app.GetImageData(filepath.Join(t.TempDir(), "missing.png"))
	if err == nil {
		t.Fatal("error = nil, want missing file error")
	}
}

func TestGetImageDataRejectsDirectory(t *testing.T) {
	app := NewApp()
	_, err := app.GetImageData(t.TempDir())
	if err == nil || !strings.Contains(err.Error(), "unsupported image type") {
		t.Fatalf("error = %v, want unsupported image type for directory without image extension", err)
	}

	dir := filepath.Join(t.TempDir(), "folder.png")
	if err := os.Mkdir(dir, 0o700); err != nil {
		t.Fatalf("mkdir: %v", err)
	}
	_, err = app.GetImageData(dir)
	if err == nil || !strings.Contains(err.Error(), "not a regular file") {
		t.Fatalf("error = %v, want not a regular file", err)
	}
}

func TestGetImageDataRejectsOversizedFile(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "large.png")
	file, err := os.Create(path)
	if err != nil {
		t.Fatalf("create large file: %v", err)
	}
	if err := file.Truncate(maxImageDataBytes + 1); err != nil {
		t.Fatalf("truncate large file: %v", err)
	}
	if err := file.Close(); err != nil {
		t.Fatalf("close large file: %v", err)
	}

	app := NewApp()
	_, err = app.GetImageData(path)
	if err == nil || !strings.Contains(err.Error(), "image file too large") {
		t.Fatalf("error = %v, want image file too large", err)
	}
}
```

- [ ] **Step 2: Run tests and verify failure**

Run:

```powershell
go test ./...
```

Expected:

```text
undefined: maxImageDataBytes
app.GetImageData undefined
```

---

### Task 4: Backend Image API Implementation

**Files:**
- Modify: `app.go`

- [ ] **Step 1: Add imports**

In `app.go`, change the import block to:

```go
import (
	"context"
	"encoding/base64"
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"github.com/wailsapp/wails/v2/pkg/runtime"
)
```

- [ ] **Step 2: Add image data types and constants**

Add below `FileNode`:

```go
type ImageData struct {
	DataURL  string `json:"dataUrl"`
	MimeType string `json:"mimeType"`
	Size     int64  `json:"size"`
}

const maxImageDataBytes int64 = 20 * 1024 * 1024

var supportedImageMimeTypes = map[string]string{
	".png":  "image/png",
	".jpg":  "image/jpeg",
	".jpeg": "image/jpeg",
	".gif":  "image/gif",
	".webp": "image/webp",
	".svg":  "image/svg+xml",
	".bmp":  "image/bmp",
}
```

- [ ] **Step 3: Add `GetImageData`**

Add below `GetMarkdownContent`:

```go
func (a *App) GetImageData(imagePath string) (*ImageData, error) {
	ext := strings.ToLower(filepath.Ext(imagePath))
	mimeType, ok := supportedImageMimeTypes[ext]
	if !ok {
		return nil, fmt.Errorf("unsupported image type: %s", ext)
	}

	info, err := os.Stat(imagePath)
	if err != nil {
		return nil, err
	}
	if !info.Mode().IsRegular() {
		return nil, fmt.Errorf("not a regular file: %s", imagePath)
	}
	if info.Size() > maxImageDataBytes {
		return nil, fmt.Errorf("image file too large: %d bytes", info.Size())
	}

	content, err := os.ReadFile(imagePath)
	if err != nil {
		return nil, err
	}

	encoded := base64.StdEncoding.EncodeToString(content)
	return &ImageData{
		DataURL:  fmt.Sprintf("data:%s;base64,%s", mimeType, encoded),
		MimeType: mimeType,
		Size:     info.Size(),
	}, nil
}
```

- [ ] **Step 4: Run backend tests**

Run:

```powershell
go test ./...
```

Expected:

```text
ok  	tmp_mdviewer	...
```

- [ ] **Step 5: Commit backend API**

Run:

```powershell
git add app.go app_image_test.go
git commit -m "feat: add local image data api"
```

---

### Task 5: Regenerate Wails Bindings

**Files:**
- Modify: `frontend/wailsjs/go/main/App.js`
- Modify: `frontend/wailsjs/go/main/App.d.ts`
- Modify: `frontend/wailsjs/go/models.ts`

- [ ] **Step 1: Generate bindings**

Run:

```powershell
wails generate module
```

Expected generated additions:

```ts
export function GetImageData(arg1:string):Promise<main.ImageData>;
```

If `wails generate module` is unavailable, fix the Wails v2.11.0 CLI installation from Task 2 before continuing.

- [ ] **Step 2: Verify generated files contain image API**

Run:

```powershell
rg -n "GetImageData|ImageData" frontend/wailsjs
```

Expected includes:

```text
frontend\wailsjs\go\main\App.d.ts:export function GetImageData(arg1:string):Promise<main.ImageData>;
frontend\wailsjs\go\main\App.js:export function GetImageData(arg1) {
frontend\wailsjs\go\models.ts:export class ImageData
```

- [ ] **Step 3: Commit generated bindings**

Run:

```powershell
git add frontend/wailsjs/go/main/App.js frontend/wailsjs/go/main/App.d.ts frontend/wailsjs/go/models.ts
git commit -m "chore: regenerate wails image bindings"
```

---

### Task 6: Frontend Image Path Resolver

**Files:**
- Modify: `frontend/package.json`
- Modify: `frontend/package-lock.json`
- Create: `frontend/src/utils/imagePaths.ts`
- Create: `frontend/src/utils/imagePaths.test.ts`

- [ ] **Step 1: Add Vitest**

Run:

```powershell
cd frontend
npm install --save-dev vitest
```

Then add this script in `frontend/package.json`:

```json
"test": "vitest run"
```

- [ ] **Step 2: Write failing resolver tests**

Create `frontend/src/utils/imagePaths.test.ts`:

```ts
import { describe, expect, it } from 'vitest';
import { isDirectImageSource, resolveLocalImagePath } from './imagePaths';

describe('isDirectImageSource', () => {
  it('keeps browser-rendered sources unchanged', () => {
    expect(isDirectImageSource('https://example.com/a.png')).toBe(true);
    expect(isDirectImageSource('http://example.com/a.png')).toBe(true);
    expect(isDirectImageSource('data:image/png;base64,abc')).toBe(true);
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

  it('converts file URLs to Windows paths', () => {
    expect(resolveLocalImagePath('file:///D:/docs/image%201.png', activeFile)).toBe('D:\\docs\\image 1.png');
  });

  it('throws on malformed file URLs', () => {
    expect(() => resolveLocalImagePath('file://', activeFile)).toThrow('Invalid file URL');
  });
});
```

- [ ] **Step 3: Run tests and verify failure**

Run:

```powershell
cd frontend
npm test -- imagePaths
```

Expected:

```text
Failed to resolve import "./imagePaths"
```

- [ ] **Step 4: Implement resolver**

Create `frontend/src/utils/imagePaths.ts`:

```ts
const directSourcePrefixes = ['http://', 'https://', 'data:'];

export function isDirectImageSource(src?: string): boolean {
  if (!src) return true;
  const lower = src.trim().toLowerCase();
  return directSourcePrefixes.some((prefix) => lower.startsWith(prefix));
}

export function resolveLocalImagePath(src: string, activeFile: string): string {
  if (!src) {
    throw new Error('Image source is empty');
  }
  if (!activeFile) {
    throw new Error('Active markdown file is empty');
  }

  const trimmed = src.trim();
  if (trimmed.toLowerCase().startsWith('file://')) {
    return fileUrlToWindowsPath(trimmed);
  }

  const decoded = decodeURIComponent(trimmed).replace(/\//g, '\\');
  if (isWindowsAbsolutePath(decoded) || decoded.startsWith('\\\\')) {
    return normalizeWindowsPath(decoded);
  }

  const baseDir = getWindowsDirname(activeFile);
  return normalizeWindowsPath(`${baseDir}\\${decoded}`);
}

function fileUrlToWindowsPath(src: string): string {
  try {
    const url = new URL(src);
    if (url.protocol !== 'file:') {
      throw new Error('Invalid file URL');
    }
    let pathname = decodeURIComponent(url.pathname);
    if (/^\/[a-zA-Z]:/.test(pathname)) {
      pathname = pathname.slice(1);
    }
    if (!pathname) {
      throw new Error('Invalid file URL');
    }
    return normalizeWindowsPath(pathname.replace(/\//g, '\\'));
  } catch {
    throw new Error('Invalid file URL');
  }
}

function getWindowsDirname(path: string): string {
  const normalized = path.replace(/\//g, '\\');
  const index = normalized.lastIndexOf('\\');
  if (index === -1) {
    throw new Error('Active markdown file has no directory');
  }
  return normalized.slice(0, index);
}

function isWindowsAbsolutePath(path: string): boolean {
  return /^[a-zA-Z]:\\/.test(path);
}

function normalizeWindowsPath(path: string): string {
  const normalized = path.replace(/\//g, '\\');
  const driveMatch = normalized.match(/^([a-zA-Z]:)\\(.*)$/);
  if (!driveMatch) {
    return normalized;
  }

  const drive = driveMatch[1];
  const parts = driveMatch[2].split('\\');
  const output: string[] = [];

  for (const part of parts) {
    if (!part || part === '.') {
      continue;
    }
    if (part === '..') {
      output.pop();
      continue;
    }
    output.push(part);
  }

  return `${drive}\\${output.join('\\')}`;
}
```

- [ ] **Step 5: Run resolver tests**

Run:

```powershell
cd frontend
npm test -- imagePaths
```

Expected:

```text
Test Files  1 passed
Tests  9 passed
```

- [ ] **Step 6: Commit resolver**

Run:

```powershell
git add frontend/package.json frontend/package-lock.json frontend/src/utils/imagePaths.ts frontend/src/utils/imagePaths.test.ts
git commit -m "feat: resolve local markdown image paths"
```

---

### Task 7: Image Components

**Files:**
- Create: `frontend/src/components/MarkdownImage.tsx`
- Create: `frontend/src/components/ImageLightbox.tsx`

- [ ] **Step 1: Create `MarkdownImage`**

Create `frontend/src/components/MarkdownImage.tsx`:

```tsx
import { useEffect, useState } from 'react';
import type { ImgHTMLAttributes } from 'react';
import { GetImageData } from '../../wailsjs/go/main/App';
import { isDirectImageSource, resolveLocalImagePath } from '../utils/imagePaths';

interface MarkdownImageProps extends ImgHTMLAttributes<HTMLImageElement> {
  activeFile: string;
  onOpenLightbox: (src: string, alt: string) => void;
}

type LoadState =
  | { status: 'direct'; src: string }
  | { status: 'loading' }
  | { status: 'loaded'; src: string }
  | { status: 'error'; message: string };

function getAltText(value: unknown): string {
  return typeof value === 'string' ? value : '';
}

export default function MarkdownImage({ activeFile, onOpenLightbox, src, alt, title, ...props }: MarkdownImageProps) {
  const [loadState, setLoadState] = useState<LoadState>(() => {
    const source = typeof src === 'string' ? src : '';
    return isDirectImageSource(source) ? { status: 'direct', src: source } : { status: 'loading' };
  });

  useEffect(() => {
    const source = typeof src === 'string' ? src : '';
    if (isDirectImageSource(source)) {
      setLoadState({ status: 'direct', src: source });
      return;
    }

    let cancelled = false;
    setLoadState({ status: 'loading' });

    try {
      const resolvedPath = resolveLocalImagePath(source, activeFile);
      GetImageData(resolvedPath)
        .then((image) => {
          if (!cancelled) {
            setLoadState({ status: 'loaded', src: image.dataUrl });
          }
        })
        .catch((error) => {
          console.warn('Failed to load local image:', resolvedPath, error);
          if (!cancelled) {
            setLoadState({ status: 'error', message: source });
          }
        });
    } catch (error) {
      console.warn('Failed to resolve local image:', source, error);
      setLoadState({ status: 'error', message: source });
    }

    return () => {
      cancelled = true;
    };
  }, [activeFile, src]);

  const altText = getAltText(alt);

  if (loadState.status === 'loading') {
    return <span className="markdown-image-state">Loading image...</span>;
  }

  if (loadState.status === 'error') {
    return (
      <span className="markdown-image-error" title={loadState.message}>
        {altText || loadState.message || 'Image failed to load'}
      </span>
    );
  }

  return (
    <img
      {...props}
      src={loadState.src}
      alt={altText}
      title={title}
      className="markdown-image"
      onClick={() => onOpenLightbox(loadState.src, altText)}
    />
  );
}
```

- [ ] **Step 2: Create `ImageLightbox`**

Create `frontend/src/components/ImageLightbox.tsx`:

```tsx
import { useEffect } from 'react';

interface ImageLightboxProps {
  src: string;
  alt: string;
  onClose: () => void;
}

export default function ImageLightbox({ src, alt, onClose }: ImageLightboxProps) {
  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onClose();
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [onClose]);

  return (
    <div className="image-lightbox" role="dialog" aria-modal="true" onClick={onClose}>
      <img
        className="image-lightbox-img"
        src={src}
        alt={alt}
        onClick={(event) => event.stopPropagation()}
      />
    </div>
  );
}
```

- [ ] **Step 3: Run frontend build and verify missing wiring only**

Run:

```powershell
cd frontend
npm run build
```

Expected at this point:

```text
✓ built in ...
```

The components compile but are not visible until Task 8.

- [ ] **Step 4: Commit components**

Run:

```powershell
git add frontend/src/components/MarkdownImage.tsx frontend/src/components/ImageLightbox.tsx
git commit -m "feat: add markdown image components"
```

---

### Task 8: Wire Markdown Image Rendering and Lightbox

**Files:**
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.css`

- [ ] **Step 1: Import components**

In `frontend/src/App.tsx`, add:

```tsx
import MarkdownImage from './components/MarkdownImage';
import ImageLightbox from './components/ImageLightbox';
```

- [ ] **Step 2: Add lightbox state**

Below `const [viewportWidth, setViewportWidth] = useState<number>(850);`, add:

```tsx
const [lightboxImage, setLightboxImage] = useState<{ src: string; alt: string } | null>(null);
```

- [ ] **Step 3: Wire `img` component override**

Inside `ReactMarkdown` `components`, after the `a` renderer, add this sibling entry:

```tsx
img: ({ node, ...props }) => (
  <MarkdownImage
    {...props}
    activeFile={activeFile}
    onOpenLightbox={(src, alt) => setLightboxImage({ src, alt })}
  />
),
```

Keep the existing `code` and `a` renderers unchanged.

- [ ] **Step 4: Render lightbox**

Before the closing `</div>` of `.app-container`, add:

```tsx
{lightboxImage && (
  <ImageLightbox
    src={lightboxImage.src}
    alt={lightboxImage.alt}
    onClose={() => setLightboxImage(null)}
  />
)}
```

- [ ] **Step 5: Add CSS**

Add to `frontend/src/App.css` after the existing `.markdown-body img` block:

```css
.markdown-image {
  cursor: zoom-in;
}

.markdown-image-state,
.markdown-image-error {
  display: inline-flex;
  align-items: center;
  min-height: 40px;
  max-width: 100%;
  padding: 8px 10px;
  margin: 1em 0;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  color: var(--text-secondary);
  background: var(--bg-secondary);
  font-size: 13px;
  overflow-wrap: anywhere;
}

.markdown-image-error {
  color: #f85149;
}

.image-lightbox {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2.5vh 2.5vw;
  background: rgba(0, 0, 0, 0.86);
  cursor: zoom-out;
}

.image-lightbox-img {
  max-width: 95vw;
  max-height: 95vh;
  object-fit: contain;
  border-radius: 6px;
  cursor: default;
}
```

- [ ] **Step 6: Run frontend tests and build**

Run:

```powershell
cd frontend
npm test -- imagePaths
npm run build
```

Expected:

```text
Test Files  1 passed
Tests  9 passed
✓ built in ...
```

- [ ] **Step 7: Commit frontend wiring**

Run:

```powershell
git add frontend/src/App.tsx frontend/src/App.css
git commit -m "feat: render local markdown images"
```

---

### Task 9: End-to-End Verification

**Files:**
- Create: `manual-test/local-images/index.md`
- Create: `manual-test/local-images/images/sample.svg`
- Create: `manual-test/local-images/parent.png`

- [ ] **Step 1: Create manual test files**

Create `manual-test/local-images/index.md`:

```markdown
# Local Image Test

Relative:

![Relative SVG](./images/sample.svg)

Parent relative:

![Parent PNG](../local-images/parent.png)

Broken:

![Broken](./missing.png)
```

Create `manual-test/local-images/images/sample.svg`:

```xml
<svg xmlns="http://www.w3.org/2000/svg" width="240" height="120" viewBox="0 0 240 120">
  <rect width="240" height="120" fill="#58a6ff"/>
  <text x="120" y="68" text-anchor="middle" font-family="Arial" font-size="22" fill="#0d1117">SVG OK</text>
</svg>
```

Create `manual-test/local-images/parent.png` by copying an existing PNG:

```powershell
Copy-Item -LiteralPath "frontend/src/assets/images/logo-universal.png" -Destination "manual-test/local-images/parent.png"
```

- [ ] **Step 2: Run all automated checks**

Run:

```powershell
go test ./...
cd frontend
npm test -- imagePaths
npm run build
cd ..
```

Expected:

```text
ok  	tmp_mdviewer	...
Test Files  1 passed
Tests  9 passed
✓ built in ...
```

- [ ] **Step 3: Run app for manual check**

Run:

```powershell
wails dev
```

Manual expected result:
- Open `D:\AI\MarkdownViewer\manual-test\local-images`.
- Select `index.md`.
- Relative SVG is visible.
- Parent PNG is visible.
- Broken image renders the error box.
- Clicking visible images opens the lightbox.
- Escape closes the lightbox.
- Overlay click closes the lightbox.

- [ ] **Step 4: Remove manual test files if they are not desired in repo**

Run:

```powershell
Remove-Item -LiteralPath "manual-test" -Recurse
```

- [ ] **Step 5: Commit verification cleanup or fixtures**

If `manual-test` was removed:

```powershell
git status --short
```

Expected:

```text
```

If keeping fixtures:

```powershell
git add manual-test
git commit -m "test: add local image manual fixtures"
```

---

### Task 10: Final Push

**Files:**
- No code changes

- [ ] **Step 1: Review commit history**

Run:

```powershell
git log --oneline --decorate -5
git status --short
```

Expected:

```text
<latest commits listed>
```

and clean status:

```text
```

- [ ] **Step 2: Push only when remote history is safe**

If Task 1 showed empty remote:

```powershell
git push -u origin main
```

Expected:

```text
branch 'main' set up to track 'origin/main'
```

If Task 1 showed remote branches:

```powershell
git fetch origin
git branch -r
```

Expected includes:

```text
origin/main
```

Stop and decide merge/rebase/import strategy before pushing. Do not force-push.

---

## Final Verification Checklist

- [ ] `git status --short` is clean.
- [ ] `go test ./...` passes.
- [ ] `cd frontend; npm test -- imagePaths` passes.
- [ ] `cd frontend; npm run build` passes.
- [ ] Wails app manually shows relative local images.
- [ ] Wails app manually shows absolute local images.
- [ ] Wails app manually shows `file://` local images.
- [ ] Broken images do not break the document.
- [ ] Lightbox opens on image click.
- [ ] Lightbox closes on Escape and overlay click.

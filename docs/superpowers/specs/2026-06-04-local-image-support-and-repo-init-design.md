# Local Image Support and Repository Init Design

## Goal

Add reliable local image rendering to the Markdown viewer and make repository initialization the first implementation task.

Primary image support:
- Markdown image links relative to the active `.md` file.
- Absolute local paths.
- `file://` URLs.
- Existing remote `http`, `https`, and `data:` images remain browser-rendered.

Primary UX:
- Inline image rendering inside the Markdown document.
- Click image to open a lightbox.
- Escape or overlay click closes the lightbox.

## Phase 0: Repository Init

Before feature implementation, initialize this project for:

`https://github.com/WackyGameStudio/Markdown-Viewer`

Expected steps:
1. Confirm whether the remote repository is empty or already has history.
2. Initialize local git repository if `.git` is absent.
3. Add remote `origin` pointing to `https://github.com/WackyGameStudio/Markdown-Viewer.git`.
4. Decide branch name, defaulting to `main`.
5. Create an initial commit from the current project state.
6. Push to GitHub only after confirming credentials and remote history safety.

Safety rules:
- Do not force-push unless explicitly approved.
- If the GitHub repo has existing commits, fetch first and choose merge/rebase/import strategy before pushing.
- Do not commit generated dependency directories unless intentionally accepted. Current project includes `frontend/node_modules`; review before initial commit.
- Review existing release artifacts such as `mdviewer*.exe` and `mdviewer*.zip` before initial commit.

## Image Path Resolution

The frontend receives raw Markdown `img.src`.

Supported local formats:
- `./img.png`
- `../assets/img.jpg`
- `images/img.webp`
- `D:\docs\img.png`
- `C:/docs/img.png`
- `file:///D:/docs/img.png`

Resolution rules:
- URL-decode the source.
- For relative paths, resolve from the active Markdown file directory.
- For absolute paths, use the path directly.
- For `file://`, convert to a local filesystem path.
- Normalize path separators for Windows.
- Leave `http:`, `https:`, and `data:` unchanged.

Supported image extensions:
- `.png`
- `.jpg`
- `.jpeg`
- `.gif`
- `.webp`
- `.svg`
- `.bmp`

## Backend API

Add a Go API:

```go
func (a *App) GetImageData(imagePath string) (*ImageData, error)
```

Return shape:

```go
type ImageData struct {
	DataURL  string `json:"dataUrl"`
	MimeType string `json:"mimeType"`
	Size     int64  `json:"size"`
}
```

Behavior:
- Reject unsupported extensions.
- Verify the path exists.
- Verify the path is a regular file.
- Enforce a max file size, default 20 MB.
- Read file bytes.
- Detect MIME by extension.
- Return `data:<mime>;base64,<payload>`.

Reason for backend API:
- WebView may block direct local file loading.
- Backend reading avoids relying on browser `file://` policy.
- Data URLs make inline rendering and lightbox reuse straightforward.

Trade-off:
- Data URLs increase memory usage for large images.
- Size limit prevents worst-case memory spikes.

## Frontend Rendering

Add a Markdown image component override:

```tsx
components={{
  img: MarkdownImage
}}
```

`MarkdownImage` responsibilities:
- Accept `src`, `alt`, `title`.
- Detect remote/data images and render unchanged.
- Resolve local paths using `activeFile`.
- Call `GetImageData(resolvedPath)`.
- Store loading, success, and error state per image.
- Render returned `dataUrl`.
- On click, open lightbox with the same image.

Failure UI:
- Show alt text or original source in a compact broken-image placeholder.
- Log warning to console with the resolved path and error.

## Lightbox

Behavior:
- Opens when a successfully loaded image is clicked.
- Uses a full-window overlay.
- Centers the image.
- Preserves original aspect ratio.
- Closes on overlay click.
- Closes on Escape.

Sizing:
- `max-width: 95vw`
- `max-height: 95vh`
- `object-fit: contain`

## Error Handling

Expected failures:
- Unsupported extension.
- Missing file.
- Directory passed instead of file.
- File larger than size limit.
- Malformed `file://` URL.
- Permission denied.

Handling:
- Do not crash Markdown rendering.
- Keep rendering the rest of the document.
- Show local placeholder for the failed image.
- Log the technical error to console.

## Testing

Backend tests:
- Valid PNG returns `image/png` data URL.
- Valid JPG/JPEG returns `image/jpeg` data URL.
- Valid SVG returns `image/svg+xml` data URL.
- Unsupported extension returns error.
- Missing file returns error.
- Directory path returns error.
- Oversized file returns error.

Frontend/manual tests:
- `![x](./img.png)` resolves relative to current `.md`.
- `![x](../img.png)` resolves parent directory path.
- `![x](D:\path\img.png)` loads absolute Windows path.
- `![x](file:///D:/path/img.png)` loads file URL.
- Remote image still renders directly.
- Broken image shows placeholder.
- Image click opens lightbox.
- Escape closes lightbox.
- Overlay click closes lightbox.

## Out of Scope

- Obsidian `![[image.png]]` syntax.
- Image editing.
- File/folder open buttons from image UI.
- PDF export.
- Full-text search.
- Link graph/backlinks.

## Open Risks

- Data URL memory cost for many images in one document.
- SVG script/security behavior should be considered if untrusted files are opened.
- Initial GitHub push depends on credentials and remote repository state.

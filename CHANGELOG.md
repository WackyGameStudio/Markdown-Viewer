# Changelog

## [0.1.0] - 2026-06-05

### Added

- Initial Windows release.
- Folder-based Markdown explorer.
- Recent folders and folder bookmarks.
- Markdown rendering with GFM and code highlighting.
- Mermaid diagram rendering.
- Table of contents sidebar.
- Scroll position restoration.
- Local `.md` link navigation.
- Local image rendering for relative paths, absolute Windows paths, and `file://` URLs.
- Image lightbox.
- Broken image placeholder.
- Backend image loading API with MIME allowlist and 20 MB limit.
- Path resolver tests for local image handling.

### Changed

- Preserved raw Markdown image `src` values so `file://` and absolute image paths reach the image loader.

### Known Issues

- Wails CLI `v2.12.0` prints a warning because `go.mod` uses Wails `v2.11.0`.
- Vite emits a large chunk warning.
- `npm audit` reports dependency vulnerabilities.
- SVG/data images assume trusted local input.

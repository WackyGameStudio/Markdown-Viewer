# Markdown Viewer

Wails 기반 Windows용 로컬 Markdown 뷰어입니다. 폴더를 열고 `.md` 파일을 선택해 빠르게 문서를 탐색합니다.

## 주요 기능

- 폴더 기반 Markdown 파일 탐색
- 최근 폴더 저장
- 폴더 북마크
- Markdown 렌더링: GFM, table, code highlight
- Mermaid diagram 렌더링
- 문서 목차(TOC)
- 문서별 scroll position 복원
- mouse back/forward navigation
- 로컬 `.md` 링크 이동
- 로컬 이미지 표시
  - relative path: `![img](./image.png)`
  - absolute path: `![img](D:/docs/image.png)`
  - file URL: `![img](file:///D:/docs/image.png)`
- 이미지 클릭 lightbox
- broken image placeholder

## 다운로드

0.1.0 Windows 빌드:

- `release/MarkdownViewer-0.1.0-windows-amd64.exe`

## 개발

필수 도구:

- Go
- Node.js/npm
- Wails CLI

```powershell
npm install --prefix frontend
wails dev
```

## 빌드

```powershell
wails build -skipbindings
```

빌드 산출물:

```text
build/bin/MarkdownViewer.exe
```

## 테스트

```powershell
go test -count=1 ./...
npm test --prefix frontend -- imagePaths
npm run build --prefix frontend
```

## 릴리스

현재 릴리스: `v0.1.0`

변경 내역은 [CHANGELOG.md](./CHANGELOG.md)를 확인하세요.

export type ViewerLanguage = 'ko' | 'en';

export interface ViewerStrings {
  selectDocument: string;
  imageLoadError: string;
  diagramTooLarge: string;
  mermaidError: string;
  renderingDiagram: string;
  image: string;
  zoomOut: string;
  zoomIn: string;
  fit: string;
  fitWidth: string;
  originalSize: string;
  rotateClockwise: string;
  previousPage: string;
  pageNumber: string;
  nextPage: string;
  pdfOpenError: string;
  analyzingPdf: string;
  renderingPage: string;
  passwordProtectedPdf: string;
  incorrectPassword: string;
  enterPdfPassword: string;
  pdfPassword: string;
  open: string;
  wordDocument: string;
  analyzingWord: string;
  wordOpenError: string;
  powerpointDocument: string;
  previousSlide: string;
  nextSlide: string;
  analyzingPowerpoint: string;
  powerpointOpenError: string;
  legacyOffice: (format: string) => string;
  enlargedImage: string;
  close: string;
  fileReadFailed: (status: number) => string;
  fileTooLarge: string;
  loadingCancelled: string;
  unknownError: string;
}

export const viewerStrings: Record<ViewerLanguage, ViewerStrings> = {
  ko: {
    selectDocument: '문서를 선택하세요.',
    imageLoadError: '이미지를 불러올 수 없습니다',
    diagramTooLarge: '다이어그램이 너무 큽니다.',
    mermaidError: 'Mermaid 오류',
    renderingDiagram: '다이어그램을 그리는 중…',
    image: '이미지',
    zoomOut: '축소',
    zoomIn: '확대',
    fit: '맞춤',
    fitWidth: '너비 맞춤',
    originalSize: '원래 크기',
    rotateClockwise: '시계 방향 회전',
    previousPage: '이전 페이지',
    pageNumber: '페이지 번호',
    nextPage: '다음 페이지',
    pdfOpenError: 'PDF를 열 수 없습니다',
    analyzingPdf: 'PDF를 분석하는 중…',
    renderingPage: '페이지를 그리는 중…',
    passwordProtectedPdf: '암호가 필요한 PDF입니다',
    incorrectPassword: '암호가 맞지 않습니다.',
    enterPdfPassword: 'PDF 암호를 입력하세요.',
    pdfPassword: 'PDF 암호',
    open: '열기',
    wordDocument: 'Word 문서',
    analyzingWord: 'Word 문서를 분석하는 중…',
    wordOpenError: 'Word 문서를 열 수 없습니다',
    powerpointDocument: 'PowerPoint 문서',
    previousSlide: '이전 슬라이드',
    nextSlide: '다음 슬라이드',
    analyzingPowerpoint: 'PowerPoint 문서를 분석하는 중…',
    powerpointOpenError: 'PowerPoint 문서를 열 수 없습니다',
    legacyOffice: (format) =>
      `구형 .${format.toLowerCase()} 형식은 앱 내부 미리보기를 지원하지 않습니다. 우측 상단의 외부 앱으로 열기 버튼을 사용하세요.`,
    enlargedImage: '이미지 크게 보기',
    close: '닫기',
    fileReadFailed: (status) => `파일을 읽지 못했습니다 (${status})`,
    fileTooLarge: '파일이 60MB 제한을 초과합니다.',
    loadingCancelled: '불러오기가 취소되었습니다.',
    unknownError: '알 수 없는 오류',
  },
  en: {
    selectDocument: 'Select a document.',
    imageLoadError: 'Could not load image',
    diagramTooLarge: 'The diagram is too large.',
    mermaidError: 'Mermaid error',
    renderingDiagram: 'Rendering diagram…',
    image: 'Image',
    zoomOut: 'Zoom out',
    zoomIn: 'Zoom in',
    fit: 'Fit',
    fitWidth: 'Fit width',
    originalSize: 'Original size',
    rotateClockwise: 'Rotate clockwise',
    previousPage: 'Previous page',
    pageNumber: 'Page number',
    nextPage: 'Next page',
    pdfOpenError: 'Could not open PDF',
    analyzingPdf: 'Analyzing PDF…',
    renderingPage: 'Rendering page…',
    passwordProtectedPdf: 'This PDF requires a password',
    incorrectPassword: 'The password is incorrect.',
    enterPdfPassword: 'Enter the PDF password.',
    pdfPassword: 'PDF password',
    open: 'Open',
    wordDocument: 'Word document',
    analyzingWord: 'Analyzing Word document…',
    wordOpenError: 'Could not open Word document',
    powerpointDocument: 'PowerPoint document',
    previousSlide: 'Previous slide',
    nextSlide: 'Next slide',
    analyzingPowerpoint: 'Analyzing PowerPoint document…',
    powerpointOpenError: 'Could not open PowerPoint document',
    legacyOffice: (format) =>
      `The legacy .${format.toLowerCase()} format cannot be previewed in the app. Use the open-in-another-app button at the top right.`,
    enlargedImage: 'Enlarged image',
    close: 'Close',
    fileReadFailed: (status) => `Could not read file (${status})`,
    fileTooLarge: 'The file exceeds the 60 MB limit.',
    loadingCancelled: 'Loading was cancelled.',
    unknownError: 'Unknown error',
  },
};

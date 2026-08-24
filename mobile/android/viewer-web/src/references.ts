export type DocumentKind =
  | 'markdown'
  | 'image'
  | 'pdf'
  | 'video'
  | 'word'
  | 'presentation'
  | 'html';

export function documentKindFromReference(reference: string): DocumentKind | null {
  const path = reference.split(/[?#]/, 1)[0].toLowerCase();
  if (path.endsWith('.md')) return 'markdown';
  if (/\.(png|jpe?g|gif|webp|svg|bmp)$/.test(path)) return 'image';
  if (path.endsWith('.pdf')) return 'pdf';
  if (/\.(mp4|m4v|webm|mkv|mov)$/.test(path)) return 'video';
  if (/\.(doc|docx)$/.test(path)) return 'word';
  if (/\.(ppt|pptx)$/.test(path)) return 'presentation';
  if (/\.(html?|xhtml)$/.test(path)) return 'html';
  return null;
}

export function isExternalReference(reference: string): boolean {
  return /^(https?:|mailto:)/i.test(reference) || reference.startsWith('//');
}

export function isDirectImageSource(reference: string): boolean {
  return /^(https?:|data:|blob:)/i.test(reference) || reference.startsWith('//');
}

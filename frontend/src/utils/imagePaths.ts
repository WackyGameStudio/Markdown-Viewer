const directSourcePrefixes = ['http://', 'https://', 'data:'];

export function isDirectImageSource(src?: string): boolean {
  const trimmed = src?.trim();
  if (!trimmed) return true;

  const lower = trimmed.toLowerCase();
  return directSourcePrefixes.some((prefix) => lower.startsWith(prefix));
}

export function resolveLocalImagePath(src: string, activeFile: string): string {
  const trimmedSrc = src.trim();
  if (!trimmedSrc) {
    throw new Error('Image source is empty');
  }

  const trimmedActiveFile = activeFile.trim();
  if (!trimmedActiveFile) {
    throw new Error('Active markdown file is empty');
  }

  if (trimmedSrc.toLowerCase().startsWith('file://')) {
    return fileUrlToWindowsPath(trimmedSrc);
  }

  const sourcePath = toWindowsPath(trimmedSrc);
  if (isDriveAbsolutePath(sourcePath) || isUncPath(sourcePath)) {
    return normalizeWindowsPath(sourcePath);
  }

  const activePath = trimmedActiveFile.toLowerCase().startsWith('file://')
    ? fileUrlToWindowsPath(trimmedActiveFile)
    : toWindowsPath(trimmedActiveFile);
  const activeDir = getDirectoryName(activePath);

  return normalizeWindowsPath(joinWindowsPath(activeDir, sourcePath));
}

function fileUrlToWindowsPath(src: string): string {
  let url: URL;
  try {
    url = new URL(src);
  } catch {
    throw new Error('Invalid file URL');
  }

  if (url.protocol.toLowerCase() !== 'file:') {
    throw new Error('Invalid file URL');
  }

  const pathname = safeDecode(url.pathname);
  if (url.host) {
    const hostPath = pathname.replace(/^\/+/, '');
    if (!hostPath) {
      throw new Error('Invalid file URL');
    }

    return normalizeWindowsPath(`\\\\${url.host}\\${hostPath}`);
  }

  if (!pathname || pathname === '/') {
    throw new Error('Invalid file URL');
  }

  const pathWithoutDriveSlash = pathname.replace(/^\/(?=[A-Za-z]:)/, '');
  return normalizeWindowsPath(pathWithoutDriveSlash);
}

function toWindowsPath(path: string): string {
  return safeDecode(path).replace(/\//g, '\\');
}

function safeDecode(value: string): string {
  try {
    return decodeURIComponent(value);
  } catch {
    return value;
  }
}

function isDriveAbsolutePath(path: string): boolean {
  return /^[A-Za-z]:[\\/]/.test(path);
}

function isUncPath(path: string): boolean {
  return /^[\\/]{2}[^\\/]+[\\/]+[^\\/]+/.test(path);
}

function getDirectoryName(path: string): string {
  const normalized = normalizeWindowsPath(path);
  const lastSeparator = normalized.lastIndexOf('\\');

  if (lastSeparator < 0) {
    return '';
  }

  if (/^[A-Za-z]:\\/.test(normalized) && lastSeparator === 2) {
    return normalized.slice(0, 3);
  }

  return normalized.slice(0, lastSeparator);
}

function joinWindowsPath(base: string, path: string): string {
  if (!base) return path;
  if (base.endsWith('\\')) return `${base}${path}`;
  return `${base}\\${path}`;
}

function normalizeWindowsPath(path: string): string {
  const windowsPath = path.replace(/\//g, '\\');

  if (isUncPath(windowsPath)) {
    return normalizeUncPath(windowsPath);
  }

  const driveMatch = windowsPath.match(/^([A-Za-z]:)\\*(.*)$/);
  if (driveMatch) {
    const [, drive, rest] = driveMatch;
    const segments = normalizeSegments(rest.split(/\\+/), true);
    return segments.length > 0 ? `${drive}\\${segments.join('\\')}` : `${drive}\\`;
  }

  const rooted = windowsPath.startsWith('\\');
  const segments = normalizeSegments(windowsPath.split(/\\+/), rooted);
  return `${rooted ? '\\' : ''}${segments.join('\\')}`;
}

function normalizeUncPath(path: string): string {
  const parts = path.replace(/^\\+/, '').split(/\\+/);
  const [server, share, ...rest] = parts;
  const segments = normalizeSegments(rest, true);

  return `\\\\${server}\\${share}${segments.length > 0 ? `\\${segments.join('\\')}` : ''}`;
}

function normalizeSegments(segments: string[], rooted: boolean): string[] {
  const normalized: string[] = [];

  for (const segment of segments) {
    if (!segment || segment === '.') {
      continue;
    }

    if (segment === '..') {
      if (normalized.length > 0 && normalized[normalized.length - 1] !== '..') {
        normalized.pop();
      } else if (!rooted) {
        normalized.push(segment);
      }
      continue;
    }

    normalized.push(segment);
  }

  return normalized;
}

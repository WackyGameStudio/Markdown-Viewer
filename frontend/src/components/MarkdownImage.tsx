import { useEffect, useState, type ImgHTMLAttributes } from 'react';
import { GetImageData } from '../../wailsjs/go/main/App';
import { isDirectImageSource, resolveLocalImagePath } from '../utils/imagePaths';

interface MarkdownImageProps extends ImgHTMLAttributes<HTMLImageElement> {
  activeFile: string;
  onOpenLightbox: (src: string, alt: string) => void;
}

type ImageState = 'ready' | 'loading' | 'error';

function getFallbackText(src?: string, alt?: string) {
  return alt || src || 'Image failed to load';
}

function getImageClassName(className?: string) {
  return ['markdown-image', className].filter(Boolean).join(' ');
}

function MarkdownImage({
  src,
  alt,
  activeFile,
  onOpenLightbox,
  className,
  onClick,
  ...props
}: MarkdownImageProps) {
  const [imageSrc, setImageSrc] = useState(src || '');
  const [imageState, setImageState] = useState<ImageState>(
    isDirectImageSource(src) ? 'ready' : 'loading'
  );

  useEffect(() => {
    if (isDirectImageSource(src)) {
      setImageSrc(src || '');
      setImageState('ready');
      return;
    }

    let isCancelled = false;
    const source = src || '';
    let resolvedPath = '';

    setImageState('loading');

    try {
      resolvedPath = resolveLocalImagePath(source, activeFile);
    } catch (error) {
      console.warn('Failed to resolve markdown image', { source, path: resolvedPath, error });
      setImageState('error');
      return;
    }

    GetImageData(resolvedPath)
      .then((image) => {
        if (isCancelled) return;
        setImageSrc(image.dataUrl);
        setImageState('ready');
      })
      .catch((error) => {
        if (isCancelled) return;
        console.warn('Failed to load markdown image', { source, path: resolvedPath, error });
        setImageState('error');
      });

    return () => {
      isCancelled = true;
    };
  }, [activeFile, src]);

  if (imageState === 'loading') {
    return <span className="markdown-image-state">Loading image...</span>;
  }

  if (imageState === 'error') {
    return (
      <span className="markdown-image-state markdown-image-error">
        {getFallbackText(src, alt)}
      </span>
    );
  }

  return (
    <img
      {...props}
      src={imageSrc}
      alt={alt}
      className={getImageClassName(className)}
      onClick={(event) => {
        onClick?.(event);
        if (!event.defaultPrevented) {
          onOpenLightbox(imageSrc, alt || '');
        }
      }}
    />
  );
}

export default MarkdownImage;

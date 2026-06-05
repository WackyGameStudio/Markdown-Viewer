import { useState, useEffect, useLayoutEffect, useRef } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import rehypeHighlight from 'rehype-highlight';
import { Folder, FolderOpen, FileText, Search, Bookmark, BookmarkCheck, ChevronDown, History, RefreshCw, SlidersHorizontal } from 'lucide-react';
import Mermaid from './components/Mermaid';
import MarkdownImage from './components/MarkdownImage';
import ImageLightbox from './components/ImageLightbox';

import { OpenDirectory, GetMarkdownTree, GetMarkdownContent } from "../wailsjs/go/main/App";
import { main } from "../wailsjs/go/models";

import './style.css';
import './App.css';

interface TOCItem {
  id: string;
  text: string;
  level: number;
}

interface BookmarkItem {
  path: string;
  name: string;
}

// LocalStorage Keys
const STORE_RECENT_FOLDERS = 'mdv_recent_folders';
const STORE_BOOKMARKS = 'mdv_bookmarks';
const STORE_SCROLL_POSITIONS = 'mdv_scroll_positions';
const STORE_VIEWPORT_WIDTH = 'mdv_viewport_width';

function App() {
  const [rootPath, setRootPath] = useState<string>('');
  const [fileTree, setFileTree] = useState<main.FileNode | null>(null);
  const [activeFile, setActiveFile] = useState<string>('');
  const [markdownContent, setMarkdownContent] = useState<string>('');
  const [toc, setToc] = useState<TOCItem[]>([]);
  const [expandedFolders, setExpandedFolders] = useState<Set<string>>(new Set());
  
  // Navigation History State
  const [history, setHistory] = useState<string[]>([]);
  const [historyIndex, setHistoryIndex] = useState<number>(-1);
  const isInternalChange = useRef(false);

  // Features State
  const [recentFolders, setRecentFolders] = useState<string[]>([]);
  const [bookmarks, setBookmarks] = useState<BookmarkItem[]>([]);
  const [showRecentDropdown, setShowRecentDropdown] = useState(false);
  const [showBookmarkDropdown, setShowBookmarkDropdown] = useState(false);
  const [viewportWidth, setViewportWidth] = useState<number>(850);
  const [lightboxImage, setLightboxImage] = useState<{ src: string; alt: string } | null>(null);
  
  // Keep track of scroll container
  const contentPaneRef = useRef<HTMLDivElement>(null);
  
  // [신규] 스크롤 위치를 메모리(세션)에서 관리 (영구 저장 X)
  const scrollPositionsRef = useRef<Record<string, number>>({});

  // Initialize from LocalStorage
  useEffect(() => {
    const storedRecent = localStorage.getItem(STORE_RECENT_FOLDERS);
    if (storedRecent) setRecentFolders(JSON.parse(storedRecent));

    const storedBookmarks = localStorage.getItem(STORE_BOOKMARKS);
    if (storedBookmarks) setBookmarks(JSON.parse(storedBookmarks));

    const storedWidth = localStorage.getItem(STORE_VIEWPORT_WIDTH);
    if (storedWidth) setViewportWidth(parseInt(storedWidth, 10));

    // [신규] 앱 시작 시 마지막(최근) 폴더 자동 오픈
    if (storedRecent) {
      try {
        const recents = JSON.parse(storedRecent);
        if (recents && recents.length > 0) {
          const lastFolder = recents[0];
          // 유효성 검사 (트리 로드 시도)
          GetMarkdownTree(lastFolder).then(() => {
            setRootPath(lastFolder);
          }).catch(() => {
            console.log("Last folder no longer exists or inaccessible:", lastFolder);
          });
        }
      } catch (e) {
        console.error("Failed to parse recent folders for auto-open:", e);
      }
    }
  }, []);

  // When a root folder is opened or changed
  useEffect(() => {
    if (rootPath) {
      GetMarkdownTree(rootPath).then((tree) => {
        setFileTree(tree);
        setExpandedFolders(new Set([tree.path]));
        
        // Add to recent folders
        setRecentFolders(prev => {
          const newRecent = [rootPath, ...prev.filter(p => p !== rootPath)].slice(0, 5); // Keep max 5
          localStorage.setItem(STORE_RECENT_FOLDERS, JSON.stringify(newRecent));
          return newRecent;
        });
      }).catch(err => {
        console.error("Failed to load tree:", err);
        // Possible reasons: deleted or inaccessible folder. Remove from bookmarks if exists
        checkAndRemoveInvalidBookmark(rootPath);
      });
    }
  }, [rootPath]);

  // Ref to hold the currently viewable file path so we can save its scroll position
  const currentViewedFileRef = useRef<string>('');

  // Handle active file change and scroll restorations
  useEffect(() => {
    // 1. Save scroll position of the PREVIOUS file before switching
    if (currentViewedFileRef.current) {
      saveScrollPosition(currentViewedFileRef.current);
    }
    
    // Update ref to the NEW file
    currentViewedFileRef.current = activeFile;

    // 히스토리 기록 (내부 이동인 경우 제외)
    if (activeFile && !isInternalChange.current) {
      setHistory(prev => {
        const newHistory = prev.slice(0, historyIndex + 1);
        if (newHistory[newHistory.length - 1] !== activeFile) {
          return [...newHistory, activeFile];
        }
        return prev;
      });
      setHistoryIndex(prev => {
        if (history[historyIndex] !== activeFile) {
          return prev + 1;
        }
        return prev;
      });
    }
    isInternalChange.current = false;

    // 2. Load new file content
    if (activeFile) {
      // 강제 스크롤 초기화 (새 파일을 불러오기 전에 미리 0으로 세팅)
      if (contentPaneRef.current) {
         contentPaneRef.current.scrollTop = 0;
      }

      GetMarkdownContent(activeFile).then(content => {
        setMarkdownContent(content);
        extractTOC(content);
        // [수정] 여기서 setTimeout을 쓰면 렌더링 후 점프하는 게 보이므로, 
        // useLayoutEffect에서 처리하도록 위임합니다.
      }).catch(err => {
        console.error("Failed to load markdown content:", err);
      });
    } else {
      setMarkdownContent('');
      setToc([]);
    }
  }, [activeFile]);

  // [신규] 콘텐츠가 업데이트된 직후(브라우저가 그리기 전) 스크롤 위치를 즉각 복원
  useLayoutEffect(() => {
    if (activeFile && markdownContent) {
      restoreScrollPosition(activeFile);
    }
  }, [markdownContent]);

  // 마우스 앞/뒤 버튼 감지
  useEffect(() => {
    const handleMouseButtons = (e: MouseEvent) => {
      if (e.button === 3) { // Back button
        handleGoBack();
      } else if (e.button === 4) { // Forward button
        handleGoForward();
      }
    };
    
    window.addEventListener('mousedown', handleMouseButtons);
    return () => window.removeEventListener('mousedown', handleMouseButtons);
  }, [history, historyIndex]);

  const handleGoBack = () => {
    if (historyIndex > 0) {
      isInternalChange.current = true;
      const prevFile = history[historyIndex - 1];
      setHistoryIndex(historyIndex - 1);
      setActiveFile(prevFile);
    }
  };

  const handleGoForward = () => {
    if (historyIndex < history.length - 1) {
      isInternalChange.current = true;
      const nextFile = history[historyIndex + 1];
      setHistoryIndex(historyIndex + 1);
      setActiveFile(nextFile);
    }
  };

  // Scroll Position Helpers
  const saveScrollPosition = (filePath: string) => {
    if (!filePath || !contentPaneRef.current) return;
    scrollPositionsRef.current[filePath] = contentPaneRef.current.scrollTop;
  };

  const restoreScrollPosition = (filePath: string) => {
    if (!filePath || !contentPaneRef.current) return;
    
    const savedPos = scrollPositionsRef.current[filePath];
    if (savedPos !== undefined && savedPos > 0) {
      // 직접 scrollTop 설정하여 한 순간에 딱 이동 (사용자 요청: 빠르게)
      contentPaneRef.current.scrollTop = savedPos;
    } else {
      contentPaneRef.current.scrollTop = 0;
    }
  };

  const extractTOC = (content: string) => {
    const headingRegex = /^(#{1,6})\s+(.+)$/gm;
    let match;
    const extractedToc: TOCItem[] = [];
    let idCounter = 0;

    while ((match = headingRegex.exec(content)) !== null) {
      const level = match[1].length;
      const text = match[2];
      const id = `heading-${idCounter++}`;
      extractedToc.push({ id, text, level });
    }
    setToc(extractedToc);
  };

  const handleOpenFolder = () => {
    OpenDirectory().then(dir => {
      if (dir) {
        setRootPath(dir);
        setActiveFile('');
        setHistory([]); // 폴더 새로 열면 히스토리 리셋
        setHistoryIndex(-1);
        scrollPositionsRef.current = {}; // 폴더 새로 열면 스크롤 기록 초기화
      }
    });
  };
  
  const handleRefreshFolder = () => {
    if (!rootPath) return;
    GetMarkdownTree(rootPath).then((tree) => {
      setFileTree(tree);
    }).catch(err => {
      console.error("Failed to refresh tree:", err);
    });
  };

  const handleOpenRecent = (path: string) => {
    setRootPath(path);
    setActiveFile('');
    setShowRecentDropdown(false);
    setHistory([]); // 폴더 전환 시 히스토리 리셋
    setHistoryIndex(-1);
    scrollPositionsRef.current = {}; // 폴더 전환 시 스크롤 기록 초기화
  };

  // Bookmarks Logic (Folder Level)
  const toggleBookmark = () => {
    if (!rootPath) return; // 폴더가 열려있지 않으면 무시
    
    const folderName = rootPath.split(/[\\/]/).pop() || rootPath;
    
    setBookmarks(prev => {
      let newBookmarks;
      if (prev.some(b => b.path === rootPath)) {
        newBookmarks = prev.filter(b => b.path !== rootPath); // Remove
      } else {
        newBookmarks = [...prev, { path: rootPath, name: folderName }]; // Add
      }
      localStorage.setItem(STORE_BOOKMARKS, JSON.stringify(newBookmarks));
      return newBookmarks;
    });
  };

  const isBookmarked = (path: string) => bookmarks.some(b => b.path === path);

  const handleOpenBookmark = (bookmarkPath: string) => {
    // 북마크 클릭 시 해당 '폴더'를 오픈
    setRootPath(bookmarkPath);
    setActiveFile(''); // 기존 열려있던 파일은 닫음
    setShowBookmarkDropdown(false);
    setHistory([]); // 폴더 전환 시 히스토리 리셋
    setHistoryIndex(-1);
    scrollPositionsRef.current = {}; // 폴더 전환 시 스크롤 기록 초기화
  };

  // 폴더가 삭제되었거나 접근 불가능할 때 북마크 목록에서 제거
  // (현재는 tree로드 catch문 안에 들어가있음)
  const checkAndRemoveInvalidBookmark = (invalidPath: string) => {
    if (isBookmarked(invalidPath)) {
      alert(`폴더를 찾을 수 없거나 열 수 없습니다.\n북마크에서 제거됩니다: ${invalidPath}`);
      setBookmarks(prev => {
        const next = prev.filter(b => b.path !== invalidPath);
        localStorage.setItem(STORE_BOOKMARKS, JSON.stringify(next));
        return next;
      });
      // 이미 setRootPath로 시도하면서 트리가 빈 상태일 것이므로,
      // 별도의 추가 상태조작은 불필요하지만 안전을 위해 rootPath 리셋
      setRootPath('');
      setFileTree(null);
    }
  };

  const toggleSidebarFolder = (path: string) => {
    setExpandedFolders(prev => {
      const next = new Set(prev);
      if (next.has(path)) {
        next.delete(path);
      } else {
        next.add(path);
      }
      return next;
    });
  };

  const renderTree = (node: main.FileNode) => {
    const isExpanded = expandedFolders.has(node.path);
    const isActive = activeFile === node.path;

    return (
      <div key={node.path}>
        <div 
          className={`tree-node ${isActive ? 'active' : ''}`}
          onClick={() => {
            if (node.isDir) {
              toggleSidebarFolder(node.path);
            } else {
              setActiveFile(node.path);
            }
          }}
        >
          <div className="tree-node-item">
            {node.isDir ? (
               isExpanded ? <FolderOpen size={16} className="icon"/> : <Folder size={16} className="icon"/>
            ) : (
               <FileText size={16} className="icon"/>
            )}
            <span 
              onMouseEnter={(e) => {
                const target = e.currentTarget;
                if (target.scrollWidth > target.clientWidth) {
                  target.title = node.name;
                } else {
                  target.title = "";
                }
              }}
            >
              {node.name}
            </span>
          </div>
        </div>
        
        {node.isDir && isExpanded && node.children && (
          <div className="tree-children">
            {node.children.map(child => renderTree(child))}
          </div>
        )}
      </div>
    );
  };

  const scrollToHeading = (id: string, text: string) => {
    const elements = document.querySelectorAll('.markdown-body h1, .markdown-body h2, .markdown-body h3, .markdown-body h4, .markdown-body h5, .markdown-body h6');
    for (let i = 0; i < elements.length; i++) {
      if (elements[i].textContent === text) {
        elements[i].scrollIntoView({ behavior: 'smooth' });
        break;
      }
    }
  };

  return (
    <div className="app-container" style={{ '--viewport-width': `${viewportWidth}px` } as React.CSSProperties} onClick={() => {
        // Close dropdowns if clicking anywhere else
        if(showRecentDropdown) setShowRecentDropdown(false);
        if(showBookmarkDropdown) setShowBookmarkDropdown(false);
    }}>
      {/* Toolbar */}
      <div className="toolbar">
        <div className="brand">Markdown Viewer</div>
        
        <div className="toolbar-actions">
          {/* Viewport Width Slider */}
          <div className="slider-container" title="뷰포트 너비 조절">
            <SlidersHorizontal size={14} />
            <input 
              type="range" 
              className="viewport-slider"
              min={600} 
              max={1600} 
              step={50}
              value={viewportWidth} 
              onChange={(e) => {
                const width = parseInt(e.target.value, 10);
                setViewportWidth(width);
                localStorage.setItem(STORE_VIEWPORT_WIDTH, width.toString());
              }} 
            />
          </div>

          {/* Recent Folders Dropdown */}
          <div className="dropdown-container">
            <button className="btn-toolbar" onClick={(e) => { e.stopPropagation(); setShowRecentDropdown(!showRecentDropdown); setShowBookmarkDropdown(false); }}>
              <History size={16} /> Recent <ChevronDown size={14} />
            </button>
            {showRecentDropdown && (
              <div className="dropdown-menu">
                {recentFolders.length > 0 ? recentFolders.map((path, idx) => (
                  <div key={idx} className="dropdown-item" onClick={() => handleOpenRecent(path)} title={path}>
                    <span className="dropdown-item-title">{path.split(/[\\/]/).pop() || path}</span>
                    <span className="dropdown-hint">{path}</span>
                  </div>
                )) : (
                  <div className="dropdown-empty">No recent folders</div>
                )}
              </div>
            )}
          </div>

          {/* Bookmarks Dropdown */}
          <div className="dropdown-container">
            <button className="btn-toolbar" onClick={(e) => { e.stopPropagation(); setShowBookmarkDropdown(!showBookmarkDropdown); setShowRecentDropdown(false); }}>
              <Bookmark size={16} /> Bookmarks <ChevronDown size={14} />
            </button>
            {showBookmarkDropdown && (
              <div className="dropdown-menu">
                {bookmarks.length > 0 ? bookmarks.map((b, idx) => (
                  <div key={idx} className="dropdown-item" onClick={() => handleOpenBookmark(b.path)} title={b.path}>
                    <BookmarkCheck size={14} className="icon-tiny" /> 
                    <span className="dropdown-item-title">{b.name}</span>
                    <span className="dropdown-hint">{b.path}</span>
                  </div>
                )) : (
                  <div className="dropdown-empty">No bookmarks</div>
                )}
              </div>
            )}
          </div>

          <button className="btn-open" onClick={handleOpenFolder}>
            <FolderOpen size={18} />
            Open Folder
          </button>
        </div>
      </div>

      {/* Main Layout */}
      <div className="main-content-layout">
        <div className="sidebar-wrapper">
          <div className="sidebar">
        <div className="sidebar-header">
          <div className="sidebar-header-content">
            <span>EXPLORER</span>
            {rootPath && (
              <div style={{ display: 'flex', gap: '4px' }}>
                <button 
                  className="btn-icon" 
                  onClick={handleRefreshFolder} 
                  title="Refresh Folder"
                  style={{ padding: '4px' }}
                >
                  <RefreshCw size={20} />
                </button>
                <button 
                  className="btn-icon" 
                  onClick={toggleBookmark} 
                  title={isBookmarked(rootPath) ? "Remove Bookmark" : "Bookmark this Folder"}
                  style={{ padding: '4px' }}
                >
                  {isBookmarked(rootPath) ? <BookmarkCheck size={20} className="bookmarked-icon" /> : <Bookmark size={20} />}
                </button>
              </div>
            )}
          </div>
        </div>
        <div className="tree-container">
          {fileTree ? renderTree(fileTree) : (
             <div style={{color: 'var(--text-secondary)', padding: '10px 20px', fontSize: '13px'}}>
               No folder opened.
             </div>
          )}
        </div>
          </div>
        </div>

        <div className="content-pane" id="main-content" ref={contentPaneRef}>
          {activeFile && markdownContent ? (
          <div className="markdown-container">
            {/* Action Bar for Active File - Now simplified */}
            <div className="content-action-bar" style={{ padding: '0', border: 'none', background: 'transparent', minHeight: '0', marginBottom: '10px' }}>
               {/* Breadcrumb removed as requested */}
            </div>

            <div className="markdown-body">
              <ReactMarkdown 
                 remarkPlugins={[remarkGfm]}
                 rehypePlugins={[rehypeHighlight]}
                 components={{
                    img: ({ node, ...props }) => (
                      <MarkdownImage
                        {...props}
                        activeFile={activeFile}
                        onOpenLightbox={(src, alt) => setLightboxImage({ src, alt })}
                      />
                    ),
                    code: ({ node, inline, className, children, ...props }: any) => {
                      const match = /language-(\w+)/.exec(className || '');
                      const lang = match ? match[1] : '';

                      if (!inline && lang === 'mermaid') {
                        return <Mermaid chart={String(children).replace(/\n$/, '')} />;
                      }

                      return (
                        <code className={className} {...props}>
                          {children}
                        </code>
                      );
                    },
                   a: ({ node, ...props }) => {
                     const href = props.href || '';
                     const isExternal = href.startsWith('http');
                     
                     const handleClick = (e: React.MouseEvent) => {
                       if (isExternal) return; 
                       
                       // 로컬 링크 (.md 파일만 지원)
                       const isMdFile = href.toLowerCase().endsWith('.md');
                       const isAnchor = href.startsWith('#');

                       if (isMdFile) {
                         e.preventDefault();
                         
                         let targetPath = href;
                         // URL 엔코딩된 부분 해제 (공백 등)
                         targetPath = decodeURIComponent(targetPath);
                         // 경로 정규화 (./ 제거)
                         targetPath = targetPath.replace(/^\.\//, '').replace(/^\.\\/, '');
                         
                         // 상대경로인 경우 현재 파일 위치 기준으로 절대경로 계산
                         if (!targetPath.includes(':') && !targetPath.startsWith('/') && !targetPath.startsWith('\\')) {
                            const lastSlash = activeFile.lastIndexOf('\\');
                            const lastSlashAlt = activeFile.lastIndexOf('/');
                            const slashIdx = Math.max(lastSlash, lastSlashAlt);
                            if (slashIdx !== -1) {
                              const baseDir = activeFile.substring(0, slashIdx + 1);
                              targetPath = baseDir + targetPath.replace(/\//g, '\\');
                            }
                         }

                         // 파일이 실제로 존재하는지 확인 후 이동
                         GetMarkdownContent(targetPath).then(() => {
                           setActiveFile(targetPath);
                         }).catch(() => {
                           console.log("File not accessible or not found:", targetPath);
                         });
                       } else if (isAnchor) {
                         // 앵커 링크는 기본 동작(부드러운 이동 등) 유지
                       } else {
                         // md 파일이 아닌 로컬 파일 링크는 무시 (보안 및 뷰어 목적상)
                         if (!isExternal) e.preventDefault();
                       }
                     };

                     return (
                       <a 
                         {...props} 
                         onClick={handleClick}
                         target={isExternal ? "_blank" : undefined}
                         rel={isExternal ? "noopener noreferrer" : undefined}
                       />
                     );
                   }
                 }}
              >
                {markdownContent}
              </ReactMarkdown>
            </div>
          </div>
        ) : (
          <div className="empty-state">
            <Search size={48} />
            <h2>Select a markdown file to view</h2>
            <p>Click "Open Folder" to start exploring your documents.</p>
          </div>
        )}
        </div>

        <div className="toc-sidebar-wrapper">
          <div className="toc-sidebar">
            <div className="toc-title">ON THIS PAGE</div>
            <ul className="toc-list">
          {toc.length > 0 ? toc.map((item) => (
             <li key={item.id}>
               <a 
                 className="toc-item" 
                 style={{'--level': item.level - 1} as React.CSSProperties}
                 onClick={() => scrollToHeading(item.id, item.text)}
               >
                 {item.text}
               </a>
             </li>
          )) : (
            <li style={{color: 'var(--text-secondary)', fontSize: '13px'}}>No headings found.</li>
          )}
            </ul>
          </div>
        </div>
      </div>
      {lightboxImage && (
        <ImageLightbox
          src={lightboxImage.src}
          alt={lightboxImage.alt}
          onClose={() => setLightboxImage(null)}
        />
      )}
    </div>
  )
}

export default App

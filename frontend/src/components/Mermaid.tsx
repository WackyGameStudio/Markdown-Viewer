import React, { useEffect, useRef } from 'react';
import mermaid from 'mermaid';

// Initialize mermaid
mermaid.initialize({
  startOnLoad: true,
  theme: 'dark', // 뷰어의 기본 테마에 맞춰 설정
  securityLevel: 'loose',
  fontFamily: 'inherit'
});

interface MermaidProps {
  chart: string;
}

const Mermaid: React.FC<MermaidProps> = ({ chart }) => {
  const mermaidRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (mermaidRef.current && chart) {
      // 렌더링 전 기존 내용 삭제
      mermaidRef.current.innerHTML = '';
      
      const uniqueId = `mermaid-svg-${Math.random().toString(36).substr(2, 9)}`;
      
      try {
        mermaid.render(uniqueId, chart).then(({ svg }) => {
          if (mermaidRef.current) {
            mermaidRef.current.innerHTML = svg;
          }
        });
      } catch (error) {
        console.error('Mermaid rendering error:', error);
        if (mermaidRef.current) {
          mermaidRef.current.innerHTML = `<pre style="color: #ff6b6b;">Mermaid Error: ${error}</pre>`;
        }
      }
    }
  }, [chart]);

  return <div className="mermaid-render" ref={mermaidRef} style={{ display: 'flex', justifyContent: 'center', margin: '20px 0' }} />;
};

export default Mermaid;

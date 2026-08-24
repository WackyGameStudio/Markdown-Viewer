import React from 'react';
import ReactDOM from 'react-dom/client';
import 'highlight.js/styles/github-dark.css';
import 'react-pdf/dist/Page/AnnotationLayer.css';
import 'react-pdf/dist/Page/TextLayer.css';
import './styles.css';
import { Viewer } from './viewer';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <Viewer />
  </React.StrictMode>,
);

window.AndroidBridge?.ready();

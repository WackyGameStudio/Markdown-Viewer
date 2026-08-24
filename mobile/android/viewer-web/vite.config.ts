import { createRequire } from 'node:module';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import react from '@vitejs/plugin-react';
import { defineConfig, normalizePath } from 'vite';
import { viteStaticCopy } from 'vite-plugin-static-copy';

const require = createRequire(import.meta.url);
const projectDir = path.dirname(fileURLToPath(import.meta.url));
const pdfjsDistPath = path.dirname(require.resolve('pdfjs-dist/package.json'));

export default defineConfig({
  base: './',
  plugins: [
    react(),
    viteStaticCopy({
      targets: ['cmaps', 'wasm', 'standard_fonts'].map((directory) => ({
        src: normalizePath(path.join(pdfjsDistPath, directory, '*')),
        dest: directory,
        rename: { stripBase: true },
      })),
    }),
  ],
  build: {
    target: 'es2020',
    outDir: path.resolve(projectDir, '../app/src/main/assets/viewer'),
    emptyOutDir: true,
  },
});

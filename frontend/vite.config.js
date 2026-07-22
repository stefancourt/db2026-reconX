// TICKET-ADV111 — Vite config + path aliases (@/components, @/hooks, ...)
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { fileURLToPath, URL } from 'node:url';

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@':          fileURLToPath(new URL('./src',          import.meta.url)),
      '@components':fileURLToPath(new URL('./src/components',import.meta.url)),
      '@hooks':     fileURLToPath(new URL('./src/hooks',     import.meta.url)),
      '@services':  fileURLToPath(new URL('./src/services',  import.meta.url)),
      '@pages':     fileURLToPath(new URL('./src/pages',     import.meta.url)),
      '@context':   fileURLToPath(new URL('./src/context',   import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/test-setup.js',
  },
});

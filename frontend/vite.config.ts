/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Dev server: http://localhost:5173 (and http://<laptop-ip>:5173 from the phone).
// API calls to /api are forwarded to the Spring Boot backend on port 8080.
export default defineConfig({
  plugins: [react()],
  // Game pictures are NOT bundled: the backend serves the content folder at /content/** from
  // disk, so new pictures appear after "reload content" without a rebuild (brief 12b).
  server: {
    proxy: { '/api': 'http://localhost:8080', '/content': 'http://localhost:8080' },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
  },
});

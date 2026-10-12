/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Dev server: http://localhost:5173 (and http://<laptop-ip>:5173 from the phone).
// API calls to /api are forwarded to the Spring Boot backend on port 8080.
export default defineConfig({
  plugins: [react()],
  // Sprites live in content/sprites (shared with the content tools), served at the root:
  // content/sprites/hero/hero_hacker_idle.png -> /hero/hero_hacker_idle.png
  publicDir: '../content/sprites',
  server: {
    proxy: { '/api': 'http://localhost:8080' },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
  },
});

/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// base: относительные пути, чтобы демо-сборку можно было выложить в подпапку (например, GitHub Pages)
export default defineConfig({
  plugins: [react()],
  base: './',
  server: {
    port: 5173,
    // в обычном режиме запросы /api проксируются на Spring Boot
    proxy: { '/api': 'http://localhost:8080' },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test-setup.ts'],
    css: false,
  },
})

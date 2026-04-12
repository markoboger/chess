import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      // Stockfish REST (chess-api.com) — browser-safe during dev; prod can set VITE_STOCKFISH_API_URL
      '/chess-api': {
        target: 'https://chess-api.com',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/chess-api/, ''),
      },
      '/match-runner': {
        target: 'http://localhost:8084',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/match-runner/, ''),
      },
    },
  },
  test: {
    environment: 'happy-dom',
    globals: true,
  },
})

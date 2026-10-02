import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// P0 테스트 하네스 (CLAUDE.md §4). 백엔드(8080)의 /api 와 /ws 를 프록시한다.
// 브라우저 입장에서는 같은 출처라 CORS 설정이 필요 없다.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
      '/ws': { target: 'ws://localhost:8080', ws: true },
    },
  },
})

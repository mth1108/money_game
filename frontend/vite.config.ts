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
  // 임시 공개 (CLAUDE.md §4). 빌드 결과(dist)만 내보내고 프록시는 server.proxy 를 그대로 쓴다.
  // 개발 서버는 소스를 내보내므로 터널에 물리지 않는다
  preview: {
    port: 4173,
    strictPort: true,
    allowedHosts: ['.trycloudflare.com'],
  },
})

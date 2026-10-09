import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// 백엔드(8080)의 /api 와 /ws 를 프록시한다. 브라우저 입장에서는 같은 출처라 CORS 설정이 필요 없다 (CLAUDE.md §4).
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
      '/ws': { target: 'ws://localhost:8080', ws: true },
    },
  },
  // 임시 공개 (CLAUDE.md §4). 빌드 결과(dist)만 내보내고 프록시는 server.proxy 를 그대로 쓴다.
  // 개발 서버는 소스를 내보내므로 터널에 물리지 않는다.
  // IP 주소로 접속(share.sh --lan)할 때는 --host 0.0.0.0 으로 띄운다. IP 주소는 Vite 가 allowedHosts 없이도 받는다
  preview: {
    port: 4173,
    strictPort: true,
    allowedHosts: ['.trycloudflare.com'],
  },
})

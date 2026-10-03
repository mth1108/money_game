// 진입점. 기본은 P1 화면, 주소에 ?p0 이 있으면 P0 테스트 하네스 (CLAUDE.md §4).
// 둘은 따로 불러온다 — P0 에는 P1 의 스타일(Tailwind)이 섞이지 않는다.
import { StrictMode, Suspense, lazy } from 'react'
import { createRoot } from 'react-dom/client'

const isP0 = new URLSearchParams(location.search).has('p0')
const App = lazy(() => (isP0 ? import('./p0/App') : import('./App')))

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <Suspense fallback={null}>
      <App />
    </Suspense>
  </StrictMode>,
)
